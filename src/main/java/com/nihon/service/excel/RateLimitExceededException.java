package com.nihon.service.excel;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ngoại lệ ném ra khi người dùng vượt quá tần suất tải lên tệp tin cho phép (HTTP 429 Too Many Requests).
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message != null ? message : "Quá giới hạn tần suất yêu cầu (tối đa 10 lần mỗi phút).");
    }
}
