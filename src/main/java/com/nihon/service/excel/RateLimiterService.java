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
     * Kiểm tra và ghi nhận lượt tải lên của người dùng với giới hạn mặc định 10 lần/phút.
     *
     * @param userIdentifier Chuỗi định danh người dùng (userId, sessionId hoặc IP)
     * @return true nếu yêu cầu nằm trong giới hạn cho phép, false nếu vượt quá 10 lần/phút
     */
    public boolean tryAcquire(String userIdentifier) {
        return tryAcquire(userIdentifier, MAX_UPLOADS_PER_MINUTE);
    }

    /**
     * Kiểm tra và ghi nhận lượt yêu cầu với số lượng tối đa tùy chỉnh trong cửa sổ 1 phút.
     *
     * @param userIdentifier Chuỗi định danh người dùng (IP, v.v.)
     * @param maxRequestsPerWindow Số yêu cầu tối đa cho phép trong cửa sổ
     * @return true nếu được phép, false nếu vượt ngưỡng
     */
    public boolean tryAcquire(String userIdentifier, int maxRequestsPerWindow) {
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

            if (timestamps.size() >= maxRequestsPerWindow) {
                return false;
            }

            timestamps.add(currentTime);
            return true;
        }
    }

    /**
     * Kiểm tra xem định danh có đang bị vượt quá giới hạn (rate-limited) hay không mà không tự ý tăng bộ đếm.
     *
     * @param userIdentifier Chuỗi định danh
     * @param maxRequestsPerWindow Ngưỡng giới hạn
     * @return true nếu đã chạm hoặc vượt ngưỡng
     */
    public boolean isRateLimited(String userIdentifier, int maxRequestsPerWindow) {
        String key = (userIdentifier != null && !userIdentifier.isBlank())
                ? userIdentifier.trim()
                : "anonymous";

        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - TIME_WINDOW_MILLIS;

        Queue<Long> timestamps = requestHistory.get(key);
        if (timestamps == null) {
            return false;
        }
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peek() < windowStart) {
                timestamps.poll();
            }
            return timestamps.size() >= maxRequestsPerWindow;
        }
    }

    /**
     * Ghi nhận một lần thất bại (ví dụ: sai mật khẩu) cho định danh người dùng/IP.
     *
     * @param userIdentifier Chuỗi định danh
     */
    public void recordFailure(String userIdentifier) {
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
            timestamps.add(currentTime);
        }
    }

    /**
     * Xóa sạch lịch sử theo dõi tần suất của một định danh cụ thể.
     */
    public void reset(String userIdentifier) {
        if (userIdentifier != null && !userIdentifier.isBlank()) {
            requestHistory.remove(userIdentifier.trim());
        }
    }

    /**
     * Xóa sạch lịch sử theo dõi tần suất (hỗ trợ kiểm thử).
     */
    public void reset() {
        requestHistory.clear();
    }
}
