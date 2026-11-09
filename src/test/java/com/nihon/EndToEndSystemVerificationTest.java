package com.nihon;

import com.nihon.config.CsrfInterceptor;
import com.nihon.dto.PracticeResultDto;
import com.nihon.dto.VocabularyRowDto;
import com.nihon.entity.ImportAuditLog;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.ImportAuditLogRepository;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import com.nihon.service.StudyProgressService;
import com.nihon.service.VocabularyService;
import com.nihon.service.excel.RateLimiterService;
import jakarta.servlet.http.Cookie;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử xác nhận toàn hệ thống từ đầu đến cuối (End-to-End Verification):
 * 1) Luồng nghiệp vụ: Chủ đề, Từ vựng (kể cả từ thuộc 2 chủ đề, xóa/chuyển hàng loạt),
 *    Kanji (tạo, sửa, xóa, thêm từ cần nhớ từ trang chi tiết),
 *    Import (preview, commit, history, undo, export, re-import),
 *    Luyện tập & Thống kê (giới hạn session history).
 * 2) Đầu vào xấu (Fuzzing / Bad Inputs) cho mọi form & tham số truy vấn:
 *    không trả 500, không lộ thông tin nội bộ, không dính XSS.
 * 3) Rà soát bảo mật: mọi POST kiểm tra CSRF, mọi /import/** kiểm tra IP.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EndToEndSystemVerificationTest {

    private static final String CSRF = "e2e-verification-csrf-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private KanjiEntryRepository kanjiEntryRepository;

    @Autowired
    private ImportAuditLogRepository auditLogRepository;

    @Autowired
    private VocabularyService vocabularyService;

    @Autowired
    private StudyProgressService studyProgressService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void resetRateLimit() {
        rateLimiterService.reset();
    }

    private Cookie csrfCookie() {
        return new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF);
    }

    @Test
    @DisplayName("E2E-1: Chủ đề - tạo, sửa, di chuyển lên xuống, xem chi tiết, chuyển nội dung rồi xóa")
    void verifyTopicLifecycleEndToEnd() throws Exception {
        // 1. Tạo 2 chủ đề mới
        mockMvc.perform(post("/topics/new")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("name", "E2E Chủ đề Nguồn")
                        .param("kanjiSection", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"));

        mockMvc.perform(post("/topics/new")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("name", "E2E Chủ đề Đích")
                        .param("kanjiSection", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"));

        Topic srcTopic = topicRepository.findByNameIgnoreCase("E2E Chủ đề Nguồn").orElseThrow();
        Topic dstTopic = topicRepository.findByNameIgnoreCase("E2E Chủ đề Đích").orElseThrow();

        // 2. Sửa tên chủ đề nguồn
        mockMvc.perform(post("/topics/" + srcTopic.getId() + "/edit")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("name", "E2E Chủ đề Nguồn Đã Sửa")
                        .param("kanjiSection", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"));

        assertThat(topicRepository.findById(srcTopic.getId()).orElseThrow().getName())
                .isEqualTo("E2E Chủ đề Nguồn Đã Sửa");

        // 3. Di chuyển thứ tự chủ đề lên/xuống
        int orderBefore = topicRepository.findById(dstTopic.getId()).orElseThrow().getSortOrder();
        mockMvc.perform(post("/topics/" + dstTopic.getId() + "/move")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("direction", "UP"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"));
        int orderAfterUp = topicRepository.findById(dstTopic.getId()).orElseThrow().getSortOrder();
        assertThat(orderAfterUp).isLessThanOrEqualTo(orderBefore);

        mockMvc.perform(post("/topics/" + dstTopic.getId() + "/move")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("direction", "DOWN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"));

        // 4. Thêm 1 từ vựng vào chủ đề nguồn và xem chi tiết chủ đề
        mockMvc.perform(post("/vocabulary/new")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("topicId", String.valueOf(srcTopic.getId()))
                        .param("japaneseText", "机（つくえ）")
                        .param("reading", "つくえ")
                        .param("romaji", "tsukue")
                        .param("meaningVi", "Cái bàn"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/topics/" + srcTopic.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("E2E Chủ đề Nguồn Đã Sửa")));

        // 5. Chuyển nội dung từ chủ đề nguồn sang chủ đề đích rồi xóa chủ đề nguồn
        mockMvc.perform(post("/topics/" + srcTopic.getId() + "/move-content")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("targetTopicId", String.valueOf(dstTopic.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"));

        assertThat(vocabularyRepository.countByTopicId(srcTopic.getId())).isZero();
        assertThat(vocabularyRepository.countByTopicId(dstTopic.getId())).isEqualTo(1);

        mockMvc.perform(post("/topics/" + srcTopic.getId() + "/delete")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"));

        assertThat(topicRepository.findById(srcTopic.getId())).isEmpty();

        // Dọn dẹp
        vocabularyRepository.deleteAll(vocabularyRepository.findByTopicIdOrderBySourceLineAsc(dstTopic.getId()));
        topicRepository.deleteById(dstTopic.getId());
    }

    @Test
    @DisplayName("E2E-2: Từ vựng - tạo ở 2 chủ đề, sửa nhóm, xem chi tiết, xóa theo chủ đề, xóa/chuyển hàng loạt")
    void verifyVocabularyLifecycleAndBulkActionsEndToEnd() throws Exception {
        Topic t1 = new Topic();
        t1.setName("E2E Vocab Topic 1");
        t1.setSortOrder(901);
        t1 = topicRepository.save(t1);

        Topic t2 = new Topic();
        t2.setName("E2E Vocab Topic 2");
        t2.setSortOrder(902);
        t2 = topicRepository.save(t2);

        // 1. Tạo cùng 1 từ thuộc 2 chủ đề khác nhau
        for (Topic t : List.of(t1, t2)) {
            mockMvc.perform(post("/vocabulary/new")
                            .cookie(csrfCookie())
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                            .param("topicId", String.valueOf(t.getId()))
                            .param("japaneseText", "図書館（としょかん）")
                            .param("reading", "としょかん")
                            .param("romaji", "toshokan")
                            .param("meaningVi", "Thư viện"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/vocabulary"));
        }

        Vocabulary v1 = vocabularyRepository.findByTopicIdAndJapaneseText(t1.getId(), "図書館（としょかん）").orElseThrow();
        Vocabulary v2 = vocabularyRepository.findByTopicIdAndJapaneseText(t2.getId(), "図書館（としょかん）").orElseThrow();

        // Kiểm tra gom thành 1 hàng với 2 ID trong allIds
        Page<VocabularyRowDto> rows = vocabularyService.search(null, "図書館", PageRequest.of(0, 10));
        VocabularyRowDto mergedRow = rows.getContent().stream()
                .filter(r -> "図書館（としょかん）".equals(r.getJapaneseText()))
                .findFirst().orElseThrow();
        assertThat(mergedRow.getAllIds()).contains(v1.getId(), v2.getId());

        // 2. Xem chi tiết từ vựng
        mockMvc.perform(get("/vocabulary/" + v1.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("図書館（としょかん）")))
                .andExpect(content().string(containsString("E2E Vocab Topic 1")))
                .andExpect(content().string(containsString("E2E Vocab Topic 2")));

        // 3. Sửa văn bản áp dụng cho toàn nhóm allIds
        mockMvc.perform(post("/vocabulary/" + v1.getId() + "/edit")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("topicId", String.valueOf(t1.getId()))
                        .param("japaneseText", "図書館（としょかん）")
                        .param("reading", "としょかん")
                        .param("romaji", "toshokan")
                        .param("meaningVi", "Thư viện trung tâm")
                        .param("scope", "ALL")
                        .param("allIds", v1.getId() + "," + v2.getId()))
                .andExpect(status().is3xxRedirection());

        assertThat(vocabularyRepository.findById(v2.getId()).orElseThrow().getMeaningVi())
                .isEqualTo("Thư viện trung tâm");

        // 4. Xóa trong phạm vi 1 chủ đề (scope=TOPIC): chỉ xóa v1, giữ nguyên v2
        mockMvc.perform(post("/vocabulary/" + v1.getId() + "/delete")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("scope", "TOPIC"))
                .andExpect(status().is3xxRedirection());

        assertThat(vocabularyRepository.findById(v1.getId())).isEmpty();
        assertThat(vocabularyRepository.findById(v2.getId())).isPresent();

        // 5. Tạo thêm 2 từ mới để kiểm tra chuyển hàng loạt và xóa hàng loạt
        Vocabulary b1 = new Vocabulary();
        b1.setTopic(t1);
        b1.setJapaneseText("鉛筆（えんぴつ）");
        b1.setReading("えんぴつ");
        b1.setRomaji("enpitsu");
        b1.setMeaningVi("Bút chì");
        b1.setHasKanji(true);
        b1 = vocabularyRepository.save(b1);

        // Chuyển hàng loạt b1 từ t1 sang t2
        mockMvc.perform(post("/vocabulary/bulk-move")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("ids", String.valueOf(b1.getId()))
                        .param("targetTopicId", String.valueOf(t2.getId()))
                        .param("scope", "TOPIC"))
                .andExpect(status().is3xxRedirection());

        assertThat(vocabularyRepository.findById(b1.getId()).orElseThrow().getTopic().getId())
                .isEqualTo(t2.getId());

        // Xóa hàng loạt cả v2 và b1
        mockMvc.perform(post("/vocabulary/bulk-delete")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("ids", v2.getId() + "," + b1.getId())
                        .param("scope", "ALL"))
                .andExpect(status().is3xxRedirection());

        assertThat(vocabularyRepository.findById(v2.getId())).isEmpty();
        assertThat(vocabularyRepository.findById(b1.getId())).isEmpty();

        topicRepository.deleteById(t1.getId());
        topicRepository.deleteById(t2.getId());
    }

    @Test
    @DisplayName("E2E-3: Kanji - tạo, sửa, xem chi tiết, thêm từ cần nhớ từ trang chi tiết, xóa Kanji")
    void verifyKanjiLifecycleAndAddWordFromDetailEndToEnd() throws Exception {
        Topic kanjiTopic = new Topic();
        kanjiTopic.setName("E2E Kanji Bài 1");
        kanjiTopic.setSortOrder(903);
        kanjiTopic.setKanjiSection(true);
        kanjiTopic = topicRepository.save(kanjiTopic);

        // 1. Tạo Kanji mới '櫻'
        mockMvc.perform(post("/kanji/new")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("topicId", String.valueOf(kanjiTopic.getId()))
                        .param("character", "櫻")
                        .param("hanViet", "ANH")
                        .param("meaningVi", "Hoa anh đào")
                        .param("onyomi", "オウ")
                        .param("kunyomi", "さくら"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kanji"));

        KanjiEntry savedKanji = kanjiEntryRepository.findByCharacter("櫻").orElseThrow();

        // 2. Sửa Kanji
        mockMvc.perform(post("/kanji/" + savedKanji.getId() + "/edit")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("topicId", String.valueOf(kanjiTopic.getId()))
                        .param("character", "櫻")
                        .param("hanViet", "ANH ĐÀO")
                        .param("meaningVi", "Cây hoa anh đào")
                        .param("onyomi", "オウ")
                        .param("kunyomi", "さくら"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kanji"));

        assertThat(kanjiEntryRepository.findById(savedKanji.getId()).orElseThrow().getHanViet())
                .isEqualTo("ANH ĐÀO");

        // 3. Mở form tạo từ cần nhớ từ liên kết trên trang chi tiết Kanji (/vocabulary/new?topicId=...&japaneseText=櫻)
        mockMvc.perform(get("/vocabulary/new")
                        .param("topicId", String.valueOf(kanjiTopic.getId()))
                        .param("japaneseText", "櫻"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("櫻")));

        mockMvc.perform(post("/vocabulary/new")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("topicId", String.valueOf(kanjiTopic.getId()))
                        .param("japaneseText", "櫻花（おうか）")
                        .param("reading", "おうか")
                        .param("romaji", "ouka")
                        .param("meaningVi", "Hoa anh đào"))
                .andExpect(status().is3xxRedirection());

        // Kiểm tra trang chi tiết Kanji hiện đúng từ cần nhớ vừa thêm
        mockMvc.perform(get("/kanji/" + savedKanji.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("櫻花（おうか）")));

        // 4. Xóa Kanji (từ vựng liên quan vẫn được bảo toàn)
        mockMvc.perform(post("/kanji/" + savedKanji.getId() + "/delete")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kanji"));

        assertThat(kanjiEntryRepository.findById(savedKanji.getId())).isEmpty();
        Vocabulary keptVocab = vocabularyRepository.findByTopicIdAndJapaneseText(kanjiTopic.getId(), "櫻花（おうか）").orElseThrow();

        // Dọn dẹp
        vocabularyRepository.deleteById(keptVocab.getId());
        topicRepository.deleteById(kanjiTopic.getId());
    }

    @Test
    @DisplayName("E2E-4: Import (Preview -> Commit -> History -> Undo -> Export -> Re-import)")
    void verifyImportExportUndoFlowEndToEnd() throws Exception {
        byte[] workbookBytes = buildSampleImportWorkbook();
        MockMultipartFile uploadFile = new MockMultipartFile(
                "file", "e2e-import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookBytes
        );

        // 1. Preview
        MvcResult previewRes = mockMvc.perform(multipart("/import/preview")
                        .file(uploadFile)
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF))
                .andExpect(status().isOk())
                .andReturn();
        String previewToken = (String) previewRes.getModelAndView().getModel().get("previewToken");
        assertThat(previewToken).isNotBlank();

        // 2. Commit
        mockMvc.perform(post("/import/commit")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("previewToken", previewToken)
                        .param("mode", "SKIP_DUPLICATES"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nhập dữ liệu thành công")));

        ImportAuditLog latestAudit = auditLogRepository.findAll().stream()
                .filter(l -> "e2e-import.xlsx".equals(l.getFileName()) && "SUCCESS".equals(l.getResult()))
                .reduce((first, second) -> second)
                .orElseThrow();

        // 3. Xem lịch sử (/import/history)
        mockMvc.perform(get("/import/history"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("e2e-import.xlsx")));

        // 4. Xuất file Excel (/import/export)
        byte[] exportedBytes = mockMvc.perform(get("/import/export"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(exportedBytes).isNotEmpty();

        // 5. Hoàn tác lần import vừa tạo (POST /import/history/{id}/undo)
        mockMvc.perform(post("/import/history/" + latestAudit.getId() + "/undo")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/import/history"));

        assertThat(topicRepository.findByNameIgnoreCase("E2E Import Chủ đề 1")).isEmpty();
        assertThat(kanjiEntryRepository.findByCharacter("嶺")).isEmpty();

        // 6. Import lại từ dữ liệu rồi hoàn tác sạch sẽ
        rateLimiterService.reset();
        MockMultipartFile reimportFile = new MockMultipartFile(
                "file", "e2e-reimport.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookBytes
        );
        mockMvc.perform(multipart("/import/commit")
                        .file(reimportFile)
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("mode", "SKIP_DUPLICATES"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nhập dữ liệu thành công")));

        ImportAuditLog reimportAudit = auditLogRepository.findAll().stream()
                .filter(l -> "e2e-reimport.xlsx".equals(l.getFileName()) && "SUCCESS".equals(l.getResult()))
                .reduce((first, second) -> second)
                .orElseThrow();
        rateLimiterService.reset();
        mockMvc.perform(post("/import/history/" + reimportAudit.getId() + "/undo")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("E2E-5: Luyện tập Nhật->Việt, Việt->Nhật & Thống kê giới hạn session history đúng")
    void verifyPracticeAndStatsSessionCapEndToEnd() throws Exception {
        Topic t = new Topic();
        t.setName("E2E Practice Topic");
        t.setSortOrder(904);
        t = topicRepository.save(t);

        Vocabulary v = new Vocabulary();
        v.setTopic(t);
        v.setJapaneseText("先生（せんせい）");
        v.setReading("せんせい");
        v.setRomaji("sensei");
        v.setMeaningVi("Giáo viên");
        v.setHasKanji(true);
        v = vocabularyRepository.save(v);

        MockHttpSession session = new MockHttpSession();

        // 1. Luyện tập Nhật -> Việt
        mockMvc.perform(get("/practice/question").param("topicId", String.valueOf(t.getId())).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("先生")));

        mockMvc.perform(post("/practice/check")
                        .session(session)
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("vocabId", String.valueOf(v.getId()))
                        .param("topicId", String.valueOf(t.getId()))
                        .param("userReading", "せんせい")
                        .param("userMeaning", "Giáo viên"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chính xác")));

        // 2. Luyện tập Việt -> Nhật
        mockMvc.perform(get("/practice/vi-ja/question").param("topicId", String.valueOf(t.getId())).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Giáo viên")));

        mockMvc.perform(post("/practice/vi-ja/check")
                        .session(session)
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("vocabId", String.valueOf(v.getId()))
                        .param("topicId", String.valueOf(t.getId()))
                        .param("userJapanese", "先生"))
                .andExpect(status().isOk());

        // 3. Trang Thống kê (/stats)
        mockMvc.perform(get("/stats").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("E2E Practice Topic")));

        // 4. Kiểm tra giới hạn lịch sử session (tối đa 500 bản ghi)
        for (int i = 0; i < 520; i++) {
            studyProgressService.recordJaViAttempt(session,
                    PracticeResultDto.builder()
                            .vocabId(v.getId())
                            .japaneseText("先生")
                            .overallCorrect(true)
                            .userMeaning("Giáo viên")
                            .expectedMeaning("Giáo viên")
                            .build(),
                    t.getId());
        }
        assertThat(studyProgressService.getHistory(session))
                .hasSize(StudyProgressService.MAX_SESSION_RECORDS);

        vocabularyRepository.deleteById(v.getId());
        topicRepository.deleteById(t.getId());
    }

    @Test
    @DisplayName("E2E-6: Kiểm thử đầu vào xấu (Bad Inputs) trên mọi Form & Tham số - Không bao giờ trả 500 hoặc lộ thông tin nội bộ")
    void verifyBadInputsNeverReturn500OrLeakInternalDetails() throws Exception {
        String huge10000 = "あ".repeat(10000);
        String nfdVietnamese = Normalizer.normalize("Tiếng Việt tổ hợp", Normalizer.Form.NFD);
        List<String> badStrings = List.of(
                "",
                "\u3000\u3000\u3000",
                "\u200B\u200C\u200D\uFEFF",
                "dòng 1\r\ndòng 2",
                "<script>alert(1)</script>",
                huge10000,
                "🍣🎌🌸",
                nfdVietnamese
        );

        for (String bad : badStrings) {
            // 1. TopicForm
            MvcResult topicRes = mockMvc.perform(post("/topics/new")
                            .cookie(csrfCookie())
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                            .param("name", bad)
                            .param("parentId", "-99")
                            .param("kanjiSection", "false"))
                    .andReturn();
            assertSafeResponse(topicRes);

            // 2. VocabularyForm
            MvcResult vocabRes = mockMvc.perform(post("/vocabulary/new")
                            .cookie(csrfCookie())
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                            .param("topicId", "-1")
                            .param("japaneseText", bad)
                            .param("reading", bad)
                            .param("romaji", bad)
                            .param("meaningVi", bad))
                    .andReturn();
            assertSafeResponse(vocabRes);

            // 3. KanjiForm
            MvcResult kanjiRes = mockMvc.perform(post("/kanji/new")
                            .cookie(csrfCookie())
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                            .param("topicId", "-1")
                            .param("character", bad)
                            .param("hanViet", bad)
                            .param("meaningVi", bad)
                            .param("onyomi", bad)
                            .param("kunyomi", bad))
                    .andReturn();
            assertSafeResponse(kanjiRes);

            // 4. PracticeAnswerForm & PracticeViJaAnswerForm
            MvcResult prac1 = mockMvc.perform(post("/practice/check")
                            .cookie(csrfCookie())
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                            .param("vocabId", "-1")
                            .param("userReading", bad)
                            .param("userMeaning", bad))
                    .andReturn();
            assertSafeResponse(prac1);

            MvcResult prac2 = mockMvc.perform(post("/practice/vi-ja/check")
                            .cookie(csrfCookie())
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                            .param("vocabId", "-1")
                            .param("userJapanese", bad))
                    .andReturn();
            assertSafeResponse(prac2);
        }

        // Dọn chủ đề hợp lệ được tạo từ chuỗi tiếng Việt NFD (sau khi chuẩn hóa NFC)
        topicRepository.findByNameIgnoreCase("Tiếng Việt tổ hợp")
                .ifPresent(t -> topicRepository.deleteById(t.getId()));

        // 5. Kiểm tra ID âm, ID cực lớn (Long.MAX_VALUE và tràn kiểu Long), page=2147483647, mode lạ
        String[] idEndpoints = {
                "/topics/-1", "/topics/0", "/topics/9223372036854775807", "/topics/99999999999999999999999999",
                "/topics/-1/edit", "/topics/9223372036854775807/edit",
                "/vocabulary/-1", "/vocabulary/0", "/vocabulary/9223372036854775807", "/vocabulary/99999999999999999999999999",
                "/vocabulary/-1/edit", "/vocabulary/9223372036854775807/edit",
                "/kanji/-1", "/kanji/0", "/kanji/9223372036854775807", "/kanji/99999999999999999999999999",
                "/kanji/-1/edit", "/kanji/9223372036854775807/edit"
        };
        for (String url : idEndpoints) {
            MvcResult res = mockMvc.perform(get(url)).andReturn();
            assertSafeResponse(res);
        }

        String[][] queryCases = {
                {"/topics", "page", "2147483647", "type", "WEIRD_MODE"},
                {"/vocabulary", "page", "2147483647", "topicId", "-5"},
                {"/vocabulary", "page", "-999", "topicId", "9223372036854775807"},
                {"/kanji", "page", "2147483647", "topicId", "-5"},
                {"/import/history", "page", "2147483647", "size", "999999"},
                {"/stats", "mode", "<script>alert(1)</script>", "topicId", "-1"},
                {"/practice", "topicId", "9223372036854775807", "mode", "WEIRD"},
                {"/practice/vi-ja", "topicId", "-999", "mode", "WEIRD"}
        };
        for (String[] q : queryCases) {
            MvcResult res = mockMvc.perform(get(q[0]).param(q[1], q[2]).param(q[3], q[4])).andReturn();
            assertSafeResponse(res);
        }

        // POST /import/commit với mode lạ
        rateLimiterService.reset();
        MvcResult weirdImportMode = mockMvc.perform(post("/import/commit")
                        .cookie(csrfCookie())
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF)
                        .param("mode", "UNKNOWN_WEIRD_MODE"))
                .andReturn();
        assertSafeResponse(weirdImportMode);
    }

    @Test
    @DisplayName("E2E-7: Rà soát bảo mật - Mọi endpoint POST kiểm tra CSRF (403) và mọi /import/** kiểm tra IP (403)")
    void verifySecurityCsrfAndImportInterceptorCoverage() throws Exception {
        String[] postEndpoints = {
                "/topics/new", "/topics/1/edit", "/topics/1/move", "/topics/1/move-content", "/topics/1/delete",
                "/vocabulary/new", "/vocabulary/1/edit", "/vocabulary/1/delete", "/vocabulary/bulk-delete", "/vocabulary/bulk-move",
                "/kanji/new", "/kanji/1/edit", "/kanji/1/delete",
                "/import/preview", "/import/commit", "/import/history/1/undo",
                "/practice/check", "/practice/vi-ja/check", "/stats/reset"
        };
        for (String endpoint : postEndpoints) {
            mockMvc.perform(post(endpoint))
                    .andExpect(status().isForbidden());
        }

        String[] importGetEndpoints = {"/import", "/import/history", "/import/export", "/import/template"};
        for (String endpoint : importGetEndpoints) {
            mockMvc.perform(get(endpoint).with(req -> {
                        req.setRemoteAddr("203.0.113.50");
                        return req;
                    }))
                    .andExpect(status().isForbidden());
        }
    }

    private void assertSafeResponse(MvcResult result) throws Exception {
        int status = result.getResponse().getStatus();
        assertThat(status).as("HTTP status không được là 500").isNotEqualTo(500);
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("<script>alert(1)</script>");
        assertThat(body).doesNotContain("java.lang.");
        assertThat(body).doesNotContain("org.springframework.");
        assertThat(body).doesNotContain("org.hibernate.");
    }

    private byte[] buildSampleImportWorkbook() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s1 = wb.createSheet("New Word");
            Row h1 = s1.createRow(0);
            h1.createCell(0).setCellValue("Topic");
            h1.createCell(1).setCellValue("Tiếng Nhật");
            h1.createCell(2).setCellValue("Romaji");
            h1.createCell(3).setCellValue("Nghĩa tiếng Việt");

            Row r1 = s1.createRow(1);
            r1.createCell(0).setCellValue("E2E Import Chủ đề 1");
            r1.createCell(1).setCellValue("未来（みらい）");
            r1.createCell(2).setCellValue("mirai");
            r1.createCell(3).setCellValue("Tương lai");

            Sheet s2 = wb.createSheet("Kanji");
            Row kh1 = s2.createRow(0);
            kh1.createCell(0).setCellValue("Topic");
            kh1.createCell(1).setCellValue("Kanji");
            kh1.createCell(2).setCellValue("Hán Việt");
            kh1.createCell(3).setCellValue("Ý nghĩa");
            kh1.createCell(4).setCellValue("On");
            kh1.createCell(5).setCellValue("Kun");
            kh1.createCell(6).setCellValue("Từ cần nhớ");
            s2.addMergedRegion(new CellRangeAddress(0, 0, 6, 8));

            Row kh2 = s2.createRow(1);
            kh2.createCell(6).setCellValue("Tiếng Nhật");
            kh2.createCell(7).setCellValue("Romaji");
            kh2.createCell(8).setCellValue("Nghĩa tiếng Việt");

            Row kr1 = s2.createRow(2);
            kr1.createCell(0).setCellValue("E2E Import Kanji 1");
            kr1.createCell(1).setCellValue("嶺");
            kr1.createCell(2).setCellValue("LĨNH");
            kr1.createCell(3).setCellValue("Đỉnh núi cao");
            kr1.createCell(4).setCellValue("レイ");
            kr1.createCell(5).setCellValue("みね");

            Row kr2 = s2.createRow(3);
            kr2.createCell(6).setCellValue("高嶺（たかね）");
            kr2.createCell(7).setCellValue("takane");
            kr2.createCell(8).setCellValue("Đỉnh núi cao");

            wb.write(out);
            return out.toByteArray();
        }
    }
}

