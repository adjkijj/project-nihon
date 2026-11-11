/**
 * Project Nihon - Trình phát âm tiếng Nhật thuần Web Speech API (Client-side)
 * Tuân thủ bảo mật CSP nghiêm ngặt: không gọi mạng ra ngoài, không dùng API đám mây.
 */
(function () {
    'use strict';

    // 1. Kiểm tra hỗ trợ Web Speech API của trình duyệt
    if (typeof window === 'undefined' || !('speechSynthesis' in window)) {
        hideAllSpeakerButtons();
        return;
    }

    var selectedVoice = null;
    var voicesLoaded = false;
    var noticeShown = false;
    var currentSpeakingBtn = null;

    /**
     * Ẩn tất cả nút loa trên trang
     */
    function hideAllSpeakerButtons() {
        var buttons = document.querySelectorAll('.btn-speaker');
        for (var i = 0; i < buttons.length; i++) {
            buttons[i].style.display = 'none';
        }
    }

    /**
     * Hiển thị ghi chú một lần khi thiết bị chưa có gói giọng đọc tiếng Nhật
     */
    function showNoVoiceNoticeOnce() {
        if (noticeShown) {
            return;
        }
        noticeShown = true;
        hideAllSpeakerButtons();

        // Tạo phần tử thông báo nhỏ cạnh thanh tiêu đề hoặc đầu danh sách
        var container = document.querySelector('.page-title, .question-card, .vocab-detail-card, main');
        if (container) {
            var notice = document.createElement('div');
            notice.className = 'speech-device-notice';
            notice.setAttribute('role', 'status');
            notice.textContent = 'Thiết bị chưa có giọng đọc tiếng Nhật.';
            container.appendChild(notice);
        }
    }

    /**
     * Tìm kiếm giọng đọc tiếng Nhật phù hợp trong danh sách voices
     */
    function findJapaneseVoice() {
        var voices = window.speechSynthesis.getVoices();
        if (!voices || voices.length === 0) {
            return null;
        }

        // Ưu tiên 1: Giọng ja-JP chuẩn
        for (var i = 0; i < voices.length; i++) {
            var v = voices[i];
            var lang = (v.lang || '').toLowerCase().replace('_', '-');
            if (lang === 'ja-jp') {
                return v;
            }
        }

        // Ưu tiên 2: Bất kỳ giọng nào bắt đầu bằng 'ja'
        for (var j = 0; j < voices.length; j++) {
            var voice = voices[j];
            var voiceLang = (voice.lang || '').toLowerCase();
            if (voiceLang.indexOf('ja') === 0) {
                return voice;
            }
        }

        return null;
    }

    /**
     * Khởi tạo và nạp giọng đọc tiếng Nhật
     */
    function initVoices() {
        selectedVoice = findJapaneseVoice();
        if (selectedVoice) {
            voicesLoaded = true;
            return;
        }

        var timerId = null;

        function onVoicesChanged() {
            selectedVoice = findJapaneseVoice();
            if (selectedVoice) {
                voicesLoaded = true;
                if (timerId) {
                    clearTimeout(timerId);
                }
                window.speechSynthesis.removeEventListener('voiceschanged', onVoicesChanged);
            }
        }

        if (window.speechSynthesis.onvoiceschanged !== undefined) {
            window.speechSynthesis.addEventListener('voiceschanged', onVoicesChanged);
        }

        // Thời gian chờ tối đa 2 giây cho voiceschanged
        timerId = setTimeout(function () {
            if (window.speechSynthesis.removeEventListener) {
                window.speechSynthesis.removeEventListener('voiceschanged', onVoicesChanged);
            }
            selectedVoice = findJapaneseVoice();
            if (selectedVoice) {
                voicesLoaded = true;
            } else {
                showNoVoiceNoticeOnce();
            }
        }, 2000);
    }

    /**
     * Đặt lại trạng thái nút đang phát âm
     */
    function clearSpeakingState() {
        if (currentSpeakingBtn) {
            currentSpeakingBtn.classList.remove('is-speaking');
            currentSpeakingBtn = null;
        }
    }

    /**
     * Hiển thị thông báo lỗi khi phát âm không thành công
     */
    function showErrorMessage() {
        alert('Trình duyệt không hỗ trợ đọc tiếng Nhật');
    }

    /**
     * Phát âm đoạn văn bản tiếng Nhật
     */
    function speakText(text, btnElement) {
        if (!text || !text.trim()) {
            return;
        }

        try {
            // Hủy câu đang phát trước đó để tránh nói đè
            window.speechSynthesis.cancel();
            clearSpeakingState();

            var utterance = new SpeechSynthesisUtterance(text.trim());
            utterance.rate = 0.9;
            utterance.lang = 'ja-JP';

            if (selectedVoice) {
                utterance.voice = selectedVoice;
            }

            utterance.onstart = function () {
                if (btnElement) {
                    currentSpeakingBtn = btnElement;
                    btnElement.classList.add('is-speaking');
                }
            };

            utterance.onend = function () {
                clearSpeakingState();
            };

            utterance.onerror = function (e) {
                clearSpeakingState();
                // Không báo lỗi nếu do người dùng bấm nút khác gây cancel
                if (e && e.error && e.error !== 'canceled' && e.error !== 'interrupted') {
                    showErrorMessage();
                }
            };

            window.speechSynthesis.speak(utterance);
        } catch (err) {
            clearSpeakingState();
            showErrorMessage();
        }
    }

    // 2. Ủy quyền sự kiện click trên document tìm nút .btn-speaker
    document.addEventListener('click', function (event) {
        var target = event.target;
        if (!target) {
            return;
        }

        var btn = target.closest ? target.closest('.btn-speaker') : null;
        if (!btn) {
            return;
        }

        event.preventDefault();
        var speechText = btn.getAttribute('data-speech-text');
        speakText(speechText, btn);
    });

    // 3. Khởi động nạp giọng đọc
    initVoices();
})();

