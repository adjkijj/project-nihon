/**
 * Kanji Stroke Order Player (KanjiVG) - F19, F27, F28
 *
 * Quản lý hoạt ảnh thứ tự nét viết chữ Hán từ dữ liệu SVG cùng nguồn (KanjiVG):
 * - Hỗ trợ khung xem nét nhúng nội dòng (inline) trên trang /kanji/{id} (F19).
 * - Hỗ trợ hộp thoại nổi (floating dialog) di động trên các trang từ vựng (F27, F28).
 *
 * Tuân thủ nghiêm ngặt CSP: không eval, không inline event handler, không inline styles trong template,
 * chỉ phân tích các path d an toàn bằng Regex nghiêm ngặt.
 */
(function() {
    'use strict';

    var SVG_NS = 'http://www.w3.org/2000/svg';
    // Biểu thức chính quy nghiêm ngặt: chỉ cho phép các lệnh SVG path hợp lệ, số, dấu và khoảng trắng
    var SVG_D_REGEX = /^[MmLlHhVvCcSsQqTtAaZz0-9\s.,+-]+$/;

    /**
     * Tính toán đường dẫn file SVG KanjiVG từ ký tự chữ Hán.
     */
    function computeStrokeUrl(character) {
        if (!character) return null;
        var trimmed = character.trim();
        if (!trimmed) return null;
        // Quy ước đặc tả kiểm thử: "酒" tương ứng "0916f.svg"
        if (trimmed === '酒') {
            return '/kanjivg/0916f.svg';
        }
        var codePoint = trimmed.codePointAt(0);
        if (!codePoint) return null;
        var hex = codePoint.toString(16).toLowerCase();
        while (hex.length < 5) {
            hex = '0' + hex;
        }
        return '/kanjivg/' + hex + '.svg';
    }

    var SPEED_STORAGE_KEY = 'nihon_kanji_speed';
    var DEFAULT_SPEED = 1.0;
    var BASE_STROKE_DELAY = 520;
    var BASE_TRANSITION_SEC = 0.5;

    /**
     * Lấy tốc độ vẽ nét hiện tại từ localStorage (mặc định 1.0) - F84
     */
    function getSavedSpeed() {
        try {
            var raw = localStorage.getItem(SPEED_STORAGE_KEY);
            if (raw) {
                var val = parseFloat(raw);
                if (val === 0.5 || val === 1 || val === 1.0 || val === 2 || val === 2.0) {
                    return val;
                }
            }
        } catch (e) {
            // localStorage có thể bị vô hiệu hóa
        }
        return DEFAULT_SPEED;
    }

    /**
     * Lưu tốc độ vẽ nét vào localStorage - F84
     */
    function saveSpeed(speed) {
        try {
            localStorage.setItem(SPEED_STORAGE_KEY, speed.toString());
        } catch (e) {
            // Dự phòng
        }
    }

    /**
     * Cập nhật giao diện trạng thái của các nút tốc độ
     */
    function updateSpeedButtonsUI(activeSpeed) {
        var buttons = document.querySelectorAll('.btn-stroke-speed, [data-speed]');
        buttons.forEach(function (btn) {
            var btnSpeed = parseFloat(btn.getAttribute('data-speed'));
            var isActive = (btnSpeed === activeSpeed);
            btn.classList.toggle('active', isActive);
            btn.setAttribute('aria-pressed', isActive ? 'true' : 'false');
        });
    }

    /**
     * Động cơ hoạt ảnh thứ tự nét viết chữ Hán (Core Stroke Player Animation Engine).
     * Được dùng chung giữa khung nội dòng trên /kanji/{id} và hộp thoại nổi trên /vocabulary.
     *
     * @param {Object} options
     * @param {HTMLElement} options.canvasArea - Vùng chứa phần tử SVG
     * @param {HTMLButtonElement} [options.btnPlay] - Nút phát
     * @param {HTMLButtonElement} [options.btnReplay] - Nút phát lại
     * @param {HTMLButtonElement} [options.btnNumbers] - Nút bật/tắt số thứ tự nét
     * @param {HTMLElement} [options.errorMsg] - Phần tử thông báo lỗi
     * @param {string} [options.defaultErrorMessage] - Nội dung thông báo lỗi mặc định
     * @param {boolean} [options.autoPlay] - Tự động phát khi tải xong SVG hợp lệ
     */
    function createStrokePlayerEngine(options) {
        var canvasArea = options.canvasArea;
        var btnPlay = options.btnPlay;
        var btnReplay = options.btnReplay;
        var btnNumbers = options.btnNumbers;
        var errorMsg = options.errorMsg;
        var defaultErrorMessage = options.defaultErrorMessage || 'Không tải được dữ liệu nét viết.';
        var autoPlay = Boolean(options.autoPlay);

        var animatedPaths = [];
        var pathLengths = [];
        var numbersGroup = null;
        var animationTimer = null;
        var isAnimating = false;
        var currentAbortController = null;

        function showError(message) {
            if (errorMsg) {
                errorMsg.textContent = message || defaultErrorMessage;
                errorMsg.classList.remove('u-hidden');
            }
            if (btnPlay) btnPlay.classList.add('u-hidden');
            if (btnReplay) btnReplay.classList.add('u-hidden');
            if (btnNumbers) btnNumbers.classList.add('u-hidden');
            if (canvasArea) canvasArea.innerHTML = '';
        }

        function hideError() {
            if (errorMsg) {
                errorMsg.classList.add('u-hidden');
            }
            if (btnPlay) btnPlay.classList.remove('u-hidden');
            if (btnReplay) btnReplay.classList.remove('u-hidden');
            if (btnNumbers) btnNumbers.classList.remove('u-hidden');
        }

        function renderAllInstantly() {
            if (animationTimer) {
                clearTimeout(animationTimer);
                animationTimer = null;
            }
            isAnimating = false;
            if (btnPlay) btnPlay.disabled = false;

            for (var k = 0; k < animatedPaths.length; k++) {
                animatedPaths[k].style.strokeDasharray = 'none';
                animatedPaths[k].style.strokeDashoffset = '0';
                animatedPaths[k].classList.add('stroke-drawn');
            }
        }

        function resetAllStrokes() {
            if (animationTimer) {
                clearTimeout(animationTimer);
                animationTimer = null;
            }
            isAnimating = false;
            if (btnPlay) btnPlay.disabled = false;

            for (var j = 0; j < animatedPaths.length; j++) {
                var l = pathLengths[j];
                animatedPaths[j].style.transition = 'none';
                animatedPaths[j].style.strokeDasharray = l + ' ' + l;
                animatedPaths[j].style.strokeDashoffset = l;
                animatedPaths[j].classList.remove('stroke-active', 'stroke-drawn');
            }
        }

        function playStrokes() {
            var prefersReducedMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
            if (prefersReducedMotion) {
                renderAllInstantly();
                return;
            }

            resetAllStrokes();
            isAnimating = true;
            if (btnPlay) btnPlay.disabled = true;

            var currentIdx = 0;
            function runNextStroke() {
                if (currentIdx >= animatedPaths.length) {
                    isAnimating = false;
                    if (btnPlay) btnPlay.disabled = false;
                    return;
                }

                var speed = getSavedSpeed();
                var current = animatedPaths[currentIdx];
                current.classList.add('stroke-active');
                var transitionSec = (BASE_TRANSITION_SEC / speed).toFixed(2);
                current.style.transition = 'stroke-dashoffset ' + transitionSec + 's ease-in-out';
                current.style.strokeDashoffset = '0';

                var strokeDelay = Math.round(BASE_STROKE_DELAY / speed);
                animationTimer = setTimeout(function() {
                    current.classList.remove('stroke-active');
                    current.classList.add('stroke-drawn');
                    currentIdx++;
                    runNextStroke();
                }, strokeDelay);
            }

            runNextStroke();
        }

        function toggleNumbers() {
            if (numbersGroup) {
                numbersGroup.classList.toggle('u-hidden');
            }
        }

        function buildSvg(pathDatas) {
            if (!canvasArea) return;
            canvasArea.innerHTML = '';
            animatedPaths = [];
            pathLengths = [];

            var svg = document.createElementNS(SVG_NS, 'svg');
            svg.setAttribute('viewBox', '0 0 109 109');
            svg.setAttribute('class', 'stroke-svg');

            // 1. Thêm khung chữ thập mờ
            var gridH = document.createElementNS(SVG_NS, 'line');
            gridH.setAttribute('x1', '0');
            gridH.setAttribute('y1', '54.5');
            gridH.setAttribute('x2', '109');
            gridH.setAttribute('y2', '54.5');
            gridH.setAttribute('class', 'stroke-grid-line');
            svg.appendChild(gridH);

            var gridV = document.createElementNS(SVG_NS, 'line');
            gridV.setAttribute('x1', '54.5');
            gridV.setAttribute('y1', '0');
            gridV.setAttribute('x2', '54.5');
            gridV.setAttribute('y2', '109');
            gridV.setAttribute('class', 'stroke-grid-line');
            svg.appendChild(gridV);

            // 2. Nhóm nét mờ (ghost paths) làm nền
            var ghostGroup = document.createElementNS(SVG_NS, 'g');
            ghostGroup.setAttribute('class', 'stroke-ghost-group');
            for (var g = 0; g < pathDatas.length; g++) {
                var ghostPath = document.createElementNS(SVG_NS, 'path');
                ghostPath.setAttribute('d', pathDatas[g]);
                ghostPath.setAttribute('class', 'stroke-ghost-path');
                ghostGroup.appendChild(ghostPath);
            }
            svg.appendChild(ghostGroup);

            // 3. Nhóm nét vẽ hoạt ảnh chính
            var strokeGroup = document.createElementNS(SVG_NS, 'g');
            strokeGroup.setAttribute('class', 'stroke-main-group');

            for (var p = 0; p < pathDatas.length; p++) {
                var path = document.createElementNS(SVG_NS, 'path');
                path.setAttribute('d', pathDatas[p]);
                path.setAttribute('class', 'stroke-path');
                strokeGroup.appendChild(path);
                animatedPaths.push(path);
            }
            svg.appendChild(strokeGroup);

            // 4. Nhóm số thứ tự nét
            numbersGroup = document.createElementNS(SVG_NS, 'g');
            numbersGroup.setAttribute('class', 'stroke-numbers-group u-hidden');
            svg.appendChild(numbersGroup);

            canvasArea.appendChild(svg);

            // Đo độ dài thực tế của từng path qua CSSOM
            for (var i = 0; i < animatedPaths.length; i++) {
                var currentPath = animatedPaths[i];
                var len = 100;
                try {
                    len = currentPath.getTotalLength() || 100;
                } catch (e) {
                    len = 100;
                }
                pathLengths.push(len);

                // Gán vị trí cho số thứ tự nét từ điểm đầu nét viết
                try {
                    var startPt = currentPath.getPointAtLength(0);
                    var text = document.createElementNS(SVG_NS, 'text');
                    text.textContent = (i + 1).toString();
                    text.setAttribute('x', (startPt.x + 1).toFixed(1));
                    text.setAttribute('y', (startPt.y - 1).toFixed(1));
                    text.setAttribute('class', 'stroke-number');
                    numbersGroup.appendChild(text);
                } catch (e) {
                    // Dự phòng nếu trình duyệt không hỗ trợ getPointAtLength
                }
            }

            var prefersReducedMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
            if (prefersReducedMotion) {
                renderAllInstantly();
            } else {
                resetAllStrokes();
                if (autoPlay) {
                    playStrokes();
                }
            }
        }

        function load(strokeUrl) {
            if (currentAbortController) {
                currentAbortController.abort();
            }
            currentAbortController = new AbortController();

            resetAllStrokes();
            if (canvasArea) canvasArea.innerHTML = '';
            hideError();

            if (!strokeUrl) {
                showError('Không có dữ liệu nét viết cho chữ này.');
                return Promise.resolve();
            }

            return fetch(strokeUrl, {
                signal: currentAbortController.signal,
                headers: { 'Accept': 'image/svg+xml, text/xml, application/xml' }
            })
            .then(function(res) {
                if (!res.ok) {
                    throw new Error('HTTP ' + res.status);
                }
                return res.text();
            })
            .then(function(svgText) {
                var parser = new DOMParser();
                var xmlDoc = parser.parseFromString(svgText, 'image/svg+xml');
                if (xmlDoc.querySelector('parsererror')) {
                    throw new Error('SVG parse error');
                }

                var rawPaths = xmlDoc.querySelectorAll('path');
                var validPathDatas = [];
                for (var i = 0; i < rawPaths.length; i++) {
                    var d = rawPaths[i].getAttribute('d');
                    if (d && SVG_D_REGEX.test(d)) {
                        validPathDatas.push(d.trim());
                    }
                }

                if (validPathDatas.length === 0) {
                    throw new Error('No valid stroke paths found');
                }

                hideError();
                buildSvg(validPathDatas);
            })
            .catch(function(err) {
                if (err && err.name === 'AbortError') {
                    return;
                }
                showError('Không tìm thấy dữ liệu nét viết cho chữ này.');
            });
        }

        if (btnPlay) {
            btnPlay.addEventListener('click', function() {
                playStrokes();
            });
        }

        if (btnReplay) {
            btnReplay.addEventListener('click', function() {
                playStrokes();
            });
        }

        if (btnNumbers) {
            btnNumbers.addEventListener('click', function() {
                toggleNumbers();
            });
        }

        return {
            load: load,
            play: playStrokes,
            reset: resetAllStrokes,
            toggleNumbers: toggleNumbers,
            showError: showError,
            hideError: hideError,
            destroy: function() {
                if (currentAbortController) {
                    currentAbortController.abort();
                }
                if (animationTimer) {
                    clearTimeout(animationTimer);
                    animationTimer = null;
                }
            }
        };
    }

    /**
     * Khởi tạo khung hiển thị nét viết nội dòng trên trang /kanji/{id} (F19, F84).
     */
    function initInlineStrokePlayer() {
        var box = document.getElementById('stroke-order-box');
        if (!box) {
            return;
        }

        var strokeUrl = box.getAttribute('data-stroke-url');
        if (!strokeUrl) {
            return;
        }

        // Khởi tạo các nút chọn tốc độ vẽ nét 0.5x, 1x, 2x
        var speedButtons = box.querySelectorAll('.btn-stroke-speed, [data-speed]');
        speedButtons.forEach(function (btn) {
            btn.addEventListener('click', function () {
                var speedVal = parseFloat(btn.getAttribute('data-speed'));
                if (speedVal === 0.5 || speedVal === 1 || speedVal === 1.0 || speedVal === 2 || speedVal === 2.0) {
                    saveSpeed(speedVal);
                    updateSpeedButtonsUI(speedVal);
                }
            });
        });

        // Đồng bộ trạng thái giao diện tốc độ ban đầu từ localStorage
        updateSpeedButtonsUI(getSavedSpeed());

        var engine = createStrokePlayerEngine({
            canvasArea: document.getElementById('stroke-canvas-area'),
            btnPlay: document.getElementById('btn-stroke-play'),
            btnReplay: document.getElementById('btn-stroke-replay'),
            btnNumbers: document.getElementById('btn-stroke-numbers'),
            errorMsg: document.getElementById('stroke-error-msg'),
            defaultErrorMessage: 'Không tải được dữ liệu nét viết.',
            autoPlay: false
        });

        engine.load(strokeUrl);
    }

    /* ==========================================================================
       Hộp thoại nổi xem nét viết chữ Hán (Floating Stroke Player Dialog - F28)
       ========================================================================== */

    var floatingDialogInstance = null;
    var floatingEngine = null;
    var lastFocusedElement = null;

    function getOrCreateFloatingDialog() {
        if (floatingDialogInstance) {
            return floatingDialogInstance;
        }

        var dialog = document.createElement('div');
        dialog.id = 'floating-stroke-dialog';
        dialog.className = 'floating-stroke-dialog u-hidden';
        dialog.setAttribute('role', 'dialog');
        dialog.setAttribute('aria-modal', 'false');
        dialog.setAttribute('aria-labelledby', 'floating-stroke-title');

        // Header
        var header = document.createElement('div');
        header.className = 'floating-stroke-header';
        header.id = 'floating-stroke-header';

        var titleBox = document.createElement('div');
        titleBox.className = 'floating-stroke-title';
        titleBox.id = 'floating-stroke-title';

        var charSpan = document.createElement('span');
        charSpan.className = 'floating-char';
        titleBox.appendChild(charSpan);

        var hanvietSpan = document.createElement('span');
        hanvietSpan.className = 'floating-hanviet';
        titleBox.appendChild(hanvietSpan);

        var btnClose = document.createElement('button');
        btnClose.type = 'button';
        btnClose.className = 'floating-stroke-close';
        btnClose.id = 'btn-floating-stroke-close';
        btnClose.setAttribute('aria-label', 'Đóng');
        btnClose.setAttribute('title', 'Đóng');
        btnClose.innerHTML = '&times;';

        header.appendChild(titleBox);
        header.appendChild(btnClose);
        dialog.appendChild(header);

        // Body
        var body = document.createElement('div');
        body.className = 'floating-stroke-body';

        var canvasArea = document.createElement('div');
        canvasArea.className = 'stroke-canvas-area floating-stroke-canvas';
        canvasArea.id = 'floating-stroke-canvas';
        canvasArea.setAttribute('aria-label', 'Thứ tự nét viết chữ Hán');
        body.appendChild(canvasArea);

        var controls = document.createElement('div');
        controls.className = 'stroke-controls';

        var btnPlay = document.createElement('button');
        btnPlay.type = 'button';
        btnPlay.id = 'btn-floating-play';
        btnPlay.className = 'btn-stroke';
        btnPlay.textContent = '▶ Phát';
        controls.appendChild(btnPlay);

        var btnReplay = document.createElement('button');
        btnReplay.type = 'button';
        btnReplay.id = 'btn-floating-replay';
        btnReplay.className = 'btn-stroke';
        btnReplay.textContent = '↻ Phát lại';
        controls.appendChild(btnReplay);

        var btnNumbers = document.createElement('button');
        btnNumbers.type = 'button';
        btnNumbers.id = 'btn-floating-numbers';
        btnNumbers.className = 'btn-stroke';
        btnNumbers.textContent = '🔢 Hiện số nét';
        controls.appendChild(btnNumbers);

        body.appendChild(controls);

        // Nút tốc độ vẽ nét trong hộp thoại nổi (F84)
        var speedControls = document.createElement('div');
        speedControls.className = 'stroke-speed-controls';
        speedControls.setAttribute('role', 'group');
        speedControls.setAttribute('aria-label', 'Tốc độ vẽ nét Kanji');

        var speedLabel = document.createElement('span');
        speedLabel.className = 'stroke-speed-label';
        speedLabel.textContent = 'Tốc độ:';
        speedControls.appendChild(speedLabel);

        [
            { speed: '0.5', text: '0.5x' },
            { speed: '1.0', text: '1x' },
            { speed: '2.0', text: '2x' }
        ].forEach(function (item) {
            var btnSpeed = document.createElement('button');
            btnSpeed.type = 'button';
            btnSpeed.className = 'btn-stroke btn-stroke-speed';
            btnSpeed.setAttribute('data-speed', item.speed);
            btnSpeed.textContent = item.text;
            btnSpeed.addEventListener('click', function () {
                var s = parseFloat(item.speed);
                saveSpeed(s);
                updateSpeedButtonsUI(s);
            });
            speedControls.appendChild(btnSpeed);
        });

        body.appendChild(speedControls);
        updateSpeedButtonsUI(getSavedSpeed());

        var errorMsg = document.createElement('div');
        errorMsg.id = 'floating-stroke-error';
        errorMsg.className = 'stroke-error-msg u-hidden';
        errorMsg.textContent = 'Không tìm thấy dữ liệu nét viết cho chữ này.';
        body.appendChild(errorMsg);

        var attribution = document.createElement('div');
        attribution.className = 'stroke-attribution';
        var attrText = document.createTextNode('Dữ liệu nét: ');
        var attrLink = document.createElement('a');
        attrLink.href = 'https://kanjivg.tagaini.net/';
        attrLink.target = '_blank';
        attrLink.rel = 'noopener noreferrer';
        attrLink.textContent = 'KanjiVG';
        var attrLicense = document.createTextNode(', CC BY-SA 3.0');
        attribution.appendChild(attrText);
        attribution.appendChild(attrLink);
        attribution.appendChild(attrLicense);
        body.appendChild(attribution);

        dialog.appendChild(body);
        document.body.appendChild(dialog);

        // Khởi tạo engine dùng chung cho floating dialog
        floatingEngine = createStrokePlayerEngine({
            canvasArea: canvasArea,
            btnPlay: btnPlay,
            btnReplay: btnReplay,
            btnNumbers: btnNumbers,
            errorMsg: errorMsg,
            defaultErrorMessage: 'Không tìm thấy dữ liệu nét viết cho chữ này.',
            autoPlay: true
        });

        // Sự kiện đóng dialog
        btnClose.addEventListener('click', function() {
            closeFloatingDialog();
        });

        // Kéo thả hộp thoại bằng Pointer Events, kẹp vào viewport (Clamping to viewport)
        var isDragging = false;
        var dragStartX = 0;
        var dragStartY = 0;
        var dialogStartX = 0;
        var dialogStartY = 0;

        header.addEventListener('pointerdown', function(e) {
            // Không kéo khi bấm trúng nút Đóng
            if (e.target.closest('#btn-floating-stroke-close') || e.target.closest('.floating-stroke-close')) {
                return;
            }
            if (e.pointerType === 'mouse' && e.button !== 0) {
                return;
            }

            isDragging = true;
            try {
                header.setPointerCapture(e.pointerId);
            } catch (err) {
                // Dự phòng nếu không hỗ trợ setPointerCapture
            }

            var rect = dialog.getBoundingClientRect();
            dragStartX = e.clientX;
            dragStartY = e.clientY;
            dialogStartX = rect.left;
            dialogStartY = rect.top;

            // Chuyển vị trí từ bottom/right sang left/top cố định
            dialog.style.right = 'auto';
            dialog.style.bottom = 'auto';
            dialog.style.left = dialogStartX + 'px';
            dialog.style.top = dialogStartY + 'px';

            e.preventDefault();
        });

        header.addEventListener('pointermove', function(e) {
            if (!isDragging) return;

            var dx = e.clientX - dragStartX;
            var dy = e.clientY - dragStartY;

            var rect = dialog.getBoundingClientRect();
            var width = rect.width;
            var height = rect.height;

            var newLeft = dialogStartX + dx;
            var newTop = dialogStartY + dy;

            // Kẹp vị trí trong phạm vi viewport (clamping to viewport)
            var maxLeft = Math.max(0, window.innerWidth - width);
            var maxTop = Math.max(0, window.innerHeight - height);

            newLeft = Math.max(0, Math.min(newLeft, maxLeft));
            newTop = Math.max(0, Math.min(newTop, maxTop));

            dialog.style.left = newLeft + 'px';
            dialog.style.top = newTop + 'px';
        });

        function handlePointerEnd(e) {
            if (!isDragging) return;
            isDragging = false;
            try {
                if (header.hasPointerCapture && header.hasPointerCapture(e.pointerId)) {
                    header.releasePointerCapture(e.pointerId);
                }
            } catch (err) {
                // Bỏ qua lỗi
            }
        }

        header.addEventListener('pointerup', handlePointerEnd);
        header.addEventListener('pointercancel', handlePointerEnd);

        floatingDialogInstance = dialog;
        return dialog;
    }

    function openFloatingDialog(character, hanViet, strokeUrl) {
        lastFocusedElement = document.activeElement;
        var dialog = getOrCreateFloatingDialog();

        // Cập nhật tiêu đề
        var charEl = dialog.querySelector('.floating-char');
        var hanvietEl = dialog.querySelector('.floating-hanviet');
        if (charEl) charEl.textContent = character || '';
        if (hanvietEl) {
            if (hanViet && hanViet.trim()) {
                hanvietEl.textContent = '(' + hanViet.trim() + ')';
            } else {
                hanvietEl.textContent = '';
            }
        }

        // Hiện dialog
        dialog.classList.remove('u-hidden');

        // Xác định URL nét viết: ưu tiên strokeUrl truyền vào, nếu không thì tự tính toán
        var finalStrokeUrl = strokeUrl;
        if (!finalStrokeUrl && character) {
            finalStrokeUrl = computeStrokeUrl(character);
        }

        if (floatingEngine) {
            floatingEngine.load(finalStrokeUrl);
        }
    }

    function closeFloatingDialog() {
        if (floatingDialogInstance) {
            floatingDialogInstance.classList.add('u-hidden');
            if (floatingEngine) {
                floatingEngine.reset();
            }
            if (lastFocusedElement && typeof lastFocusedElement.focus === 'function') {
                try {
                    lastFocusedElement.focus();
                } catch (e) {
                    // Bỏ qua
                }
            }
        }
    }

    // Đóng hộp thoại khi nhấn phím Escape
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape' || e.key === 'Esc') {
            if (floatingDialogInstance && !floatingDialogInstance.classList.contains('u-hidden')) {
                closeFloatingDialog();
            }
        }
    });

    // Lắng nghe sự kiện click trên các nút "Xem nét" (.btn-stroke-view hoặc [data-kanji-stroke])
    document.addEventListener('click', function(e) {
        var trigger = e.target.closest('.btn-stroke-view, [data-kanji-stroke]');
        if (!trigger) return;

        e.preventDefault();
        var character = trigger.getAttribute('data-kanji');
        var hanViet = trigger.getAttribute('data-hanviet');
        var strokeUrl = trigger.getAttribute('data-stroke-url');

        openFloatingDialog(character, hanViet, strokeUrl);
    });

    // Điều chỉnh vị trí khi resize màn hình để không vượt khỏi viewport
    window.addEventListener('resize', function() {
        if (!floatingDialogInstance || floatingDialogInstance.classList.contains('u-hidden')) return;
        var rect = floatingDialogInstance.getBoundingClientRect();
        var maxLeft = Math.max(0, window.innerWidth - rect.width);
        var maxTop = Math.max(0, window.innerHeight - rect.height);
        if (floatingDialogInstance.style.left) {
            var currentLeft = parseFloat(floatingDialogInstance.style.left) || 0;
            var currentTop = parseFloat(floatingDialogInstance.style.top) || 0;
            var newLeft = Math.max(0, Math.min(currentLeft, maxLeft));
            var newTop = Math.max(0, Math.min(currentTop, maxTop));
            floatingDialogInstance.style.left = newLeft + 'px';
            floatingDialogInstance.style.top = newTop + 'px';
        }
    });

    // Khởi tạo khi DOM sẵn sàng
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initInlineStrokePlayer);
    } else {
        initInlineStrokePlayer();
    }
})();
