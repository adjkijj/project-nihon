package com.nihon.dto.timed;

/**
 * Chế độ chấm điểm trong phiên luyện tập tính giờ (F50).
 * - INSTANT: Hiển thị kết quả đúng/sai và phản hồi ngay sau mỗi câu hỏi.
 * - AT_END: Giấu kết quả trong suốt quá trình làm bài, chỉ công bố ở màn hình kết thúc.
 */
public enum TimedEvaluationMode {
    INSTANT("Tức thì sau mỗi câu"),
    AT_END("Chỉ xem ở cuối");

    private final String displayName;

    TimedEvaluationMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static TimedEvaluationMode from(String value) {
        if (value == null || value.isBlank()) {
            return INSTANT;
        }
        for (TimedEvaluationMode m : values()) {
            if (m.name().equalsIgnoreCase(value.trim())) {
                return m;
            }
        }
        return INSTANT;
    }
}
