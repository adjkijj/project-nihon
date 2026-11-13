/**
 * Project Nihon - JavaScript Điều phối Phòng thi (F42 - F48)
 * Tính năng: Đồng hồ đếm ngược, Bảng câu hỏi (Palette F46), Bật/tắt dịch tiếng Việt (F47),
 * Tự động lưu nháp (F48) với debounce 800ms, bộ đệm offline localStorage, phím tắt.
 * 100% tuân thủ CSP nghiêm ngặt: không có inline JS hay eval.
 */
document.addEventListener('DOMContentLoaded', () => {
    const roomContainer = document.getElementById('examRoomContainer');
    if (!roomContainer) return;

    const attemptId = roomContainer.dataset.attemptId;
    const csrfToken = document.querySelector('meta[name="csrf-token"]')?.getAttribute('content')
        || document.querySelector('input[name="_csrf"]')?.value || '';

    let remainingSeconds = parseInt(roomContainer.dataset.remainingSeconds || '0', 10);
    const totalQuestions = parseInt(roomContainer.dataset.totalQuestions || '0', 10);

    // =========================================================================
    // 1. Đồng hồ đếm ngược (Countdown Timer) & Tự nộp khi hết giờ
    // =========================================================================
    const timerDisplay = document.getElementById('examTimerDisplay');
    const timerBox = document.getElementById('examTimerBox');
    const finishForm = document.getElementById('finishExamForm');

    function formatTime(totalSec) {
        if (totalSec < 0) totalSec = 0;
        const mins = Math.floor(totalSec / 60);
        const secs = totalSec % 60;
        return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
    }

    function updateTimerUI() {
        if (!timerDisplay) return;
        timerDisplay.textContent = formatTime(remainingSeconds);

        if (timerBox) {
            if (remainingSeconds <= 60) {
                timerBox.classList.add('danger');
                timerBox.classList.remove('warning');
            } else if (remainingSeconds <= 300) {
                timerBox.classList.add('warning');
                timerBox.classList.remove('danger');
            } else {
                timerBox.classList.remove('warning', 'danger');
            }
        }
    }

    updateTimerUI();

    const timerInterval = setInterval(() => {
        remainingSeconds--;
        updateTimerUI();

        if (remainingSeconds <= 0) {
            clearInterval(timerInterval);
            if (finishForm) {
                alert('Đã hết thời gian làm bài thi! Hệ thống đang tự động nộp bài của bạn.');
                finishForm.submit();
            }
        }
    }, 1000);

    // =========================================================================
    // 2. F47: Bật / Tắt bản dịch tiếng Việt của đề bài (Mặc định TẮT)
    // =========================================================================
    const toggleTranslationBtn = document.getElementById('toggleTranslationBtn');
    const LOCAL_KEY_TRANSLATION = 'nihon_exam_hide_translation';

    // Đọc trạng thái từ localStorage (mặc định là 'true' - tức là ẨN)
    let hideTranslation = true;
    try {
        const storedPref = localStorage.getItem(LOCAL_KEY_TRANSLATION);
        if (storedPref !== null) {
            hideTranslation = storedPref === 'true';
        }
    } catch (e) {
        console.warn('Không thể truy cập localStorage cho tùy chọn bản dịch:', e);
    }

    function applyTranslationState() {
        if (hideTranslation) {
            document.body.classList.add('hide-translation');
            if (toggleTranslationBtn) {
                toggleTranslationBtn.innerHTML = '<span>👁️</span><span>Hiện dịch tiếng Việt</span>';
                toggleTranslationBtn.classList.remove('active');
            }
        } else {
            document.body.classList.remove('hide-translation');
            if (toggleTranslationBtn) {
                toggleTranslationBtn.innerHTML = '<span>🙈</span><span>Ẩn dịch tiếng Việt</span>';
                toggleTranslationBtn.classList.add('active');
            }
        }
    }

    applyTranslationState();

    if (toggleTranslationBtn) {
        toggleTranslationBtn.addEventListener('click', () => {
            hideTranslation = !hideTranslation;
            try {
                localStorage.setItem(LOCAL_KEY_TRANSLATION, String(hideTranslation));
            } catch (e) {
                console.warn(e);
            }
            applyTranslationState();
        });
    }

    // =========================================================================
    // 3. F48: Tự lưu nháp với Debounce 800ms & Bộ đệm Offline localStorage
    // =========================================================================
    const saveIndicator = document.getElementById('saveStatusIndicator');
    const LOCAL_KEY_DRAFT_PREFIX = 'nihon_exam_draft_';
    const draftStorageKey = LOCAL_KEY_DRAFT_PREFIX + attemptId;

    // Dọn dẹp các bản nháp offline cũ hơn 24 giờ
    cleanOldDrafts();

    function setSaveStatus(state, msg) {
        if (!saveIndicator) return;
        saveIndicator.className = 'save-status-indicator ' + state;
        saveIndicator.textContent = msg;
    }

    const debounceTimers = {};

    function autoSave(questionId, answer, markedForReview) {
        setSaveStatus('saving', '⏳ Đang lưu...');

        if (debounceTimers[questionId]) {
            clearTimeout(debounceTimers[questionId]);
        }

        debounceTimers[questionId] = setTimeout(() => {
            performSave(questionId, answer, markedForReview);
        }, 800);
    }

    function performSave(questionId, answer, markedForReview) {
        const payload = new URLSearchParams();
        payload.append('questionId', questionId);
        if (answer !== undefined && answer !== null) {
            payload.append('answer', answer);
        }
        if (markedForReview !== undefined && markedForReview !== null) {
            payload.append('markedForReview', String(markedForReview));
        }
        if (csrfToken) {
            payload.append('_csrf', csrfToken);
        }

        fetch(`/exams/attempt/${attemptId}/answer`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                'X-CSRF-TOKEN': csrfToken
            },
            body: payload.toString()
        })
        .then(res => {
            if (!res.ok) {
                throw new Error('Máy chủ phản hồi mã lỗi ' + res.status);
            }
            return res.json();
        })
        .then(data => {
            if (data.status === 'OK') {
                const now = new Date();
                const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
                setSaveStatus('saved', `✓ Đã lưu (${timeStr})`);
                updatePaletteItem(questionId);
                // Xóa câu đã lưu thành công khỏi bộ đệm offline
                removeOfflineAnswer(questionId);
            } else if (data.status === 'EXPIRED') {
                alert('Bài thi đã hết thời gian làm bài.');
                if (finishForm) finishForm.submit();
            } else {
                setSaveStatus('offline', '⚠️ ' + (data.message || 'Lỗi lưu nháp'));
            }
        })
        .catch(err => {
            console.warn('Lỗi kết nối khi lưu nháp:', err);
            // Lưu vào bộ đệm offline localStorage
            saveToOfflineStorage(questionId, answer, markedForReview);
            setSaveStatus('offline', '⚠️ Mất mạng: đã lưu vào bộ nhớ tạm');
        });
    }

    function saveToOfflineStorage(qId, ans, review) {
        try {
            const raw = localStorage.getItem(draftStorageKey);
            const data = raw ? JSON.parse(raw) : { attemptId, updatedAt: Date.now(), items: {} };
            data.updatedAt = Date.now();
            data.items[qId] = { answer: ans, markedForReview: review };
            localStorage.setItem(draftStorageKey, JSON.stringify(data));
        } catch (e) {
            console.warn('Không thể ghi bộ đệm localStorage:', e);
        }
    }

    function removeOfflineAnswer(qId) {
        try {
            const raw = localStorage.getItem(draftStorageKey);
            if (!raw) return;
            const data = JSON.parse(raw);
            if (data.items && data.items[qId]) {
                delete data.items[qId];
                if (Object.keys(data.items).length === 0) {
                    localStorage.removeItem(draftStorageKey);
                } else {
                    localStorage.setItem(draftStorageKey, JSON.stringify(data));
                }
            }
        } catch (e) {
            console.warn(e);
        }
    }

    // Đồng bộ lại dữ liệu offline khi có mạng trở lại
    window.addEventListener('online', () => {
        setSaveStatus('saving', '🔄 Đã có mạng lại, đang đồng bộ dữ liệu...');
        syncOfflineDrafts();
    });

    function syncOfflineDrafts() {
        try {
            const raw = localStorage.getItem(draftStorageKey);
            if (!raw) return;
            const data = JSON.parse(raw);
            if (!data.items) return;

            Object.entries(data.items).forEach(([qId, item]) => {
                performSave(qId, item.answer, item.markedForReview);
            });
        } catch (e) {
            console.warn('Lỗi khi đồng bộ offline drafts:', e);
        }
    }

    function cleanOldDrafts() {
        try {
            const now = Date.now();
            const MAX_AGE = 24 * 60 * 60 * 1000; // 24 giờ
            for (let i = 0; i < localStorage.length; i++) {
                const key = localStorage.key(i);
                if (key && key.startsWith(LOCAL_KEY_DRAFT_PREFIX)) {
                    const raw = localStorage.getItem(key);
                    if (raw) {
                        const parsed = JSON.parse(raw);
                        if (parsed.updatedAt && (now - parsed.updatedAt > MAX_AGE)) {
                            localStorage.removeItem(key);
                        }
                    }
                }
            }
        } catch (e) {
            console.warn(e);
        }
    }

    // =========================================================================
    // 4. Quản lý tương tác các dạng câu hỏi & F46: Bảng câu hỏi (Palette)
    // =========================================================================
    const questionCards = Array.from(document.querySelectorAll('.exam-question-card'));
    const paletteBtns = Array.from(document.querySelectorAll('.palette-btn'));
    const paletteAnsweredCounter = document.getElementById('paletteAnsweredCount');

    function getQuestionCard(qId) {
        return document.getElementById(`question-card-${qId}`);
    }

    function getPaletteBtn(qId) {
        return document.getElementById(`palette-btn-${qId}`);
    }

    function isQuestionAnswered(card) {
        if (!card) return false;
        // Kiểm tra radio MULTIPLE_CHOICE
        const checkedRadio = card.querySelector('input[type="radio"]:checked');
        if (checkedRadio) return true;

        // Kiểm tra text input / textarea
        const textInput = card.querySelector('.text-answer-input');
        if (textInput && textInput.value.trim().length > 0) return true;

        // Kiểm tra hidden order input
        const orderInput = card.querySelector('.ordering-hidden-input');
        if (orderInput && orderInput.value.trim().length > 0) return true;

        return false;
    }

    function updatePaletteItem(qId) {
        const card = getQuestionCard(qId);
        const btn = getPaletteBtn(qId);
        if (!card || !btn) return;

        const answered = isQuestionAnswered(card);
        const reviewBtn = card.querySelector('.btn-mark-review');
        const isReview = reviewBtn ? reviewBtn.classList.contains('marked') : false;

        if (answered) {
            btn.classList.add('answered');
        } else {
            btn.classList.remove('answered');
        }

        if (isReview) {
            btn.classList.add('review');
        } else {
            btn.classList.remove('review');
        }

        updatePaletteCounter();
    }

    function updatePaletteCounter() {
        if (!paletteAnsweredCounter) return;
        const answeredCount = questionCards.filter(isQuestionAnswered).length;
        paletteAnsweredCounter.textContent = answeredCount;
    }

    // Gắn sự kiện cho các loại câu hỏi
    questionCards.forEach(card => {
        const qId = card.dataset.questionId;
        const reviewBtn = card.querySelector('.btn-mark-review');

        // MULTIPLE_CHOICE: radio change
        const radios = card.querySelectorAll('.choice-radio-input');
        radios.forEach(radio => {
            radio.addEventListener('change', () => {
                // Đổi class selected cho thẻ label cha
                card.querySelectorAll('.choice-option-label').forEach(lbl => lbl.classList.remove('selected'));
                const parentLabel = radio.closest('.choice-option-label');
                if (parentLabel) parentLabel.classList.add('selected');

                autoSave(qId, radio.value, null);
                updatePaletteItem(qId);
            });
        });

        // TYPING, TRANSLATION, ESSAY: input / change
        const textInput = card.querySelector('.text-answer-input');
        if (textInput) {
            const counter = card.querySelector('.char-counter');
            textInput.addEventListener('input', () => {
                if (counter) {
                    counter.textContent = `${textInput.value.length} / 1000`;
                }
                autoSave(qId, textInput.value, null);
                updatePaletteItem(qId);
            });
        }

        // Đánh dấu xem lại (Review)
        if (reviewBtn) {
            reviewBtn.addEventListener('click', () => {
                const isMarked = reviewBtn.classList.toggle('marked');
                reviewBtn.textContent = isMarked ? '★ Bỏ đánh dấu' : '☆ Đánh dấu xem lại';
                autoSave(qId, null, isMarked);
                updatePaletteItem(qId);
            });
        }
    });

    // =========================================================================
    // 5. Câu hỏi ORDERING: Trợ lý ghép mảnh câu sao JLPT
    // =========================================================================
    document.querySelectorAll('.ordering-container').forEach(container => {
        const card = container.closest('.exam-question-card');
        const qId = card ? card.dataset.questionId : null;
        const hiddenInput = container.querySelector('.ordering-hidden-input');
        const pieceBtns = Array.from(container.querySelectorAll('.ordering-piece-btn'));
        const slots = Array.from(container.querySelectorAll('.ordering-slot'));
        const resetBtn = container.querySelector('.btn-reset-ordering');

        let currentOrder = [];

        // Khởi tạo từ giá trị sẵn có (nếu reload trang)
        if (hiddenInput && hiddenInput.value.trim()) {
            currentOrder = hiddenInput.value.split(',').map(s => s.trim()).filter(Boolean);
            renderOrderingUI();
        }

        function renderOrderingUI() {
            slots.forEach((slot, idx) => {
                const choiceId = currentOrder[idx];
                const slotText = slot.querySelector('.slot-text');
                if (choiceId) {
                    const matchedBtn = pieceBtns.find(b => b.dataset.choiceId === choiceId);
                    if (slotText) slotText.textContent = matchedBtn ? matchedBtn.dataset.choiceText : choiceId;
                    slot.classList.add('filled');
                } else {
                    if (slotText) slotText.textContent = slot.dataset.slotLabel || '';
                    slot.classList.remove('filled');
                }
            });

            pieceBtns.forEach(btn => {
                const isUsed = currentOrder.includes(btn.dataset.choiceId);
                btn.classList.toggle('used', isUsed);
                btn.disabled = isUsed;
            });

            if (hiddenInput) {
                hiddenInput.value = currentOrder.join(',');
            }
        }

        pieceBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                const choiceId = btn.dataset.choiceId;
                if (!currentOrder.includes(choiceId) && currentOrder.length < slots.length) {
                    currentOrder.push(choiceId);
                    renderOrderingUI();
                    if (qId) {
                        autoSave(qId, currentOrder.join(','), null);
                        updatePaletteItem(qId);
                    }
                }
            });
        });

        if (resetBtn) {
            resetBtn.addEventListener('click', () => {
                currentOrder = [];
                renderOrderingUI();
                if (qId) {
                    autoSave(qId, '', null);
                    updatePaletteItem(qId);
                }
            });
        }
    });

    // =========================================================================
    // 6. Chế độ xem: Từng câu (Single) hoặc Cả phần (Section)
    // =========================================================================
    const btnSingleMode = document.getElementById('btnSingleMode');
    const btnSectionMode = document.getElementById('btnSectionMode');
    const layoutGrid = document.querySelector('.exam-layout-grid');

    let currentMode = 'section'; // 'single' hoặc 'section'
    let currentQuestionIdx = 0;

    try {
        const storedMode = localStorage.getItem('nihon_exam_view_mode');
        if (storedMode === 'single' || storedMode === 'section') {
            currentMode = storedMode;
        }
    } catch (e) {
        console.warn(e);
    }

    function applyViewMode() {
        if (!layoutGrid) return;
        if (currentMode === 'single') {
            layoutGrid.classList.add('mode-single');
            layoutGrid.classList.remove('mode-section');
            btnSingleMode?.classList.add('active');
            btnSectionMode?.classList.remove('active');

            questionCards.forEach((card, idx) => {
                if (idx === currentQuestionIdx) {
                    card.classList.add('active-single');
                    card.classList.add('active-question');
                } else {
                    card.classList.remove('active-single');
                    card.classList.remove('active-question');
                }
            });
        } else {
            layoutGrid.classList.add('mode-section');
            layoutGrid.classList.remove('mode-single');
            btnSectionMode?.classList.add('active');
            btnSingleMode?.classList.remove('active');

            questionCards.forEach(card => {
                card.classList.remove('active-single');
            });
        }
        highlightPaletteActive(currentQuestionIdx);
    }

    function highlightPaletteActive(idx) {
        paletteBtns.forEach((btn, i) => {
            btn.classList.toggle('active', i === idx);
        });
    }

    function goToQuestion(idx) {
        if (idx < 0 || idx >= questionCards.length) return;
        currentQuestionIdx = idx;

        if (currentMode === 'single') {
            applyViewMode();
        } else {
            const targetCard = questionCards[idx];
            if (targetCard) {
                targetCard.scrollIntoView({ behavior: 'smooth', block: 'center' });
                targetCard.classList.add('active-question');
                setTimeout(() => targetCard.classList.remove('active-question'), 1500);
            }
            highlightPaletteActive(idx);
        }
    }

    btnSingleMode?.addEventListener('click', () => {
        currentMode = 'single';
        try { localStorage.setItem('nihon_exam_view_mode', 'single'); } catch (e) {}
        applyViewMode();
    });

    btnSectionMode?.addEventListener('click', () => {
        currentMode = 'section';
        try { localStorage.setItem('nihon_exam_view_mode', 'section'); } catch (e) {}
        applyViewMode();
    });

    // Bấm palette để nhảy đến câu
    paletteBtns.forEach((btn, idx) => {
        btn.addEventListener('click', () => {
            goToQuestion(idx);
        });
    });

    // Nút Câu trước / Câu sau ở chế độ từng câu
    document.querySelectorAll('.btn-prev-question').forEach(btn => {
        btn.addEventListener('click', () => {
            if (currentQuestionIdx > 0) goToQuestion(currentQuestionIdx - 1);
        });
    });

    document.querySelectorAll('.btn-next-question').forEach(btn => {
        btn.addEventListener('click', () => {
            if (currentQuestionIdx < questionCards.length - 1) goToQuestion(currentQuestionIdx + 1);
        });
    });

    applyViewMode();
    updatePaletteCounter();

    // =========================================================================
    // 7. Phím tắt bàn phím (Keyboard Shortcuts)
    // Mũi tên trái/phải chuyển câu, Phím 1-4 chọn đáp án trắc nghiệm
    // =========================================================================
    document.addEventListener('keydown', (e) => {
        const tag = document.activeElement ? document.activeElement.tagName.toLowerCase() : '';
        const isEditable = document.activeElement ? document.activeElement.isContentEditable : false;

        // Bỏ qua nếu người học đang gõ vào ô nhập liệu
        if (tag === 'input' || tag === 'textarea' || tag === 'select' || isEditable) {
            return;
        }

        if (e.key === 'ArrowLeft') {
            e.preventDefault();
            if (currentQuestionIdx > 0) {
                goToQuestion(currentQuestionIdx - 1);
            }
        } else if (e.key === 'ArrowRight') {
            e.preventDefault();
            if (currentQuestionIdx < questionCards.length - 1) {
                goToQuestion(currentQuestionIdx + 1);
            }
        } else if (['1', '2', '3', '4'].includes(e.key)) {
            // Chọn đáp án 1, 2, 3, 4 cho câu hiện tại
            const activeCard = questionCards[currentQuestionIdx];
            if (activeCard) {
                const choiceIndex = parseInt(e.key, 10);
                const targetRadio = activeCard.querySelector(`.choice-radio-input[data-choice-order="${choiceIndex}"]`);
                if (targetRadio) {
                    e.preventDefault();
                    targetRadio.checked = true;
                    targetRadio.dispatchEvent(new Event('change'));
                }
            }
        }
    });

    // =========================================================================
    // 8. Chống nộp bài hai lần (Prevent Double Submit)
    // =========================================================================
    if (finishForm) {
        finishForm.addEventListener('submit', (e) => {
            const unanswered = questionCards.filter(c => !isQuestionAnswered(c)).length;
            let confirmMsg = 'Bạn có chắc chắn muốn nộp bài thi không?';
            if (unanswered > 0) {
                confirmMsg = `Bạn còn ${unanswered} câu chưa trả lời. Bạn có chắc chắn muốn nộp bài thi ngay không?`;
            }

            if (!confirm(confirmMsg)) {
                e.preventDefault();
                return;
            }

            const submitBtn = finishForm.querySelector('button[type="submit"]');
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.textContent = 'Đang nộp bài...';
            }
        });
    }
});
