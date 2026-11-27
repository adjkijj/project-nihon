/**
 * practice-studio.js - Project Nihon (F53: Typing Studio)
 * Quản lý chuyển đổi chế độ luyện gõ tương tác tại Studio mà không reload trang.
 * Tuân thủ CSP: không dùng inline JS, không dùng eval, gắn listener an toàn.
 */
document.addEventListener('DOMContentLoaded', () => {
    const tabs = document.querySelectorAll('.studio-tab');
    const panels = {
        'ja-vi': document.getElementById('panel-ja-vi'),
        'vi-ja': document.getElementById('panel-vi-ja'),
        'kanji': document.getElementById('panel-kanji')
    };

    if (!tabs || tabs.length === 0) {
        return;
    }

    tabs.forEach(tab => {
        tab.addEventListener('click', (event) => {
            const targetMode = tab.getAttribute('data-mode');
            if (!targetMode || !panels[targetMode]) {
                return; // Để browser xử lý điều hướng mặc định theo href
            }

            // Ngăn chặn tải lại trang đầy đủ, thực hiện chuyển đổi panel tức thời
            event.preventDefault();

            // Cập nhật trạng thái active của các tab
            tabs.forEach(t => t.classList.remove('active'));
            tab.classList.add('active');

            // Cập nhật hiển thị các panel
            Object.keys(panels).forEach(mode => {
                const panel = panels[mode];
                if (panel) {
                    if (mode === targetMode) {
                        panel.hidden = false;
                        panel.removeAttribute('hidden');
                        panel.classList.add('active');
                    } else {
                        panel.hidden = true;
                        panel.setAttribute('hidden', 'true');
                        panel.classList.remove('active');
                    }
                }
            });

            // Cập nhật query parameter trên URL browser mà không reload
            try {
                const url = new URL(window.location.href);
                url.searchParams.set('mode', targetMode);
                window.history.pushState({ mode: targetMode }, '', url.toString());
            } catch (e) {
                // Fallback im lặng nếu URL API không hỗ trợ
            }
        });
    });

    // Lắng nghe sự kiện Back/Forward của trình duyệt để đồng bộ panel
    window.addEventListener('popstate', (event) => {
        try {
            const url = new URL(window.location.href);
            const currentMode = url.searchParams.get('mode') || 'ja-vi';
            const matchingTab = document.querySelector(`.studio-tab[data-mode="${currentMode}"]`);
            if (matchingTab) {
                matchingTab.click();
            }
        } catch (e) {
            // Fallback im lặng
        }
    });
});

