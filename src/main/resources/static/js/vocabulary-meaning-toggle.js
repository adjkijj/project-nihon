/**
 * Quản lý tính năng Bật/Tắt ẩn nghĩa tiếng Việt trên bảng danh sách từ vựng.
 * - Bật/tắt class 'hide-meaning' trên bảng.
 * - Lưu và khôi phục trạng thái qua localStorage với khóa 'nihon_hide_meaning' (bọc trong try/catch).
 * - Cập nhật thuộc tính aria-pressed và class active trên nút bấm.
 */
(function () {
    'use strict';

    const STORAGE_KEY = 'nihon_hide_meaning';

    /**
     * Đọc trạng thái ẩn nghĩa từ localStorage.
     * Mặc định là không ẩn (false), chỉ ẩn khi lưu giá trị 'true'.
     */
    function isMeaningHidden() {
        try {
            return localStorage.getItem(STORAGE_KEY) === 'true';
        } catch (e) {
            return false;
        }
    }

    /**
     * Áp dụng trạng thái ẩn nghĩa lên bảng từ vựng và nút toggle.
     */
    function applyState(hidden) {
        const table = document.getElementById('vocab-table') || document.querySelector('.table-container table') || document.querySelector('table');
        if (table) {
            if (hidden) {
                table.classList.add('hide-meaning');
            } else {
                table.classList.remove('hide-meaning');
            }
        }

        const btn = document.getElementById('btn-hide-meaning') || document.getElementById('hide-meaning-btn') || document.querySelector('.btn-toggle-meaning');
        if (btn) {
            btn.setAttribute('aria-pressed', hidden ? 'true' : 'false');
            if (hidden) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        }
    }

    /**
     * Bật/tắt trạng thái ẩn nghĩa khi bấm nút.
     */
    function toggleMeaning() {
        const nextState = !isMeaningHidden();
        try {
            localStorage.setItem(STORAGE_KEY, nextState ? 'true' : 'false');
        } catch (e) {
            // Không làm gián đoạn UI nếu localStorage bị chặn
        }
        applyState(nextState);
    }

    // 1. Áp dụng trạng thái sớm nếu bảng đã có sẵn trong DOM để chống nhấp nháy giao diện
    const initial = isMeaningHidden();
    const existingTable = document.getElementById('vocab-table') || document.querySelector('.table-container table') || document.querySelector('table');
    if (initial && existingTable) {
        existingTable.classList.add('hide-meaning');
    }

    // 2. Gắn sự kiện khi DOM đã sẵn sàng
    function init() {
        const current = isMeaningHidden();
        applyState(current);

        const btn = document.getElementById('btn-hide-meaning') || document.getElementById('hide-meaning-btn') || document.querySelector('.btn-toggle-meaning');
        if (btn) {
            btn.addEventListener('click', toggleMeaning);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();

