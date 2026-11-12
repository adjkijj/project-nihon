package com.nihon.history;

import java.util.Optional;
import java.util.UUID;

/**
 * Truy cập mã người học ẩn danh của request hiện tại (ThreadLocal, được LearnerIdentityFilter nạp và dọn).
 * Chỉ có giá trị khi trình duyệt GỬI LÊN cookie hợp lệ; khi tắt cookie thì rỗng và tính năng lịch sử tự bỏ qua.
 */
public final class LearnerContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private LearnerContext() {
    }

    public static Optional<String> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    static void set(String learnerId) {
        if (learnerId == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(learnerId);
        }
    }

    static void clear() {
        CURRENT.remove();
    }

    /** Trả UUID chuẩn hoá (chữ thường) hoặc null nếu không hợp lệ. */
    public static String normalizeUuid(String raw) {
        if (raw == null || raw.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(raw).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}