package com.nihon.service.excel;

import org.springframework.stereotype.Service;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Dịch vụ kiểm soát tần suất yêu cầu (Rate Limiting).
 * Đảm bảo mỗi người dùng (hoặc mỗi phiên/địa chỉ) chỉ được phép tải lên tối đa 10 lần mỗi phút.
 * Vượt quá giới hạn sẽ bị từ chối với mã phản hồi HTTP 429 Too Many Requests.
 */
@Service
public class RateLimiterService {

    public static final int MAX_UPLOADS_PER_MINUTE = 10;
    public static final long TIME_WINDOW_MILLIS = 60_000L;

    private final ConcurrentHashMap<String, Queue<Long>> requestHistory = new ConcurrentHashMap<>();

    /**
     * Kiểm tra và ghi nhận lượt tải lên của người dùng.
     *
     * @param userIdentifier Chuỗi định danh người dùng (userId, sessionId hoặc IP)
     * @return true nếu yêu cầu nằm trong giới hạn cho phép, false nếu vượt quá 10 lần/phút
     */
    public boolean tryAcquire(String userIdentifier) {
        String key = (userIdentifier != null && !userIdentifier.isBlank())
                ? userIdentifier.trim()
                : "anonymous";

        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - TIME_WINDOW_MILLIS;

        Queue<Long> timestamps = requestHistory.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peek() < windowStart) {
                timestamps.poll();
            }

            if (timestamps.size() >= MAX_UPLOADS_PER_MINUTE) {
                return false;
            }

            timestamps.add(currentTime);
            return true;
        }
    }

    /**
     * Xóa sạch lịch sử theo dõi tần suất (hỗ trợ kiểm thử).
     */
    public void reset() {
        requestHistory.clear();
    }
}
