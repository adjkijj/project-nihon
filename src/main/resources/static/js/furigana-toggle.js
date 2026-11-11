/**
 * Quản lý trạng thái Bật/Tắt hiển thị phiên âm Furigana trên toàn hệ thống.
 * Trạng thái được lưu trong localStorage (với khối try/catch an toàn khi bị chặn cookie/storage).
 * Mặc định: BẬT (true).
 */
(function () {
    const STORAGE_KEY = 'nihon_furigana_enabled';

    /**
     * Đọc trạng thái Furigana từ localStorage.
     * Mặc định là bật (true), chỉ tắt khi lưu rõ ràng 'false'.
     */
    function isFuriganaEnabled() {
        try {
            const val = localStorage.getItem(STORAGE_KEY);
            return val !== 'false';
        } catch (e) {
            return true;
        }
    }

    /**
     * Cập nhật class trên thẻ body và giao diện nút bấm.
     */
    function applyState(enabled) {
        if (document.body) {
            if (enabled) {
                document.body.classList.remove('hide-furigana');
            } else {
                document.body.classList.add('hide-furigana');
            }
        }
        updateButton(enabled);
    }

    /**
     * Cập nhật trạng thái hiển thị của nút bấm trên thanh điều hướng.
     */
    function updateButton(enabled) {
        const btn = document.getElementById('furigana-toggle-btn');
        if (!btn) {
            return;
        }
        btn.setAttribute('aria-pressed', enabled ? 'true' : 'false');
        const stateEl = btn.querySelector('.furigana-toggle-state');

        if (enabled) {
            btn.classList.add('active');
            if (stateEl) {
                stateEl.textContent = 'BẬT';
            }
        } else {
            btn.classList.remove('active');
            if (stateEl) {
                stateEl.textContent = 'TẮT';
            }
        }
    }

    /**
     * Chuyển đổi trạng thái Bật/Tắt khi người dùng bấm nút.
     */
    function toggleFurigana() {
        const nextState = !isFuriganaEnabled();
        try {
            localStorage.setItem(STORAGE_KEY, nextState ? 'true' : 'false');
        } catch (e) {
            // Không ngắt luồng nếu trình duyệt chặn localStorage
        }
        applyState(nextState);
    }

    // 1. Áp dụng trạng thái ngay lập tức khi script được tải để chống nhấp nháy giao diện
    const initial = isFuriganaEnabled();
    if (!initial && document.body) {
        document.body.classList.add('hide-furigana');
    }

    // 2. Gắn sự kiện khi cấu trúc DOM đã sẵn sàng
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            const current = isFuriganaEnabled();
            applyState(current);
            const btn = document.getElementById('furigana-toggle-btn');
            if (btn) {
                btn.addEventListener('click', toggleFurigana);
            }
        });
    } else {
        const current = isFuriganaEnabled();
        applyState(current);
        const btn = document.getElementById('furigana-toggle-btn');
        if (btn) {
            btn.addEventListener('click', toggleFurigana);
        }
    }
})();

