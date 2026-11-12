package com.nihon.dto.timed;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form yêu cầu kết thúc sớm hoặc nộp bài khi hết giờ trong phiên luyện tập tính giờ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimedFinishForm {

    @NotBlank(message = "Mã phiên làm bài không được để trống.")
    private String sessionId;
}
