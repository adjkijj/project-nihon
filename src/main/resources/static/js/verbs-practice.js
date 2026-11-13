/**
 * Verbs Practice & Lab Keyboard and UI interactions (F13 - F18, F04)
 * Strict CSP: External JS file, zero inline script.
 */
document.addEventListener('DOMContentLoaded', function () {
    // 1. Multiple choice keyboard shortcuts (1, 2, 3, 4)
    const choiceButtons = document.querySelectorAll('.vp-choice-btn');
    if (choiceButtons.length > 0) {
        document.addEventListener('keydown', function (e) {
            // Chỉ bắt phím số nếu người dùng không đang gõ trong ô nhập liệu
            if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
                return;
            }

            const key = e.key;
            if (['1', '2', '3', '4'].includes(key)) {
                const index = parseInt(key, 10) - 1;
                if (index >= 0 && index < choiceButtons.length) {
                    e.preventDefault();
                    choiceButtons[index].click();
                }
            }
        });

        // Bắt sự kiện click vào nút trắc nghiệm để gán choiceIndex và nộp form
        choiceButtons.forEach(btn => {
            btn.addEventListener('click', function () {
                const idx = this.getAttribute('data-index');
                const val = this.getAttribute('data-value');
                const form = document.getElementById('verbPracticeQuestionForm');
                const choiceIdxInput = document.getElementById('choiceIndexInput');
                const answerInput = document.getElementById('verbAnswerInput');

                if (choiceIdxInput && idx !== null) {
                    choiceIdxInput.value = idx;
                }
                if (answerInput && val !== null) {
                    answerInput.value = val;
                }
                if (form) {
                    form.submit();
                }
            });
        });
    }

    // 2. Autofocus on text input if present
    const textInput = document.getElementById('verbTextInput');
    if (textInput) {
        textInput.focus();
    }

    // 3. Lab input autofocus
    const labInput = document.getElementById('labInput');
    if (labInput) {
        labInput.focus();
    }
});

