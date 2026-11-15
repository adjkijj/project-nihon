/**
 * Practice Next Navigation & Auto-Next Handler - F83
 *
 * - Nhấn Enter để chuyển sang câu tiếp theo (kích hoạt #btnNext).
 * - Checkbox "Tự chuyển câu" lưu trạng thái vào localStorage khóa 'nihon_auto_next' (mặc định tắt: false).
 * - Khi phản hồi đúng và checkbox bật: chờ 650 ms rồi chuyển câu.
 * - Khi phản hồi sai: không tự động chuyển câu.
 * - Không dùng JavaScript inline, tuân thủ nghiêm ngặt CSP.
 */
(function () {
    'use strict';

    var STORAGE_KEY = 'nihon_auto_next';
    var AUTO_NEXT_DELAY_MS = 650;
    var autoNextTimer = null;

    /**
     * Đọc trạng thái tự chuyển câu từ localStorage (mặc định tắt: false)
     */
    function isAutoNextEnabled() {
        try {
            return localStorage.getItem(STORAGE_KEY) === 'true';
        } catch (e) {
            return false;
        }
    }

    /**
     * Lưu trạng thái tự chuyển câu vào localStorage
     */
    function setAutoNextEnabled(enabled) {
        try {
            localStorage.setItem(STORAGE_KEY, enabled ? 'true' : 'false');
        } catch (e) {
            // Dự phòng khi localStorage bị chặn/không khả dụng
        }
    }

    /**
     * Kiểm tra xem phản hồi câu hỏi hiện tại có đúng hay không
     */
    function isReactionCorrect() {
        var card = document.querySelector('[data-practice-correct]');
        if (card) {
            return card.getAttribute('data-practice-correct') === 'true';
        }
        if (document.querySelector('.status-banner-success')) {
            return true;
        }
        return false;
    }

    /**
     * Thực hiện chuyển câu tiếp theo
     */
    function triggerNextQuestion() {
        var btn = document.getElementById('btnNext');
        if (!btn) {
            return;
        }
        if (typeof btn.click === 'function') {
            btn.click();
        } else if (btn.href) {
            window.location.href = btn.href;
        }
    }

    /**
     * Khởi tạo các checkbox trên trang và thiết lập cơ chế tự chuyển câu
     */
    function initAutoNext() {
        var enabled = isAutoNextEnabled();
        var checkboxes = document.querySelectorAll('#autoNextToggle, .auto-next-checkbox');

        checkboxes.forEach(function (cb) {
            cb.checked = enabled;
            cb.addEventListener('change', function () {
                var newState = cb.checked;
                setAutoNextEnabled(newState);

                // Đồng bộ các checkbox khác trên trang nếu có
                checkboxes.forEach(function (otherCb) {
                    if (otherCb !== cb) {
                        otherCb.checked = newState;
                    }
                });

                // Nếu người dùng tắt checkbox khi timer đang chạy, hủy timer
                if (!newState && autoNextTimer) {
                    clearTimeout(autoNextTimer);
                    autoNextTimer = null;
                } else if (newState && isReactionCorrect() && !autoNextTimer) {
                    scheduleAutoNext();
                }
            });
        });

        // Nếu phản hồi đúng và checkbox đang bật: chờ 650 ms rồi chuyển câu
        if (enabled && isReactionCorrect()) {
            scheduleAutoNext();
        }
    }

    function scheduleAutoNext() {
        if (autoNextTimer) {
            clearTimeout(autoNextTimer);
        }
        var btn = document.getElementById('btnNext');
        if (!btn) {
            return;
        }

        autoNextTimer = setTimeout(function () {
            autoNextTimer = null;
            if (isAutoNextEnabled() && isReactionCorrect()) {
                triggerNextQuestion();
            }
        }, AUTO_NEXT_DELAY_MS);
    }

    // Nhấn phím Enter để kích hoạt nút chuyển câu
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
            // Không kích hoạt nếu đang gõ trong input text hoặc textarea
            var tag = (e.target && e.target.tagName) ? e.target.tagName.toUpperCase() : '';
            if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') {
                return;
            }
            triggerNextQuestion();
        }
    });

    // Khởi tạo khi DOM sẵn sàng
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAutoNext);
    } else {
        initAutoNext();
    }
})();
