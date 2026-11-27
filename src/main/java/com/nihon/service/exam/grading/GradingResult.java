package com.nihon.service.exam.grading;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả chấm điểm cho một câu hỏi thi (F40 - F45).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradingResult {

    @Builder.Default
    private BigDecimal pointsAwarded = BigDecimal.ZERO;

    /**
     * true: đúng
     * false: sai
     * null: câu hỏi tự luận chưa chấm / tự chấm
     */
    private Boolean correct;

    /**
     * Nhận xét / giải thích / phản hồi chi tiết (ví dụ độ tương đồng %, quy tắc...).
     */
    private String feedback;

    /**
     * Tỷ lệ tương đồng (áp dụng cho câu hỏi TRANSLATION từ 0.0 đến 1.0).
     */
    private Double similarity;

    /**
     * Danh sách từ khóa xuất hiện trong bài làm (dành cho ESSAY).
     */
    @Builder.Default
    private List<String> matchedKeywords = new ArrayList<>();

    /**
     * Danh sách từ khóa còn thiếu trong bài làm (dành cho ESSAY).
     */
    @Builder.Default
    private List<String> missingKeywords = new ArrayList<>();

    /**
     * Đáp án đúng hiển thị tham khảo hoặc câu dịch / bài luận mẫu.
     */
    private String sampleAnswer;
}

