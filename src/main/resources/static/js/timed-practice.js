/**
 * timed-practice.js - Bộ điều khiển tương tác Luyện tập tính giờ (F42, F50, F17)
 * Tuân thủ CSP nghiêm ngặt: 100% addEventListener, không inline handlers, không eval.
 */

document.addEventListener('DOMContentLoaded', function () {

    // =========================================================================
    // 1. QUẢN LÝ MODAL HỘP THOẠI (Active Session Confirmation / Info)
    // =========================================================================
    const modalBackdrops = document.querySelectorAll('.timed-modal-backdrop');

    function closeModal(modal) {
        if (!modal) return;
        modal.classList.add('is-hidden');
    }

    function openModal(modal) {
        if (!modal) return;
        modal.classList.remove('is-hidden');
        // Focus nút đầu tiên trong modal để hỗ trợ a11y
        const focusable = modal.querySelector('button, a, input');
        if (focusable) {
            focusable.focus();
        }
    }

    modalBackdrops.forEach(function (modal) {
        // Nút đóng (X) hoặc nút có thuộc tính data-close-modal
        const closeButtons = modal.querySelectorAll('.timed-modal-close-btn, [data-close-modal]');
        closeButtons.forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                closeModal(modal);
            });
        });

        // Click ra vùng nền tối (backdrop) để đóng
        modal.addEventListener('click', function (e) {
            if (e.target === modal) {
                closeModal(modal);
            }
        });
    });

    // Nút kích hoạt mở modal (nếu có trên trang index)
    const openModalTriggers = document.querySelectorAll('[data-open-modal]');
    openModalTriggers.forEach(function (btn) {
        btn.addEventListener('click', function (e) {
            e.preventDefault();
            const targetId = btn.getAttribute('data-open-modal');
            const targetModal = document.getElementById(targetId);
            if (targetModal) {
                openModal(targetModal);
            }
        });
    });

    // =========================================================================
    // 2. ĐỒNG HỒ ĐẾM NGƯỢC (F42 COUNTDOWN TIMER)
    // =========================================================================
    const timerWrapper = document.getElementById('countdown-timer-wrapper');
    const timerDisplay = document.getElementById('countdown-timer-display');
    const finishForm = document.getElementById('timedFinishForm');

    let isFinishSubmitted = false;

    function submitFinish(isAutoExpired) {
        if (isFinishSubmitted) return;
        isFinishSubmitted = true;

        if (timerDisplay) {
            timerDisplay.textContent = '00:00';
        }

        const finishBtn = document.getElementById('btn-finish-practice');
        if (finishBtn) {
            finishBtn.disabled = true;
            if (isAutoExpired) {
                finishBtn.textContent = 'Hết giờ! Đang nộp bài...';
            } else {
                finishBtn.textContent = 'Đang nộp bài...';
            }
        }

        if (finishForm) {
            finishForm.submit();
        }
    }

    if (timerWrapper && timerDisplay && finishForm) {
        const rawSeconds = timerWrapper.getAttribute('data-remaining-seconds');
        const remainingSeconds = parseInt(rawSeconds, 10);

        if (!isNaN(remainingSeconds) && remainingSeconds >= 0) {
            // Tính mốc deadline tuyệt đối dựa theo Date.now() để chống trôi timer khi chuyển tab
            const deadlineMs = Date.now() + (remainingSeconds * 1000);

            function updateTimer() {
                const now = Date.now();
                const diffMs = deadlineMs - now;
                const secLeft = Math.max(0, Math.ceil(diffMs / 1000));

                const minutes = Math.floor(secLeft / 60);
                const seconds = secLeft % 60;
                const formatted = (minutes < 10 ? '0' : '') + minutes + ':' + (seconds < 10 ? '0' : '') + seconds;

                timerDisplay.textContent = formatted;

                // Thêm cảnh báo gấp khi thời gian còn <= 30 giây
                if (secLeft <= 30) {
                    timerWrapper.classList.add('timer-urgent');
                } else {
                    timerWrapper.classList.remove('timer-urgent');
                }

                // Khi hết giờ: Tự động nộp bài
                if (secLeft <= 0) {
                    clearInterval(timerInterval);
                    submitFinish(true);
                }
            }

            // Chạy ngay lần đầu để đồng bộ hiển thị
            updateTimer();
            const timerInterval = setInterval(updateTimer, 250);
        }
    }

    // Nút nộp bài chủ động của người dùng
    const finishBtn = document.getElementById('btn-finish-practice');
    if (finishBtn && finishForm) {
        finishBtn.addEventListener('click', function (e) {
            e.preventDefault();
            // Nếu có hộp thoại xác nhận nộp bài sớm
            const confirmDialog = document.getElementById('finishConfirmModal');
            if (confirmDialog) {
                openModal(confirmDialog);
            } else {
                submitFinish(false);
            }
        });
    }

    // Nút xác nhận nộp bài trong modal
    const confirmFinishBtn = document.getElementById('btn-confirm-finish');
    if (confirmFinishBtn && finishForm) {
        confirmFinishBtn.addEventListener('click', function (e) {
            e.preventDefault();
            submitFinish(false);
        });
    }

    // =========================================================================
    // 3. XỬ LÝ CHỌN ĐÁP ÁN TRẮC NGHIỆM (CHOICE MODE) & PHÍM TẮT 1-4
    // =========================================================================
    const choiceButtons = document.querySelectorAll('.timed-choice-button');
    const answerForm = document.getElementById('timedAnswerForm');
    const answerInput = document.getElementById('timedAnswerInput');

    let isAnswerSubmitting = false;

    function selectChoiceAndSubmit(btn) {
        if (isAnswerSubmitting || !btn || !answerForm) return;
        isAnswerSubmitting = true;

        const choiceId = btn.getAttribute('data-choice-id');
        const choiceVal = btn.getAttribute('data-choice-val');
        const choiceVocabInput = document.getElementById('choiceVocabIdInput');

        if (choiceId && choiceVocabInput) {
            choiceVocabInput.value = choiceId;
        }
        if (choiceVal !== null && answerInput) {
            answerInput.value = choiceVal;
        }

        btn.classList.add('is-active-choice');

        // Vô hiệu hóa các lựa chọn khác để tránh submit lặp
        choiceButtons.forEach(function (b) {
            b.disabled = true;
        });

        // Tự động submit form trả lời
        answerForm.submit();
    }

    if (choiceButtons.length > 0 && answerForm) {
        choiceButtons.forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                selectChoiceAndSubmit(btn);
            });
        });
    }

    // Đồng bộ giá trị câu trả lời vào trường answer chung khi submit form
    if (answerForm) {
        answerForm.addEventListener('submit', function () {
            if (isAnswerSubmitting) return;
            isAnswerSubmitting = true;

            const userMeaningInput = document.getElementById('userMeaning');
            const userJapaneseInput = document.getElementById('userJapanese');
            const kanjiAnswerInput = document.getElementById('kanjiAnswer');

            if (userMeaningInput && userMeaningInput.value && answerInput && !answerInput.value) {
                answerInput.value = userMeaningInput.value;
            } else if (userJapaneseInput && userJapaneseInput.value && answerInput && !answerInput.value) {
                answerInput.value = userJapaneseInput.value;
            } else if (kanjiAnswerInput && kanjiAnswerInput.value && answerInput && !answerInput.value) {
                answerInput.value = kanjiAnswerInput.value;
            }
        });
    }

    // Tự động focus ô nhập liệu nếu ở chế độ gõ chữ (JA_VI, VI_JA, KANJI_READING)
    const textAnswerInput = document.getElementById('userAnswer');
    if (textAnswerInput) {
        textAnswerInput.focus();
    }

    // =========================================================================
    // 4. BẮT SỰ KIỆN BÀN PHÍM TOÀN CỤC (GLOBAL KEYBOARD LISTENER)
    // =========================================================================
    document.addEventListener('keydown', function (e) {
        // Phím Escape: CHỈ đóng modal/dialog đang mở, TUYỆT ĐỐI KHÔNG thoát hoặc nộp bài!
        if (e.key === 'Escape' || e.code === 'Escape') {
            const openModals = document.querySelectorAll('.timed-modal-backdrop:not(.is-hidden)');
            if (openModals.length > 0) {
                e.preventDefault();
                openModals.forEach(function (m) {
                    closeModal(m);
                });
            }
            return;
        }

        // Nếu người dùng đang tập trung gõ phím trong input/textarea/select thì không bắt phím tắt 1-4
        const activeTag = document.activeElement ? document.activeElement.tagName.toLowerCase() : '';
        if (activeTag === 'input' || activeTag === 'textarea' || activeTag === 'select') {
            return;
        }

        // Bắt phím 1, 2, 3, 4 cho câu hỏi trắc nghiệm
        if (choiceButtons.length > 0 && !isAnswerSubmitting) {
            let choiceIdx = -1;
            if (e.key === '1' || e.code === 'Digit1' || e.code === 'Numpad1') {
                choiceIdx = 0;
            } else if (e.key === '2' || e.code === 'Digit2' || e.code === 'Numpad2') {
                choiceIdx = 1;
            } else if (e.key === '3' || e.code === 'Digit3' || e.code === 'Numpad3') {
                choiceIdx = 2;
            } else if (e.key === '4' || e.code === 'Digit4' || e.code === 'Numpad4') {
                choiceIdx = 3;
            }

            if (choiceIdx >= 0 && choiceIdx < choiceButtons.length) {
                e.preventDefault();
                selectChoiceAndSubmit(choiceButtons[choiceIdx]);
            }
        }
    });

    // =========================================================================
    // 5. TRANG CẤU HÌNH (INDEX): TƯƠNG TÁC THẺ LỰA CHỌN MODE & SEGMENTED CONTROLS
    // =========================================================================
    const modeCards = document.querySelectorAll('.timed-mode-card');
    modeCards.forEach(function (card) {
        card.addEventListener('click', function () {
            const parentLabel = card.closest('.timed-mode-label');
            if (parentLabel) {
                const radio = parentLabel.querySelector('input[type="radio"]');
                if (radio) {
                    radio.checked = true;
                    // Trigger change event nếu cần
                    radio.dispatchEvent(new Event('change', { bubbles: true }));
                }
            }
        });
    });

    const segmentedBtns = document.querySelectorAll('.timed-segmented-btn');
    segmentedBtns.forEach(function (btn) {
        btn.addEventListener('click', function () {
            const parentLabel = btn.closest('.timed-segmented-label');
            if (parentLabel) {
                const radio = parentLabel.querySelector('input[type="radio"]');
                if (radio) {
                    radio.checked = true;
                    radio.dispatchEvent(new Event('change', { bubbles: true }));
                }
            }
        });
    });
});
