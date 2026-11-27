/**
 * flashcards.js - Xử lý tương tác lật thẻ và chuyển đổi từ vựng
 * Phù hợp CSP: Không inline script, nhận dữ liệu qua data-* attribute
 */
document.addEventListener('DOMContentLoaded', function () {
    const cardElement = document.getElementById('flashcard-card');
    if (!cardElement) {
        return; // Trang không có thẻ (ví dụ danh sách rỗng)
    }

    const dataContainer = document.getElementById('flashcard-data-list');
    if (!dataContainer) {
        return;
    }

    const dataItems = Array.from(dataContainer.querySelectorAll('.flashcard-data-item'));
    if (dataItems.length === 0) {
        return;
    }

    const stageElement = document.getElementById('flashcard-stage');
    const side = (stageElement && stageElement.getAttribute('data-side')) || 'JA';

    // Các phần tử hiển thị trạng thái
    const currentNumEl = document.getElementById('card-current-num');
    const totalNumEl = document.getElementById('card-total-num');
    const btnPrev = document.getElementById('btn-prev-card');
    const btnNext = document.getElementById('btn-next-card');
    const btnFlip = document.getElementById('btn-flip-card');

    // Các phần tử nội dung trên 2 mặt thẻ
    // Mặt trước
    const frontJpWrapper = document.getElementById('front-japanese-wrapper');
    const frontMeaningWrapper = document.getElementById('front-meaning-wrapper');
    const frontSpeakerBtn = document.getElementById('front-speaker-btn');

    // Mặt sau
    const backJpWrapper = document.getElementById('back-japanese-wrapper');
    const backMeaningWrapper = document.getElementById('back-meaning-wrapper');
    const backRomajiWrapper = document.getElementById('back-romaji-wrapper');
    const backSpeakerBtn = document.getElementById('back-speaker-btn');

    let currentIndex = 0;
    const totalCards = dataItems.length;

    if (totalNumEl) {
        totalNumEl.textContent = String(totalCards);
    }

    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Tạo phần tử hiển thị tiếng Nhật kèm furigana (nếu có reading)
     */
    function renderJapaneseWord(baseText, reading) {
        if (reading && reading.trim() !== '') {
            const ruby = document.createElement('ruby');
            ruby.appendChild(document.createTextNode(baseText || ''));
            const rt = document.createElement('rt');
            rt.textContent = reading.trim();
            ruby.appendChild(rt);
            return ruby;
        } else {
            return document.createTextNode(baseText || '');
        }
    }

    /**
     * Lật thẻ qua lại giữa mặt trước và mặt sau
     */
    function flipCard() {
        const isFlipped = cardElement.classList.toggle('is-flipped');
        cardElement.setAttribute('aria-expanded', isFlipped ? 'true' : 'false');
    }

    /**
     * Cập nhật nội dung thẻ theo chỉ số index
     */
    function showCard(index) {
        if (index < 0 || index >= totalCards) {
            return;
        }

        currentIndex = index;

        // Reset trạng thái lật thẻ về mặt trước khi chuyển thẻ
        cardElement.classList.remove('is-flipped');
        cardElement.setAttribute('aria-expanded', 'false');

        // Cập nhật số thứ tự thẻ
        if (currentNumEl) {
            currentNumEl.textContent = String(currentIndex + 1);
        }

        // Lấy dữ liệu của thẻ hiện tại
        const item = dataItems[currentIndex];
        const baseJp = item.getAttribute('data-base-japanese') || item.getAttribute('data-japanese') || '';
        const reading = item.getAttribute('data-reading') || '';
        const romaji = item.getAttribute('data-romaji') || '';
        const meaning = item.getAttribute('data-meaning') || '';
        const speechText = item.getAttribute('data-speech-text') || baseJp;

        if (side === 'VI') {
            // Mặt trước: Tiếng Việt
            if (frontMeaningWrapper) {
                frontMeaningWrapper.textContent = meaning;
            }

            // Mặt sau: Tiếng Nhật + Romaji + Nút Loa
            if (backJpWrapper) {
                backJpWrapper.innerHTML = '';
                backJpWrapper.appendChild(renderJapaneseWord(baseJp, reading));
            }
            if (backRomajiWrapper) {
                backRomajiWrapper.textContent = romaji ? romaji : '';
            }
            if (backSpeakerBtn) {
                backSpeakerBtn.setAttribute('data-speech-text', speechText);
            }
        } else {
            // Mặc định side === 'JA': Mặt trước Tiếng Nhật, Mặt sau Tiếng Việt
            if (frontJpWrapper) {
                frontJpWrapper.innerHTML = '';
                frontJpWrapper.appendChild(renderJapaneseWord(baseJp, reading));
            }
            if (frontSpeakerBtn) {
                frontSpeakerBtn.setAttribute('data-speech-text', speechText);
            }

            if (backMeaningWrapper) {
                backMeaningWrapper.textContent = meaning;
            }
            if (backRomajiWrapper) {
                backRomajiWrapper.textContent = romaji ? romaji : '';
            }
            if (backSpeakerBtn) {
                backSpeakerBtn.setAttribute('data-speech-text', speechText);
            }
        }

        // Cập nhật trạng thái disabled của các nút điều hướng
        if (btnPrev) {
            btnPrev.disabled = (currentIndex === 0);
        }
        if (btnNext) {
            btnNext.disabled = (currentIndex === totalCards - 1);
        }
    }

    // Sự kiện lật thẻ khi click vào thẻ (bỏ qua nếu click vào nút phát âm)
    cardElement.addEventListener('click', function (e) {
        if (e.target.closest('.btn-speaker')) {
            return; // Đang bấm nút loa nghe phát âm, không lật thẻ
        }
        flipCard();
    });

    // Sự kiện nút lật thẻ riêng
    if (btnFlip) {
        btnFlip.addEventListener('click', function (e) {
            e.preventDefault();
            flipCard();
        });
    }

    // Sự kiện chuyển sang thẻ trước
    if (btnPrev) {
        btnPrev.addEventListener('click', function (e) {
            e.preventDefault();
            if (currentIndex > 0) {
                showCard(currentIndex - 1);
            }
        });
    }

    // Sự kiện chuyển sang thẻ tiếp theo
    if (btnNext) {
        btnNext.addEventListener('click', function (e) {
            e.preventDefault();
            if (currentIndex < totalCards - 1) {
                showCard(currentIndex + 1);
            }
        });
    }

    // Bắt phím tắt Space và mũi tên trái/phải
    document.addEventListener('keydown', function (e) {
        // Bỏ qua nếu người dùng đang nhập liệu trong form control
        const activeTag = document.activeElement ? document.activeElement.tagName.toLowerCase() : '';
        if (activeTag === 'input' || activeTag === 'textarea' || activeTag === 'select') {
            return;
        }

        if (e.code === 'Space' || e.key === ' ') {
            e.preventDefault();
            flipCard();
        } else if (e.code === 'ArrowLeft' || e.key === 'ArrowLeft') {
            e.preventDefault();
            if (currentIndex > 0) {
                showCard(currentIndex - 1);
            }
        } else if (e.code === 'ArrowRight' || e.key === 'ArrowRight') {
            e.preventDefault();
            if (currentIndex < totalCards - 1) {
                showCard(currentIndex + 1);
            }
        }
    });

    // Xử lý thay đổi chủ đề trong dropdown
    const topicSelect = document.getElementById('topicSelect');
    if (topicSelect) {
        topicSelect.addEventListener('change', function () {
            const form = topicSelect.closest('form');
            if (form) {
                form.submit();
            }
        });
    }

    // Khởi tạo thẻ đầu tiên (SSR đã render thẻ index 0, hàm này đồng bộ các nút và state)
    showCard(0);
});

