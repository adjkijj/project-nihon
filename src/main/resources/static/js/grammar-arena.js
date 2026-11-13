/**
 * grammar-arena.js - Đồng hồ đếm ngược và xử lý đấu trường phản xạ ngữ pháp (F33)
 * Tuân thủ CSP nghiêm ngặt: không inline script, không inline style.
 */
document.addEventListener('DOMContentLoaded', function () {
    const timerElem = document.getElementById('arenaTimer');
    const timerDisplay = document.getElementById('arenaTimerDisplay');
    const arenaForm = document.getElementById('arenaAnswerForm');

    if (!timerElem || !timerDisplay || !arenaForm) {
        return;
    }

    let remainingSeconds = parseInt(timerElem.getAttribute('data-remaining-seconds'), 10);
    if (isNaN(remainingSeconds) || remainingSeconds < 0) {
        remainingSeconds = 0;
    }

    function formatTime(secs) {
        const m = Math.floor(secs / 60);
        const s = secs % 60;
        return (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
    }

    function updateDisplay() {
        timerDisplay.textContent = formatTime(remainingSeconds);
        if (remainingSeconds <= 30) {
            timerDisplay.classList.add('text-danger');
        }
    }

    updateDisplay();

    const intervalId = setInterval(function () {
        remainingSeconds--;
        if (remainingSeconds <= 0) {
            clearInterval(intervalId);
            timerDisplay.textContent = '00:00';
            // Hết giờ: tự động nộp bài
            arenaForm.submit();
        } else {
            updateDisplay();
        }
    }, 1000);

    // Xử lý click chọn đáp án
    const choiceButtons = arenaForm.querySelectorAll('.arena-choice-btn');
    choiceButtons.forEach(function (btn) {
        btn.addEventListener('click', function () {
            const choiceId = btn.getAttribute('data-choice-id');
            const hiddenChoiceInput = document.getElementById('arenaSelectedChoiceId');
            if (hiddenChoiceInput && choiceId) {
                hiddenChoiceInput.value = choiceId;
            }
            // Vô hiệu hóa nút để tránh double submit
            choiceButtons.forEach(function (b) { b.disabled = true; });
            arenaForm.submit();
        });
    });
});
