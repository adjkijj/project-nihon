/**
 * Kanji Stroke Order Player (KanjiVG) - F19
 *
 * Phát hoạt ảnh thứ tự nét viết chữ Hán an toàn từ dữ liệu SVG cùng nguồn (KanjiVG).
 * Tuân thủ nghiêm ngặt CSP: không eval, không inline event handler, chỉ phân tích các path d an toàn.
 */
(function() {
    'use strict';

    const SVG_NS = 'http://www.w3.org/2000/svg';
    // Biểu thức chính quy nghiêm ngặt: chỉ cho phép các lệnh SVG path hợp lệ, số, dấu và khoảng trắng
    const SVG_D_REGEX = /^[MmLlHhVvCcSsQqTtAaZz0-9\s.,+-]+$/;

    function initStrokePlayer() {
        const box = document.getElementById('stroke-order-box');
        if (!box) {
            return;
        }

        const strokeUrl = box.getAttribute('data-stroke-url');
        if (!strokeUrl) {
            return;
        }

        const btnPlay = document.getElementById('btn-stroke-play');
        const btnReplay = document.getElementById('btn-stroke-replay');
        const btnNumbers = document.getElementById('btn-stroke-numbers');
        const canvasArea = document.getElementById('stroke-canvas-area');
        const errorMsg = document.getElementById('stroke-error-msg');

        function showError() {
            if (errorMsg) {
                errorMsg.classList.remove('u-hidden');
            }
            if (btnPlay) btnPlay.classList.add('u-hidden');
            if (btnReplay) btnReplay.classList.add('u-hidden');
            if (btnNumbers) btnNumbers.classList.add('u-hidden');
            if (canvasArea) canvasArea.innerHTML = '';
        }

        fetch(strokeUrl, {
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

            setupPlayer(validPathDatas, canvasArea, btnPlay, btnReplay, btnNumbers);
        })
        .catch(function(err) {
            showError();
        });
    }

    function setupPlayer(pathDatas, canvasArea, btnPlay, btnReplay, btnNumbers) {
        canvasArea.innerHTML = '';

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
        var animatedPaths = [];
        var pathLengths = [];

        for (var p = 0; p < pathDatas.length; p++) {
            var path = document.createElementNS(SVG_NS, 'path');
            path.setAttribute('d', pathDatas[p]);
            path.setAttribute('class', 'stroke-path');
            strokeGroup.appendChild(path);
            animatedPaths.push(path);
        }
        svg.appendChild(strokeGroup);

        // 4. Nhóm số thứ tự nét
        var numbersGroup = document.createElementNS(SVG_NS, 'g');
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

        var isAnimating = false;
        var animationTimer = null;
        var prefersReducedMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

        function renderAllInstantly() {
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
            for (var j = 0; j < animatedPaths.length; j++) {
                var l = pathLengths[j];
                animatedPaths[j].style.strokeDasharray = l + ' ' + l;
                animatedPaths[j].style.strokeDashoffset = l;
                animatedPaths[j].classList.remove('stroke-active', 'stroke-drawn');
            }
        }

        function playStrokes() {
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

                var current = animatedPaths[currentIdx];
                current.classList.add('stroke-active');
                current.style.strokeDashoffset = '0';

                animationTimer = setTimeout(function() {
                    current.classList.remove('stroke-active');
                    current.classList.add('stroke-drawn');
                    currentIdx++;
                    runNextStroke();
                }, 520);
            }

            runNextStroke();
        }

        // Mặc định ban đầu: nếu giảm chuyển động thì hiện ngay, nếu không thì hiện nét mờ sẵn sàng phát
        if (prefersReducedMotion) {
            renderAllInstantly();
        } else {
            resetAllStrokes();
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
                numbersGroup.classList.toggle('u-hidden');
            });
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initStrokePlayer);
    } else {
        initStrokePlayer();
    }
})();

