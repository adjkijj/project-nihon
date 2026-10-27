package com.nihon.service.excel;

import com.nihon.config.ExcelUploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Lớp dịch vụ kiểm tra an toàn toàn diện cho các tệp tin Excel tải lên trước khi được xử lý bởi parser.
 * <p>
 * Thực hiện 7 bước kiểm tra tuần tự:
 * 1. Giới hạn kích thước tệp (đọc từ cấu hình, mặc định 5 MB).
 * 2. Giới hạn phần mở rộng tệp (chỉ chấp nhận .xlsx, loại trừ .xls, .xlsm, .xlsb, .xltm...).
 * 3. Kiểm tra Magic Bytes (50 4B 03 04 - định dạng ZIP chuẩn).
 * 4. Cấu hình bảo vệ chống Zip Bomb (minInflateRatio, maxEntrySize, maxTextSize).
 * 5. Mở và kiểm tra cấu trúc bên trong qua Apache POI (từ chối VBA/Macro, liên kết ngoài, OLE nhúng).
 * 6. Giới hạn số hàng (tối đa 5000/sheet) và số cột (tối đa 20/sheet).
 * 7. Bắt và xử lý các lỗi phân tích cú pháp XML/XXE một cách an toàn.
 * </p>
 * Toàn bộ quá trình đọc trực tiếp từ bộ nhớ RAM (InputStream), tuyệt đối không lưu tệp tạm lên đĩa.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelUploadValidator {

    private static final byte[] ZIP_MAGIC_BYTES = new byte[]{0x50, 0x4B, 0x03, 0x04};
    private static final String ALLOWED_EXTENSION = ".xlsx";

    private final ExcelUploadProperties properties;

    /**
     * Xác thực đối tượng {@link MultipartFile} tải lên từ client.
     *
     * @param file tệp tin tải lên
     * @throws UploadRejectedException nếu tệp không đáp ứng bất kỳ tiêu chuẩn an toàn nào
     */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.warn("Upload rejected: file is null or empty");
            throw new UploadRejectedException("Tệp tin tải lên không hợp lệ hoặc rỗng.");
        }

        String originalFilename = file.getOriginalFilename();
        long size = file.getSize();

        // 1. Kiểm tra kích thước tệp
        checkFileSize(size);

        // 2. Kiểm tra phần mở rộng tệp (.xlsx)
        checkFileExtension(originalFilename);

        // Đọc toàn bộ nội dung tệp vào mảng bytes trong bộ nhớ (không lưu ra đĩa)
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read uploaded file bytes for: {}", sanitizeFilenameForLog(originalFilename), e);
            throw new UploadRejectedException("Không thể đọc nội dung tệp tin tải lên.", e);
        }

        // Thực hiện các kiểm tra tiếp theo trên dữ liệu nhị phân
        validateBytes(bytes, originalFilename);
    }

    /**
     * Xác thực tệp tin từ InputStream và tên tệp (hỗ trợ kiểm thử và xử lý stream trực tiếp trong RAM).
     *
     * @param inputStream      luồng dữ liệu tệp tin
     * @param originalFilename tên tệp gốc từ client
     * @param estimatedSize    kích thước ước lượng (bytes) hoặc -1 nếu không biết trước
     * @throws UploadRejectedException nếu tệp không an toàn
     */
    public void validate(InputStream inputStream, String originalFilename, long estimatedSize) {
        if (inputStream == null) {
            log.warn("Upload rejected: inputStream is null");
            throw new UploadRejectedException("Tệp tin tải lên không hợp lệ hoặc rỗng.");
        }

        if (estimatedSize > properties.getMaxFileSizeBytes()) {
            log.warn("Upload rejected: estimated size {} exceeds max {}", estimatedSize, properties.getMaxFileSizeBytes());
            throw new UploadRejectedException("Kích thước tệp tin vượt quá giới hạn cho phép.");
        }

        checkFileExtension(originalFilename);

        byte[] bytes;
        try {
            bytes = inputStream.readAllBytes();
        } catch (IOException e) {
            log.error("Failed to read InputStream for file: {}", sanitizeFilenameForLog(originalFilename), e);
            throw new UploadRejectedException("Không thể đọc luồng dữ liệu tệp tin.", e);
        }

        checkFileSize(bytes.length);
        validateBytes(bytes, originalFilename);
    }

    /**
     * Thực hiện kiểm tra nội dung nhị phân và cấu trúc workbook của tệp Excel.
     *
     * @param bytes            dữ liệu nhị phân của tệp tin trong bộ nhớ
     * @param originalFilename tên tệp gốc (để log)
     */
    public void validateBytes(byte[] bytes, String originalFilename) {
        // 1. Kiểm tra lại kích thước mảng byte
        checkFileSize(bytes.length);

        // 2. Kiểm tra phần mở rộng
        checkFileExtension(originalFilename);

        // 3. Kiểm tra Magic Bytes (50 4B 03 04)
        checkMagicBytes(bytes);

        // 3.1 Quét sớm các entry trong ZIP container để phát hiện macro, external links, ole embeddings
        scanZipEntries(bytes);

        // 4. Cấu hình bảo vệ Zip Bomb của Apache POI
        applyZipBombProtections();

        // 5, 6, 7. Mở và kiểm tra cấu trúc POI (Macro, External Links, OLE Embeddings, Max Rows/Cols, XXE)
        inspectPoiWorkbook(bytes, originalFilename);
    }

    /**
     * Quét nhanh danh sách tệp tin trong ZIP container để phát hiện sớm các thành phần nguy hiểm
     * (VBA/Macro, liên kết ngoài, đối tượng OLE nhúng) kể cả khi tệp bị cố tình làm sai lệch content-types.
     */
    private void scanZipEntries(byte[] bytes) {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (name.contains("vbaproject") || (name.contains("vba") && name.endsWith(".bin"))) {
                    log.warn("Upload rejected: detected macro/vba entry '{}'", entry.getName());
                    throw new UploadRejectedException("Tệp tin chứa macro hoặc mã thực thi không được phép.");
                }
                if (name.contains("externallink")) {
                    log.warn("Upload rejected: detected external link entry '{}'", entry.getName());
                    throw new UploadRejectedException("Tệp tin chứa liên kết ngoài không được phép.");
                }
                if (name.startsWith("xl/embeddings/") || name.contains("oleobject")) {
                    log.warn("Upload rejected: detected embedded OLE object entry '{}'", entry.getName());
                    throw new UploadRejectedException("Tệp tin chứa đối tượng nhúng OLE không được phép.");
                }
                zis.closeEntry();
            }
        } catch (UploadRejectedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not inspect zip entries via stream: {}", e.getMessage());
        }
    }

    /**
     * Kiểm tra kích thước tệp tin không vượt quá cấu hình tối đa.
     */
    private void checkFileSize(long size) {
        if (size > properties.getMaxFileSizeBytes()) {
            log.warn("Upload rejected: file size {} bytes exceeds maximum allowed limit {} bytes",
                    size, properties.getMaxFileSizeBytes());
            throw new UploadRejectedException("Kích thước tệp tin vượt quá giới hạn cho phép.");
        }
    }

    /**
     * Kiểm tra phần mở rộng chỉ chấp nhận đuôi .xlsx (chặn path traversal và các đuôi khác).
     */
    private void checkFileExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            log.warn("Upload rejected: missing filename");
            throw new UploadRejectedException("Tên tệp tin không hợp lệ.");
        }

        // Chống path traversal: chỉ lấy tên tệp cuối cùng, loại bỏ toàn bộ đường dẫn client
        String cleanName = originalFilename.trim();
        int lastSlash = Math.max(cleanName.lastIndexOf('/'), cleanName.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            cleanName = cleanName.substring(lastSlash + 1);
        }

        int lastDotIndex = cleanName.lastIndexOf('.');
        if (lastDotIndex < 0) {
            log.warn("Upload rejected: file '{}' has no extension", sanitizeFilenameForLog(cleanName));
            throw new UploadRejectedException("Định dạng tệp tin không hợp lệ. Chỉ chấp nhận tệp tin .xlsx.");
        }

        String extension = cleanName.substring(lastDotIndex).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSION.equals(extension)) {
            log.warn("Upload rejected: invalid extension '{}' for file '{}'", extension, sanitizeFilenameForLog(cleanName));
            throw new UploadRejectedException("Định dạng tệp tin không hợp lệ. Chỉ chấp nhận tệp tin .xlsx.");
        }
    }

    /**
     * Kiểm tra 4 bytes đầu tiên phải là 50 4B 03 04 (ZIP magic bytes).
     * Tuyệt đối không tin cậy Content-Type từ client.
     */
    private void checkMagicBytes(byte[] bytes) {
        if (bytes == null || bytes.length < ZIP_MAGIC_BYTES.length) {
            log.warn("Upload rejected: file size is smaller than required 4 magic bytes");
            throw new UploadRejectedException("Tệp tin không đúng định dạng bảng tính Excel hợp lệ.");
        }

        for (int i = 0; i < ZIP_MAGIC_BYTES.length; i++) {
            if (bytes[i] != ZIP_MAGIC_BYTES[i]) {
                log.warn("Upload rejected: invalid magic bytes at index {}. Expected 0x{:02X}, found 0x{:02X}",
                        i, ZIP_MAGIC_BYTES[i], bytes[i]);
                throw new UploadRejectedException("Tệp tin không đúng định dạng bảng tính Excel hợp lệ.");
            }
        }
    }

    /**
     * Bật các cấu hình bảo vệ chống tấn công Zip Bomb trong Apache POI.
     */
    private void applyZipBombProtections() {
        ZipSecureFile.setMinInflateRatio(properties.getMinInflateRatio());
        ZipSecureFile.setMaxEntrySize(properties.getMaxEntrySize());
        ZipSecureFile.setMaxTextSize(properties.getMaxTextSize());
    }

    /**
     * Mở tệp hoàn toàn trong bộ nhớ bằng Apache POI và kiểm tra các nguy cơ an toàn:
     * - Macro / VBA Project
     * - External Links
     * - OLE Embeddings
     * - Vượt quá số hàng (5000) hoặc số cột (20)
     * - XXE (XML External Entity Injection)
     */
    private void inspectPoiWorkbook(byte[] bytes, String originalFilename) {
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(bytes));
             XSSFWorkbook workbook = new XSSFWorkbook(pkg)) {

            // Kiểm tra Macro / VBA
            checkMacroAndVba(pkg, workbook);

            // Kiểm tra External Links
            checkExternalLinks(pkg, workbook);

            // Kiểm tra OLE Embeddings
            checkOleEmbeddings(pkg, workbook);

            // Kiểm tra giới hạn số hàng và số cột trên từng sheet
            checkRowAndColumnLimits(workbook);

        } catch (UploadRejectedException e) {
            // Ném tiếp các ngoại lệ nghiệp vụ đã được định dạng thông báo an toàn
            throw e;
        } catch (Throwable t) {
            // Bao gồm OpenXML4JException, POIXMLException, SAXParseException, SecurityException do XXE hoặc Zip Bomb
            log.warn("Upload rejected: security or structure validation failed for file '{}'. Cause: {}",
                    sanitizeFilenameForLog(originalFilename), t.getMessage(), t);
            throw new UploadRejectedException("Tệp tin bảng tính không hợp lệ hoặc không đáp ứng tiêu chuẩn an toàn.", t);
        }
    }

    /**
     * Kiểm tra và từ chối các tệp tin chứa Macro hoặc mã VBA.
     */
    private void checkMacroAndVba(OPCPackage pkg, XSSFWorkbook workbook) throws OpenXML4JException, IOException {
        // Kiểm tra qua cờ workbook
        if (workbook.isMacroEnabled()) {
            log.warn("Upload rejected: workbook is macro-enabled");
            throw new UploadRejectedException("Tệp tin chứa macro hoặc mã thực thi không được phép.");
        }

        try {
            if (pkg.containPart(PackagingURIHelper.createPartName("/xl/vbaProject.bin"))) {
                log.warn("Upload rejected: package contains /xl/vbaProject.bin");
                throw new UploadRejectedException("Tệp tin chứa macro hoặc mã thực thi không được phép.");
            }
        } catch (UploadRejectedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Error checking vbaProject part: {}", e.getMessage());
            throw new UploadRejectedException("Tệp tin chứa thành phần không an toàn.", e);
        }

        // Quét toàn bộ parts để chặn bất kỳ thành phần VBA/Macro nào bị che giấu
        for (PackagePart part : pkg.getParts()) {
            String partName = part.getPartName().getName().toLowerCase(Locale.ROOT);
            String contentType = part.getContentType() != null ? part.getContentType().toLowerCase(Locale.ROOT) : "";

            if (partName.contains("vbaproject") || contentType.contains("vba") || contentType.contains("macro")) {
                log.warn("Upload rejected: detected macro/vba part '{}' with content type '{}'", partName, contentType);
                throw new UploadRejectedException("Tệp tin chứa macro hoặc mã thực thi không được phép.");
            }
        }
    }

    /**
     * Kiểm tra và từ chối các tệp tin chứa liên kết ngoài (External Links).
     */
    private void checkExternalLinks(OPCPackage pkg, XSSFWorkbook workbook) throws OpenXML4JException, IOException {
        // Kiểm tra danh sách bảng liên kết ngoài từ workbook
        if (workbook.getExternalLinksTable() != null && !workbook.getExternalLinksTable().isEmpty()) {
            log.warn("Upload rejected: workbook contains {} external link table(s)", workbook.getExternalLinksTable().size());
            throw new UploadRejectedException("Tệp tin chứa liên kết ngoài không được phép.");
        }

        // Kiểm tra quan hệ cấp package
        for (PackageRelationship rel : pkg.getRelationships()) {
            String relType = rel.getRelationshipType() != null ? rel.getRelationshipType().toLowerCase(Locale.ROOT) : "";
            if (relType.contains("externallink")) {
                log.warn("Upload rejected: detected external link relationship '{}' at package level", relType);
                throw new UploadRejectedException("Tệp tin chứa liên kết ngoài không được phép.");
            }
        }

        // Quét các quan hệ và parts trong OPC package để chặn các liên kết ngoài tiềm ẩn
        for (PackagePart part : pkg.getParts()) {
            String partName = part.getPartName().getName().toLowerCase(Locale.ROOT);
            String contentType = part.getContentType() != null ? part.getContentType().toLowerCase(Locale.ROOT) : "";

            if (partName.contains("externallink") || contentType.contains("externallink")) {
                log.warn("Upload rejected: detected external link part '{}'", partName);
                throw new UploadRejectedException("Tệp tin chứa liên kết ngoài không được phép.");
            }

            // Chỉ gọi getRelationships trên các part không phải là relationship part
            if (!part.isRelationshipPart()) {
                for (PackageRelationship rel : part.getRelationships()) {
                    String relType = rel.getRelationshipType() != null ? rel.getRelationshipType().toLowerCase(Locale.ROOT) : "";
                    if (relType.contains("externallink")) {
                        log.warn("Upload rejected: detected external link relationship '{}' in part '{}'", relType, partName);
                        throw new UploadRejectedException("Tệp tin chứa liên kết ngoài không được phép.");
                    }
                }
            }
        }
    }

    /**
     * Kiểm tra và từ chối các tệp tin chứa đối tượng OLE nhúng.
     */
    private void checkOleEmbeddings(OPCPackage pkg, XSSFWorkbook workbook) throws OpenXML4JException, IOException {
        if (workbook.getAllEmbeddedParts() != null && !workbook.getAllEmbeddedParts().isEmpty()) {
            log.warn("Upload rejected: workbook contains {} embedded part(s)", workbook.getAllEmbeddedParts().size());
            throw new UploadRejectedException("Tệp tin chứa đối tượng nhúng OLE không được phép.");
        }

        for (PackageRelationship rel : pkg.getRelationships()) {
            String relType = rel.getRelationshipType() != null ? rel.getRelationshipType().toLowerCase(Locale.ROOT) : "";
            if (relType.contains("oleobject")) {
                log.warn("Upload rejected: detected embedded OLE object relationship at package level");
                throw new UploadRejectedException("Tệp tin chứa đối tượng nhúng OLE không được phép.");
            }
        }

        for (PackagePart part : pkg.getParts()) {
            String partName = part.getPartName().getName().toLowerCase(Locale.ROOT);
            String contentType = part.getContentType() != null ? part.getContentType().toLowerCase(Locale.ROOT) : "";

            if (partName.startsWith("/xl/embeddings/") || partName.startsWith("xl/embeddings/") || contentType.contains("oleobject")) {
                log.warn("Upload rejected: detected embedded object part '{}' with content type '{}'", partName, contentType);
                throw new UploadRejectedException("Tệp tin chứa đối tượng nhúng OLE không được phép.");
            }

            if (!part.isRelationshipPart()) {
                for (PackageRelationship rel : part.getRelationships()) {
                    String relType = rel.getRelationshipType() != null ? rel.getRelationshipType().toLowerCase(Locale.ROOT) : "";
                    if (relType.contains("oleobject")) {
                        log.warn("Upload rejected: detected embedded OLE object relationship in part '{}'", partName);
                        throw new UploadRejectedException("Tệp tin chứa đối tượng nhúng OLE không được phép.");
                    }
                }
            }
        }
    }

    /**
     * Kiểm tra giới hạn số hàng (tối đa 5000) và số cột (tối đa 20) trên từng sheet.
     */
    private void checkRowAndColumnLimits(XSSFWorkbook workbook) {
        int sheetCount = workbook.getNumberOfSheets();
        for (int i = 0; i < sheetCount; i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();

            // Kiểm tra số hàng
            int physicalRows = sheet.getPhysicalNumberOfRows();
            int lastRowNum = sheet.getLastRowNum();
            // Trong POI: lastRowNum là 0-indexed. Nếu có hàng ở index >= maxRows thì vượt quá giới hạn.
            if (physicalRows > properties.getMaxRowsPerSheet() || lastRowNum >= properties.getMaxRowsPerSheet()) {
                log.warn("Upload rejected: sheet '{}' exceeds maximum allowed rows {}. physicalRows={}, lastRowNum={}",
                        sheetName, properties.getMaxRowsPerSheet(), physicalRows, lastRowNum);
                throw new UploadRejectedException("Dữ liệu bảng tính vượt quá số lượng hàng cho phép (tối đa "
                        + properties.getMaxRowsPerSheet() + " hàng mỗi sheet).");
            }

            // Kiểm tra số cột trên từng hàng
            for (Row row : sheet) {
                int physicalCells = row.getPhysicalNumberOfCells();
                short lastCellNum = row.getLastCellNum(); // lastCellNum là 1-based index của ô cuối cùng + 1

                if (physicalCells > properties.getMaxColumnsPerSheet() || lastCellNum > properties.getMaxColumnsPerSheet()) {
                    log.warn("Upload rejected: sheet '{}' row {} exceeds maximum allowed columns {}. physicalCells={}, lastCellNum={}",
                            sheetName, row.getRowNum() + 1, properties.getMaxColumnsPerSheet(), physicalCells, lastCellNum);
                    throw new UploadRejectedException("Dữ liệu bảng tính vượt quá số lượng cột cho phép (tối đa "
                            + properties.getMaxColumnsPerSheet() + " cột mỗi sheet).");
                }
            }
        }
    }

    /**
     * Chuẩn hóa tên tệp cho việc ghi log nội bộ nhằm chống log forging / log injection.
     */
    private String sanitizeFilenameForLog(String filename) {
        if (filename == null) {
            return "unknown";
        }
        return filename.replaceAll("[\\r\\n]", "_");
    }
}
