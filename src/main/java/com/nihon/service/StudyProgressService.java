package com.nihon.service;

import com.nihon.dto.PracticeRecordDto;
import com.nihon.dto.PracticeResultDto;
import com.nihon.dto.PracticeViJaResultDto;
import com.nihon.dto.StudyProgressSummaryDto;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Dịch vụ quản lý tiến độ học tập và lịch sử làm bài theo phạm vi phiên làm việc (HttpSession).
 * 
 * Đặc điểm kiến trúc:
 * 1. Phạm vi lưu trữ: Gắn với phiên duyệt web của người học (Session-based).
 *    Do ứng dụng chưa có đăng nhập tài khoản cá nhân, tiến độ chỉ tồn tại trong phiên và
 *    không mang tính chất dữ liệu riêng tư bảo vệ nhiều thiết bị.
 * 2. An toàn dữ liệu: Tuyệt đối không lưu trữ mật khẩu hay thông tin cá nhân.
 * 3. Tính toán máy chủ: Toàn bộ điểm số, đúng/sai và tỷ lệ chính xác được tính 100% tại máy chủ.
 * 4. Không tạo dữ liệu giả: Bắt đầu từ 0 khi phiên làm việc mới bắt đầu.
 */
@Service
public class StudyProgressService {

    public static final String SESSION_STUDY_RECORDS = "STUDY_PROGRESS_RECORDS";

    /**
     * Lấy toàn bộ danh sách lịch sử làm bài từ session hiện tại.
     */
    @SuppressWarnings("unchecked")
    public List<PracticeRecordDto> getHistory(HttpSession session) {
        if (session == null) {
            return Collections.emptyList();
        }
        Object attr = session.getAttribute(SESSION_STUDY_RECORDS);
        if (attr instanceof List<?>) {
            return (List<PracticeRecordDto>) attr;
        }
        return Collections.emptyList();
    }

    /**
     * Ghi nhận kết quả câu hỏi kiểm tra Nhật -> Việt vào tiến độ của session.
     */
    public synchronized void recordJaViAttempt(HttpSession session, PracticeResultDto result, Long topicId) {
        if (session == null || result == null) {
            return;
        }

        StringBuilder userAns = new StringBuilder();
        if (result.isRequireReading()) {
            userAns.append("Cách đọc: ").append(result.getUserReading() != null ? result.getUserReading() : "").append(" | ");
        }
        userAns.append("Nghĩa: ").append(result.getUserMeaning() != null ? result.getUserMeaning() : "");

        StringBuilder expectedAns = new StringBuilder();
        if (result.isRequireReading()) {
            expectedAns.append("Cách đọc: ").append(result.getExpectedReading() != null ? result.getExpectedReading() : "").append(" | ");
        }
        expectedAns.append("Nghĩa: ").append(result.getExpectedMeaning() != null ? result.getExpectedMeaning() : "");

        PracticeRecordDto record = PracticeRecordDto.builder()
                .id(UUID.randomUUID().toString())
                .vocabId(result.getVocabId())
                .topicId(topicId)
                .topicName(result.getTopicName() != null ? result.getTopicName() : "Tất cả chủ đề")
                .mode("JA_VI")
                .questionPrompt(result.getJapaneseText())
                .userAnswer(userAns.toString().trim())
                .expectedAnswer(expectedAns.toString().trim())
                .correct(result.isOverallCorrect())
                .feedbackMessage(result.getFeedbackMessage())
                .timestamp(LocalDateTime.now())
                .build();

        addRecordToSession(session, record);
    }

    /**
     * Ghi nhận kết quả câu hỏi kiểm tra Việt -> Nhật vào tiến độ của session.
     */
    public synchronized void recordViJaAttempt(HttpSession session, PracticeViJaResultDto result, Long topicId) {
        if (session == null || result == null) {
            return;
        }

        String expected = result.getExpectedJapanese() != null ? result.getExpectedJapanese() : "";
        if (result.getExpectedReading() != null && !result.getExpectedReading().isBlank()) {
            expected += " (" + result.getExpectedReading().strip() + ")";
        }

        PracticeRecordDto record = PracticeRecordDto.builder()
                .id(UUID.randomUUID().toString())
                .vocabId(result.getVocabId())
                .topicId(topicId)
                .topicName(result.getTopicName() != null ? result.getTopicName() : "Tất cả chủ đề")
                .mode("VI_JA")
                .questionPrompt(result.getMeaningVi())
                .userAnswer(result.getUserJapanese() != null ? result.getUserJapanese() : "")
                .expectedAnswer(expected)
                .correct(result.isCorrect())
                .feedbackMessage(result.getFeedbackMessage())
                .timestamp(LocalDateTime.now())
                .build();

        addRecordToSession(session, record);
    }

    private void addRecordToSession(HttpSession session, PracticeRecordDto record) {
        List<PracticeRecordDto> list = getOrCreateHistoryList(session);
        list.add(0, record); // Mới nhất hiển thị đầu tiên
        session.setAttribute(SESSION_STUDY_RECORDS, list);
    }

    @SuppressWarnings("unchecked")
    private List<PracticeRecordDto> getOrCreateHistoryList(HttpSession session) {
        Object attr = session.getAttribute(SESSION_STUDY_RECORDS);
        if (attr instanceof List<?>) {
            return (List<PracticeRecordDto>) attr;
        }
        List<PracticeRecordDto> newList = Collections.synchronizedList(new ArrayList<>());
        session.setAttribute(SESSION_STUDY_RECORDS, newList);
        return newList;
    }

    /**
     * Tổng hợp thống kê tiến độ học tập kèm bộ lọc theo chế độ kiểm tra và chủ đề.
     * Tính toán 100% tại máy chủ. Nếu lịch sử rỗng, trả về các giá trị 0 và danh sách rỗng.
     */
    public StudyProgressSummaryDto getSummary(HttpSession session, String modeFilter, Long topicIdFilter) {
        List<PracticeRecordDto> all = getHistory(session);

        String normalizedMode = (modeFilter == null || modeFilter.isBlank() || "ALL".equalsIgnoreCase(modeFilter))
                ? "ALL"
                : modeFilter.toUpperCase();

        List<PracticeRecordDto> filtered = all.stream()
                .filter(r -> {
                    if (!"ALL".equals(normalizedMode) && !normalizedMode.equalsIgnoreCase(r.getMode())) {
                        return false;
                    }
                    if (topicIdFilter != null && (r.getTopicId() == null || !r.getTopicId().equals(topicIdFilter))) {
                        return false;
                    }
                    return true;
                })
                .toList();

        long total = filtered.size();
        long correct = filtered.stream().filter(PracticeRecordDto::isCorrect).count();
        long incorrect = total - correct;
        int accuracy = total > 0 ? (int) Math.round((double) correct * 100.0 / total) : 0;

        List<PracticeRecordDto> incorrectRecords = filtered.stream()
                .filter(r -> !r.isCorrect())
                .toList();

        return StudyProgressSummaryDto.builder()
                .totalAnswered(total)
                .correctCount(correct)
                .incorrectCount(incorrect)
                .accuracyPercentage(accuracy)
                .records(filtered)
                .incorrectRecords(incorrectRecords)
                .currentModeFilter(normalizedMode)
                .currentTopicFilter(topicIdFilter)
                .build();
    }

    /**
     * Xóa toàn bộ tiến độ trong session hiện tại để người học có thể bắt đầu lại từ đầu.
     */
    public void clearProgress(HttpSession session) {
        if (session != null) {
            session.removeAttribute(SESSION_STUDY_RECORDS);
        }
    }
}
