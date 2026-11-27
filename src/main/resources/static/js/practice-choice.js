/**
 * practice-choice.js - Xử lý tương tác phím tắt 1-4 và lựa chọn trắc nghiệm
 * Tuân thủ CSP: Toàn bộ sự kiện gắn qua addEventListener, không inline JS
 */
document.addEventListener('DOMContentLoaded', function () {

    // 1. Xử lý trang cấu hình (choice-index)
    const directionLabels = document.querySelectorAll('.direction-card-label');
    if (directionLabels.length > 0) {
        directionLabels.forEach(function (label) {
            label.addEventListener('click', function () {
                directionLabels.forEach(function (l) {
                    l.classList.remove('is-selected');
                });
                label.classList.add('is-selected');
                const radio = label.querySelector('input[type="radio"]');
                if (radio) {
                    radio.checked = true;
                }
            });
        });
    }

    // 2. Xử lý trang câu hỏi trắc nghiệm (choice-question)
    const choiceForm = document.getElementById('choice-answer-form');
    const choiceButtons = document.querySelectorAll('.choice-card-button');
    const choiceInput = document.getElementById('choiceVocabIdInput');

    if (choiceForm && choiceButtons.length > 0 && choiceInput) {
        let isSubmitted = false;

        function submitChoice(button) {
            if (isSubmitted || !button) return;
            isSubmitted = true;

            const vocabId = button.getAttribute('data-choice-id');
            if (vocabId) {
                choiceInput.value = vocabId;
                button.classList.add('is-active-choice');

                // Vô hiệu hóa các nút còn lại để tránh submit kép
                choiceButtons.forEach(function (btn) {
                    btn.disabled = true;
                });

                choiceForm.submit();
            }
        }

        // Sự kiện click trên từng nút lựa chọn
        choiceButtons.forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                submitChoice(btn);
            });
        });

        // Bắt phím tắt 1 - 4 trên bàn phím
        document.addEventListener('keydown', function (e) {
            if (isSubmitted) return;

            // Bỏ qua nếu người dùng đang nhập liệu trong input/textarea/select
            const activeTag = document.activeElement ? document.activeElement.tagName.toLowerCase() : '';
            if (activeTag === 'input' || activeTag === 'textarea' || activeTag === 'select') {
                return;
            }

            let choiceIndex = -1;
            if (e.key === '1' || e.code === 'Digit1' || e.code === 'Numpad1') {
                choiceIndex = 0;
            } else if (e.key === '2' || e.code === 'Digit2' || e.code === 'Numpad2') {
                choiceIndex = 1;
            } else if (e.key === '3' || e.code === 'Digit3' || e.code === 'Numpad3') {
                choiceIndex = 2;
            } else if (e.key === '4' || e.code === 'Digit4' || e.code === 'Numpad4') {
                choiceIndex = 3;
            }

            if (choiceIndex >= 0 && choiceIndex < choiceButtons.length) {
                e.preventDefault();
                submitChoice(choiceButtons[choiceIndex]);
            }
        });
    }

    // 3. Xử lý trang kết quả (choice-result): Phím Space hoặc Enter để sang câu tiếp theo
    const nextQuestionBtn = document.getElementById('btn-next-question');
    if (nextQuestionBtn) {
        document.addEventListener('keydown', function (e) {
            const activeTag = document.activeElement ? document.activeElement.tagName.toLowerCase() : '';
            if (activeTag === 'input' || activeTag === 'textarea' || activeTag === 'select') {
                return;
            }

            if (e.code === 'Space' || e.key === ' ' || e.code === 'Enter' || e.key === 'Enter') {
                e.preventDefault();
                nextQuestionBtn.click();
            }
        });
    }
});

