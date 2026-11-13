/**
 * grammar.js - Xử lý giao diện học ngữ pháp (F30, F31, F32)
 * Tuân thủ CSP nghiêm ngặt: không inline handler, không inline script.
 */
document.addEventListener('DOMContentLoaded', function () {

    // Khởi tạo độ rộng thanh tiến độ từ data-percent (chuẩn CSP)
    const initProgressFill = document.getElementById('grammarProgressFill');
    if (initProgressFill) {
        const pct = initProgressFill.getAttribute('data-percent') || '0';
        initProgressFill.style.width = pct + '%';
    }

    // =========================================================================
    // 1. Accordion: Mở rộng một thẻ mỗi lần (Single expanded item policy)
    // =========================================================================
    const accordionDetails = document.querySelectorAll('details.grammar-card');

    accordionDetails.forEach(function (detail) {
        detail.addEventListener('toggle', function () {
            if (detail.open) {
                accordionDetails.forEach(function (other) {
                    if (other !== detail && other.open) {
                        other.open = false;
                    }
                });
            }
        });
    });

    // =========================================================================
    // 2. F30: Đánh dấu đã học / chưa học (AJAX với CSRF)
    // =========================================================================
    const progressForms = document.querySelectorAll('.grammar-progress-form');

    progressForms.forEach(function (form) {
        form.addEventListener('submit', function (e) {
            e.preventDefault();

            const actionUrl = form.getAttribute('action');
            const csrfInput = form.querySelector('input[name="_csrf"]');
            const csrfToken = csrfInput ? csrfInput.value : '';
            const btn = form.querySelector('.btn-progress-toggle');
            const card = form.closest('.grammar-card');

            if (!actionUrl) return;

            const formData = new FormData(form);

            fetch(actionUrl, {
                method: 'POST',
                headers: {
                    'Accept': 'application/json',
                    'X-CSRF-TOKEN': csrfToken
                },
                body: formData
            })
            .then(function (res) {
                if (!res.ok) {
                    throw new Error('Lỗi phản hồi máy chủ: ' + res.status);
                }
                return res.json();
            })
            .then(function (data) {
                if (btn) {
                    const iconSpan = btn.querySelector('.btn-progress-icon');
                    const textSpan = btn.querySelector('.btn-progress-text');

                    if (data.learned) {
                        btn.classList.add('learned');
                        if (iconSpan) iconSpan.textContent = '✓';
                        if (textSpan) textSpan.textContent = 'Đã học';
                        if (card) card.classList.add('is-learned');
                    } else {
                        btn.classList.remove('learned');
                        if (iconSpan) iconSpan.textContent = '○';
                        if (textSpan) textSpan.textContent = 'Chưa học';
                        if (card) card.classList.remove('is-learned');
                    }
                }

                // Cập nhật thanh tiến độ ở đầu trang
                const fillBar = document.getElementById('grammarProgressFill');
                const textSpan = document.getElementById('grammarProgressText');
                const percentSpan = document.getElementById('grammarProgressPercent');

                if (fillBar) {
                    fillBar.style.width = data.progressPercent + '%';
                }
                if (textSpan) {
                    textSpan.textContent = data.totalLearned + '/' + data.totalTopics;
                }
                if (percentSpan) {
                    percentSpan.textContent = data.progressPercent + '%';
                }
            })
            .catch(function () {
                // Fallback nộp form thông thường nếu AJAX thất bại
                form.submit();
            });
        });
    });

    // =========================================================================
    // 3. F32: Chấm bài trắc nghiệm mini tại thẻ (AJAX)
    // =========================================================================
    const quizForms = document.querySelectorAll('.mini-quiz-form');

    quizForms.forEach(function (form) {
        form.addEventListener('submit', function (e) {
            e.preventDefault();

            const actionUrl = form.getAttribute('action');
            const csrfInput = form.querySelector('input[name="_csrf"]');
            const csrfToken = csrfInput ? csrfInput.value : '';
            const feedbackContainer = form.querySelector('.mini-quiz-feedback');
            const submitBtn = form.querySelector('button[type="submit"]');

            if (!actionUrl) return;

            const formData = new FormData(form);

            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.textContent = 'Đang chấm điểm...';
            }

            fetch(actionUrl, {
                method: 'POST',
                headers: {
                    'Accept': 'application/json',
                    'X-CSRF-TOKEN': csrfToken
                },
                body: formData
            })
            .then(function (res) {
                if (!res.ok) {
                    throw new Error('Lỗi máy chủ khi chấm bài.');
                }
                return res.json();
            })
            .then(function (data) {
                if (submitBtn) {
                    submitBtn.disabled = false;
                    submitBtn.textContent = 'Làm lại bài tập';
                }
                renderQuizResults(form, data);
            })
            .catch(function () {
                form.submit();
            });
        });
    });

    function renderQuizResults(form, data) {
        const feedbackContainer = form.querySelector('.mini-quiz-feedback');
        if (!feedbackContainer) return;

        feedbackContainer.textContent = '';
        feedbackContainer.removeAttribute('hidden');

        // Khung thông báo tổng điểm
        const scoreBanner = document.createElement('div');
        scoreBanner.className = 'alert ' + (data.allCorrect ? 'alert-success' : 'alert-info') + ' mb-3';
        scoreBanner.textContent = 'Kết quả: ' + data.correctCount + '/' + data.totalQuestions + ' câu đúng (' + data.scorePercent + '%)';
        feedbackContainer.appendChild(scoreBanner);

        // Hiển thị đáp án đúng/sai từng câu
        if (data.questionResults) {
            data.questionResults.forEach(function (qRes) {
                const qElem = form.querySelector('[data-question-id="' + qRes.questionId + '"]');
                if (!qElem) return;

                // Reset các nhãn lựa chọn
                const labels = qElem.querySelectorAll('.mini-quiz-choice-label');
                labels.forEach(function (lbl) {
                    lbl.classList.remove('choice-correct', 'choice-wrong');
                    const radio = lbl.querySelector('input[type="radio"]');
                    if (radio) {
                        const val = parseInt(radio.value, 10);
                        if (val === qRes.correctChoiceId) {
                            lbl.classList.add('choice-correct');
                        } else if (val === qRes.userChoiceId && !qRes.correct) {
                            lbl.classList.add('choice-wrong');
                        }
                    }
                });

                // Hiển thị giải thích câu
                let explainElem = qElem.querySelector('.q-explain-text');
                if (!explainElem) {
                    explainElem = document.createElement('div');
                    explainElem.className = 'q-explain-text quiz-feedback-box bg-light border mt-2';
                    qElem.appendChild(explainElem);
                }
                explainElem.textContent = (qRes.correct ? '✓ Chính xác! ' : '✗ Chưa chính xác. ') +
                        'Đáp án đúng: ' + qRes.correctChoiceText +
                        (qRes.explanation ? ' — ' + qRes.explanation : '');
            });
        }
    }
});
