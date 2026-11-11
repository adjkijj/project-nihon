package com.nihon.service;

import org.springframework.stereotype.Service;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Giới hạn tần suất tải xuống danh sách từ vựng theo địa chỉ IP (Rate Limiting).
 * Tối đa 20 lần mỗi phút trên mỗi địa chỉ IP để chống lạm dụng và vét dữ liệu.
 */
@Service
public class VocabularyExportRateLimiter {

    public static final int MAX_REQUESTS_PER_MINUTE = 20;
    public static final long TIME_WINDOW_MILLIS = 60_000L;

    private final ConcurrentHashMap<String, Queue<Long>> requestHistory = new ConcurrentHashMap<>();

    /**
     * Kiểm tra và ghi nhận một lượt yêu cầu xuất tệp từ vựng.
     *
     * @param clientIp Địa chỉ IP của client
     * @return true nếu còn trong giới hạn cho phép, false nếu vượt quá 20 lần/phút
     */
    public boolean tryAcquire(String clientIp) {
        String key = (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "anonymous";
        long now = System.currentTimeMillis();
        long windowStart = now - TIME_WINDOW_MILLIS;

        Queue<Long> timestamps = requestHistory.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peek() < windowStart) {
                timestamps.poll();
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
                return false;
            }
            timestamps.add(now);
            return true;
        }
    }

    /**
     * Xóa sạch lịch sử giới hạn tần suất (phục vụ viết bài kiểm thử).
     */
    public void reset() {
        requestHistory.clear();
    }
}

