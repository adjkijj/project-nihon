/**
 * Kanji Handwriting Practice Canvas (F20)
 *
 * Cho phep nguoi dung tap viet chu Han truc tiep bang chuot hoac man hinh cam ung (Pointer Events)
 * tren o chu thap 300x300, ve de len chu mau voi do mo tuy chon (0%, 20%, 40%).
 *
 * Hoan toan xu ly phia client (khong gui du lieu len server, khong persistence).
 * Tuan thu nghiem ngat Content-Security-Policy (CSP) khong inline handler, khong eval.
 */
(function() {
    'use strict';

    const SVG_D_REGEX = /^[MmLlHhVvCcSsQqTtAaZz0-9\s.,+-]+$/;
    const STROKE_COLOR = '#38bdf8';

    function initKanjiCanvas() {
        const canvas = document.getElementById('kanji-practice-canvas');
        if (!canvas) {
            return;
        }

        const unsupportedNotice = document.getElementById('canvas-unsupported-notice');
        const noDataNotice = document.getElementById('canvas-no-data-notice');
        const controlsPanel = document.getElementById('canvas-controls-panel');
        const practiceWrapper = document.getElementById('kanji-practice-box');

        // 1. Kiem tra ho tro Pointer Events
        if (!window.PointerEvent) {
            canvas.classList.add('u-hidden');
            if (controlsPanel) controlsPanel.classList.add('u-hidden');
            if (unsupportedNotice) unsupportedNotice.classList.remove('u-hidden');
            return;
        }

        const ctx = canvas.getContext('2d');
        if (!ctx) {
            return;
        }

        // Thiet lap kich thuoc thuc te cho canvas
        canvas.width = 300;
        canvas.height = 300;

        let sampleOpacity = 0.2; // Mac dinh 20%
        let currentThickness = 4; // Mac dinh Net Vua (4px)
        let samplePaths = [];
        let strokes = []; // Mang luu tru danh sach cac net: [{ thickness, color, points: [{x, y}] }]
        let currentStroke = null;
        let isDrawing = false;
        let lastX = 0;
        let lastY = 0;

        function showNoDataNotice() {
            if (noDataNotice) {
                noDataNotice.classList.remove('u-hidden');
            }
        }

        // 2. Ve luoi chu thap mo (+) chia canvas 300x300 thanh 4 goc phan tu
        function drawGrid() {
            ctx.save();
            ctx.strokeStyle = 'rgba(148, 163, 184, 0.25)';
            ctx.lineWidth = 1;
            ctx.setLineDash([4, 4]);

            ctx.beginPath();
            // Duong ngang giua y = 150
            ctx.moveTo(0, 150);
            ctx.lineTo(300, 150);
            // Duong doc giua x = 150
            ctx.moveTo(150, 0);
            ctx.lineTo(150, 300);
            ctx.stroke();

            ctx.restore();
        }

        // 3. Ve mau chu Han trong nen theo do mo da chon (ty le 300 / 109)
        function drawSampleCharacter() {
            if (sampleOpacity <= 0 || samplePaths.length === 0) {
                return;
            }

            ctx.save();
            ctx.globalAlpha = sampleOpacity;
            ctx.scale(300 / 109, 300 / 109);
            ctx.strokeStyle = '#94a3b8';
            ctx.lineWidth = 3.5;
            ctx.lineCap = 'round';
            ctx.lineJoin = 'round';

            for (let i = 0; i < samplePaths.length; i++) {
                ctx.stroke(samplePaths[i]);
            }

            ctx.restore();
        }

        // 4. Ve lai toan bo canvas: Luoi -> Chu mau -> Tat ca cac net nguoi dung da ve
        function redraw() {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            drawGrid();
            drawSampleCharacter();

            for (let i = 0; i < strokes.length; i++) {
                const stroke = strokes[i];
                if (!stroke.points || stroke.points.length === 0) continue;

                ctx.save();
                ctx.strokeStyle = stroke.color || STROKE_COLOR;
                ctx.lineWidth = stroke.thickness || 4;
                ctx.lineCap = 'round';
                ctx.lineJoin = 'round';

                if (stroke.points.length === 1) {
                    // Net cham duy nhat
                    ctx.beginPath();
                    ctx.arc(stroke.points[0].x, stroke.points[0].y, (stroke.thickness || 4) / 2, 0, Math.PI * 2);
                    ctx.fillStyle = stroke.color || STROKE_COLOR;
                    ctx.fill();
                } else {
                    ctx.beginPath();
                    ctx.moveTo(stroke.points[0].x, stroke.points[0].y);
                    for (let p = 1; p < stroke.points.length; p++) {
                        ctx.lineTo(stroke.points[p].x, stroke.points[p].y);
                    }
                    ctx.stroke();
                }
                ctx.restore();
            }

            updateActionButtons();
        }

        function updateActionButtons() {
            const btnUndo = document.getElementById('btn-canvas-undo');
            const btnClear = document.getElementById('btn-canvas-clear');
            const hasStrokes = strokes.length > 0;
            if (btnUndo) btnUndo.disabled = !hasStrokes;
            if (btnClear) btnClear.disabled = !hasStrokes;
        }

        // 5. Lay toa do chuan hoa tren Canvas tu PointerEvent
        function getCanvasCoords(e) {
            const rect = canvas.getBoundingClientRect();
            const scaleX = canvas.width / rect.width;
            const scaleY = canvas.height / rect.height;
            return {
                x: (e.clientX - rect.left) * scaleX,
                y: (e.clientY - rect.top) * scaleY
            };
        }

        // 6. Trinh xu ly Pointer Events cho dong co ve net
        function onPointerDown(e) {
            if (e.button !== undefined && e.button !== 0) return;

            if (canvas.setPointerCapture && e.pointerId !== undefined) {
                try {
                    canvas.setPointerCapture(e.pointerId);
                } catch (_) {}
            }

            isDrawing = true;
            const coords = getCanvasCoords(e);
            currentStroke = {
                thickness: currentThickness,
                color: STROKE_COLOR,
                points: [coords]
            };
            lastX = coords.x;
            lastY = coords.y;

            // Ve cham khoi dau
            ctx.save();
            ctx.beginPath();
            ctx.arc(coords.x, coords.y, currentThickness / 2, 0, Math.PI * 2);
            ctx.fillStyle = STROKE_COLOR;
            ctx.fill();
            ctx.restore();
        }

        function onPointerMove(e) {
            if (!isDrawing || !currentStroke) return;

            const coords = getCanvasCoords(e);
            currentStroke.points.push(coords);

            ctx.save();
            ctx.beginPath();
            ctx.moveTo(lastX, lastY);
            ctx.lineTo(coords.x, coords.y);
            ctx.strokeStyle = currentStroke.color;
            ctx.lineWidth = currentStroke.thickness;
            ctx.lineCap = 'round';
            ctx.lineJoin = 'round';
            ctx.stroke();
            ctx.restore();

            lastX = coords.x;
            lastY = coords.y;
        }

        function onPointerUp(e) {
            if (!isDrawing) return;
            isDrawing = false;

            if (canvas.releasePointerCapture && e.pointerId !== undefined) {
                try {
                    canvas.releasePointerCapture(e.pointerId);
                } catch (_) {}
            }

            if (currentStroke && currentStroke.points.length > 0) {
                strokes.push(currentStroke);
            }
            currentStroke = null;
            updateActionButtons();
        }

        function onPointerCancel(e) {
            onPointerUp(e);
        }

        // Dang ky Pointer Events
        canvas.addEventListener('pointerdown', onPointerDown);
        canvas.addEventListener('pointermove', onPointerMove);
        canvas.addEventListener('pointerup', onPointerUp);
        canvas.addEventListener('pointercancel', onPointerCancel);

        // 7. Hoan tac net cuoi cung
        function undoLastStroke() {
            if (strokes.length > 0) {
                strokes.pop();
                redraw();
            }
        }

        // 8. Xoa sach canvas
        function clearCanvas() {
            strokes = [];
            redraw();
        }

        // 9. Thiet lap do day net ve (2px, 4px, 6px)
        function setThickness(thickness) {
            currentThickness = thickness;
            const buttons = document.querySelectorAll('.btn-thickness');
            buttons.forEach(function(btn) {
                const val = parseInt(btn.getAttribute('data-thickness'), 10);
                btn.classList.toggle('active', val === thickness);
            });
            const select = document.getElementById('canvas-thickness-select');
            if (select) select.value = String(thickness);
        }

        // 10. Thiet lap do mo chu mau (0, 0.2, 0.4)
        function setOpacity(opacity) {
            sampleOpacity = opacity;
            const slider = document.getElementById('canvas-opacity-slider');
            if (slider) slider.value = String(opacity);

            const buttons = document.querySelectorAll('.btn-opacity');
            buttons.forEach(function(btn) {
                const val = parseFloat(btn.getAttribute('data-opacity'));
                btn.classList.toggle('active', Math.abs(val - opacity) < 0.01);
            });

            const select = document.getElementById('canvas-opacity-select');
            if (select) select.value = String(opacity);

            redraw();
        }

        // Gan su kien cho cac nut dieu khien
        const btnUndo = document.getElementById('btn-canvas-undo');
        if (btnUndo) {
            btnUndo.addEventListener('click', undoLastStroke);
        }

        const btnClear = document.getElementById('btn-canvas-clear');
        if (btnClear) {
            btnClear.addEventListener('click', clearCanvas);
        }

        // Thickness buttons
        const thicknessButtons = document.querySelectorAll('.btn-thickness');
        thicknessButtons.forEach(function(btn) {
            btn.addEventListener('click', function() {
                const t = parseInt(btn.getAttribute('data-thickness'), 10);
                if (!isNaN(t)) {
                    setThickness(t);
                }
            });
        });

        // Thickness select fallback
        const thicknessSelect = document.getElementById('canvas-thickness-select');
        if (thicknessSelect) {
            thicknessSelect.addEventListener('change', function() {
                const t = parseInt(thicknessSelect.value, 10);
                if (!isNaN(t)) {
                    setThickness(t);
                }
            });
        }

        // Opacity buttons
        const opacityButtons = document.querySelectorAll('.btn-opacity');
        opacityButtons.forEach(function(btn) {
            btn.addEventListener('click', function() {
                const o = parseFloat(btn.getAttribute('data-opacity'));
                if (!isNaN(o)) {
                    setOpacity(o);
                }
            });
        });

        // Opacity slider
        const opacitySlider = document.getElementById('canvas-opacity-slider');
        if (opacitySlider) {
            opacitySlider.addEventListener('input', function() {
                const o = parseFloat(opacitySlider.value);
                if (!isNaN(o)) {
                    setOpacity(o);
                }
            });
        }

        // Opacity select fallback
        const opacitySelect = document.getElementById('canvas-opacity-select');
        if (opacitySelect) {
            opacitySelect.addEventListener('change', function() {
                const o = parseFloat(opacitySelect.value);
                if (!isNaN(o)) {
                    setOpacity(o);
                }
            });
        }

        // 11. Lay du lieu mau chu Kanji tu SVG (KanjiVG)
        const strokeUrl = (practiceWrapper && practiceWrapper.getAttribute('data-stroke-url')) ||
                          canvas.getAttribute('data-stroke-url') ||
                          (document.getElementById('stroke-order-box') ? document.getElementById('stroke-order-box').getAttribute('data-stroke-url') : null);

        if (!strokeUrl || strokeUrl.trim() === '') {
            showNoDataNotice();
            redraw();
            return;
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
            const parser = new DOMParser();
            const xmlDoc = parser.parseFromString(svgText, 'image/svg+xml');
            if (xmlDoc.querySelector('parsererror')) {
                throw new Error('SVG parse error');
            }

            const rawPaths = xmlDoc.querySelectorAll('path');
            const validPaths = [];

            for (let i = 0; i < rawPaths.length; i++) {
                const d = rawPaths[i].getAttribute('d');
                if (d && SVG_D_REGEX.test(d)) {
                    try {
                        if (typeof Path2D !== 'undefined') {
                            validPaths.push(new Path2D(d.trim()));
                        }
                    } catch (_) {}
                }
            }

            if (validPaths.length === 0) {
                throw new Error('No valid stroke paths found');
            }

            samplePaths = validPaths;
            redraw();
        })
        .catch(function() {
            showNoDataNotice();
            redraw();
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initKanjiCanvas);
    } else {
        initKanjiCanvas();
    }
})();
