/**
 * Kanji Practice Enhancements - Project Nihon (F22, F23)
 * Hỗ trợ toggle hiển thị gợi ý, chọn đáp án trắc nghiệm bằng click hoặc phím tắt 1, 2, 3, 4.
 * Tuân thủ CSP nghiêm ngặt: không inline script.
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        initHintToggle();
        initChoiceForm();
    });

    /**
     * Khởi tạo nút Hiện / Ẩn gợi ý số lượng âm đọc cho Dạng 1 (KANJI_TO_READING).
     */
    function initHintToggle() {
        var btnToggle = document.getElementById('btnToggleHint');
        var hintContent = document.getElementById('hintContent');

        if (!btnToggle || !hintContent) {
            return;
        }

        btnToggle.addEventListener('click', function () {
            var isHidden = hintContent.classList.contains('u-display-none');
            if (isHidden) {
                hintContent.classList.remove('u-display-none');
                btnToggle.setAttribute('aria-expanded', 'true');
                btnToggle.textContent = '💡 Ẩn gợi ý';
            } else {
                hintContent.classList.add('u-display-none');
                btnToggle.setAttribute('aria-expanded', 'false');
                btnToggle.textContent = '💡 Hiện gợi ý';
            }
        });
    }

    /**
     * Khởi tạo form trắc nghiệm 4 lựa chọn cho Dạng 3 (KANJI_CHOICE).
     */
    function initChoiceForm() {
        var form = document.getElementById('kanjiChoiceForm');
        var input = document.getElementById('choiceKanjiIdInput');
        var buttons = document.querySelectorAll('.kanji-choice-btn');

        if (!form || !input || buttons.length === 0) {
            return;
        }

        var isSubmitting = false;

        function selectOption(btn) {
            if (isSubmitting) return;
            isSubmitting = true;

            var choiceId = btn.getAttribute('data-choice-id');
            input.value = choiceId;

            // Đổi hiệu ứng active cho button được chọn
            buttons.forEach(function (b) {
                b.classList.remove('selected');
                b.disabled = true;
            });
            btn.classList.add('selected');

            form.submit();
        }

        // Bắt sự kiện click chuột trên từng nút
        buttons.forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                selectOption(btn);
            });
        });

        // Bắt phím tắt 1, 2, 3, 4
        document.addEventListener('keydown', function (e) {
            // Không can thiệp nếu đang nhập text trong input/textarea
            var tag = document.activeElement ? document.activeElement.tagName.toUpperCase() : '';
            if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') {
                return;
            }

            var key = e.key;
            if (key >= '1' && key <= '4') {
                var index = parseInt(key, 10) - 1;
                if (index >= 0 && index < buttons.length) {
                    e.preventDefault();
                    selectOption(buttons[index]);
                }
            }
        });
    }

})();

