package com.nihon.dto.timed;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form cấu hình khởi tạo phiên luyện tập tính giờ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimedStartForm {

    @NotNull(message = "Vui lòng chọn hình thức luyện tập.")
    private TimedPracticeType type;

    @Positive(message = "Mã chủ đề không hợp lệ.")
    private Long topicId;

    private Integer questionCount; // Whitelist: 10, 20, 30

    private Integer durationMinutes; // Whitelist: 2, 3, 5, 10, 15

    private TimedEvaluationMode evaluationMode; // Default: INSTANT

    private Boolean isSpeedPreset; // True nếu chọn gói "Đấu trường tốc độ" (F17)

    private Boolean confirmCancelOld; // True nếu người dùng đồng ý hủy bài thi cũ đang chạy
}
