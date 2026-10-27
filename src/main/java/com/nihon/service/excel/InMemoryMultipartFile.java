package com.nihon.service.excel;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Cài đặt MultipartFile lưu trữ trong bộ nhớ RAM từ mảng byte.
 * Sử dụng để tái tạo tệp tin gốc từ mã tạm (preview token) phục vụ bước commit an toàn,
 * tuyệt đối không nhận dữ liệu đã qua xử lý từ phía client.
 */
public class InMemoryMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    public InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
        this.name = (name != null && !name.isBlank()) ? name : "file";
        this.originalFilename = (originalFilename != null && !originalFilename.isBlank()) ? originalFilename : "import.xlsx";
        this.contentType = (contentType != null && !contentType.isBlank())
                ? contentType
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        this.content = (content != null) ? content.clone() : new byte[0];
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte[] getBytes() {
        return content.clone();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(File dest) throws IOException {
        if (dest == null) {
            throw new IllegalArgumentException("Destination file cannot be null");
        }
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(content);
        }
    }
}
