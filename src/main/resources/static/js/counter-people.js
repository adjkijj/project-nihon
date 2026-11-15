/**
 * Project Nihon - Counter People Audio Player (Web Speech API)
 *
 * Phát âm số đếm người (1 - 10 người) bằng Web Speech API thuần client.
 * Tuân thủ CSP nghiêm ngặt: không inline event handlers, không dịch vụ ngoài.
 */
(function () {
    'use strict';

    if (typeof window === 'undefined' || !('speechSynthesis' in window)) {
        return;
    }

    var selectedVoice = null;
    var currentSpeakingBtn = null;
    var DEFAULT_RATE = 0.9;
    var ALLOWED_SPEEDS = ['1.0', '1', '0.85', '0.75'];

    /**
     * Lấy tốc độ phát âm từ NihonSpeech hoặc localStorage
     */
    function getTtsSpeed() {
        if (window.NihonSpeech && typeof window.NihonSpeech.getSpeed === 'function') {
            return window.NihonSpeech.getSpeed();
        }
        try {
            var raw = localStorage.getItem('nihon_tts_speed');
            if (raw && ALLOWED_SPEEDS.indexOf(raw) !== -1) {
                var parsed = parseFloat(raw);
                if (!isNaN(parsed) && parsed > 0) {
                    return parsed;
                }
            }
        } catch (e) {
            // Ignored
        }
        return DEFAULT_RATE;
    }

    /**
     * Tìm kiếm giọng đọc tiếng Nhật trong trình duyệt
     */
    function findJapaneseVoice() {
        var voices = window.speechSynthesis.getVoices();
        if (!voices || voices.length === 0) {
            return null;
        }
        for (var i = 0; i < voices.length; i++) {
            var lang = (voices[i].lang || '').toLowerCase().replace('_', '-');
            if (lang === 'ja-jp') {
                return voices[i];
            }
        }
        for (var j = 0; j < voices.length; j++) {
            if ((voices[j].lang || '').toLowerCase().indexOf('ja') === 0) {
                return voices[j];
            }
        }
        return null;
    }

    function initVoices() {
        selectedVoice = findJapaneseVoice();
        if (!selectedVoice && window.speechSynthesis.onvoiceschanged !== undefined) {
            window.speechSynthesis.addEventListener('voiceschanged', function () {
                selectedVoice = findJapaneseVoice();
            });
        }
    }

    function clearSpeaking() {
        if (currentSpeakingBtn) {
            currentSpeakingBtn.classList.remove('is-speaking');
            currentSpeakingBtn = null;
        }
    }

    /**
     * Phát âm văn bản tiếng Nhật
     */
    function speak(text, btnElement) {
        if (!text || !text.trim()) {
            return;
        }

        try {
            window.speechSynthesis.cancel();
            clearSpeaking();

            var utterance = new SpeechSynthesisUtterance(text.trim());
            utterance.rate = getTtsSpeed();
            utterance.lang = 'ja-JP';

            if (!selectedVoice) {
                selectedVoice = findJapaneseVoice();
            }
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
                clearSpeaking();
            };

            utterance.onerror = function () {
                clearSpeaking();
            };

            window.speechSynthesis.speak(utterance);
        } catch (e) {
            clearSpeaking();
        }
    }

    /**
     * Gắn sự kiện click bằng Event Listener cho các nút đếm người
     */
    function initCounterPeople() {
        var buttons = document.querySelectorAll('.counter-person-btn[data-speak], button[data-speak]');
        buttons.forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                var text = btn.getAttribute('data-speak');
                speak(text, btn);
            });
        });
    }

    initVoices();

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initCounterPeople);
    } else {
        initCounterPeople();
    }
})();

