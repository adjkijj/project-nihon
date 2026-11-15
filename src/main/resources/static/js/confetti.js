/**
 * Project Nihon - Hiệu ứng pháo giấy ăn mừng (Confetti) bằng Canvas 2D thuần
 *
 * Yêu cầu:
 * 1. Chỉ chạy khi điểm phần trăm từ 90 trở lên trên result.html.
 * 2. Tôn trọng prefers-reduced-motion: không chạy hiệu ứng nếu người dùng bật cấu hình giảm chuyển động.
 * 3. Dừng sau 3 giây và xóa canvas khỏi DOM.
 * 4. Canvas có aria-hidden="true" và pointer-events: none (người dùng click xuyên qua).
 * 5. Tuân thủ Content-Security-Policy (CSP): 100% không dùng thư viện ngoài, không eval, không inline handlers.
 */
(function () {
    'use strict';

    var CONFETTI_DURATION_MS = 3000;
    var MIN_PERCENTAGE = 90.0;
    var COLORS = [
        '#F59E0B', '#EF4444', '#10B981', '#3B82F6',
        '#8B5CF6', '#EC4899', '#06B6D4', '#EAB308',
        '#F97316', '#6366F1'
    ];

    /**
     * Lấy điểm phần trăm từ trang kết quả
     * @returns {number|null} Điểm phần trăm (0 - 100) hoặc null nếu không tìm thấy
     */
    function getScorePercentage() {
        // 1. Tìm element có data-percentage hoặc data-exam-percentage
        var percentageEl = document.querySelector('[data-percentage], [data-exam-percentage]');
        if (percentageEl) {
            var raw = percentageEl.getAttribute('data-percentage') || percentageEl.getAttribute('data-exam-percentage');
            if (raw) {
                var parsed = parseFloat(raw);
                if (!isNaN(parsed)) {
                    return parsed;
                }
            }
        }

        // 2. Fallback: tìm trong các vùng hiển thị điểm số trên result.html
        var candidateSelectors = [
            '.result-score-banner',
            '.result-main-score',
            '.result-header-card',
            '[aria-labelledby="examResultTitle"]'
        ];

        for (var i = 0; i < candidateSelectors.length; i++) {
            var container = document.querySelector(candidateSelectors[i]);
            if (container) {
                var text = container.textContent || '';
                var match = text.match(/(\d+(?:\.\d+)?)\s*%/);
                if (match) {
                    var val = parseFloat(match[1]);
                    if (!isNaN(val)) {
                        return val;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Kiểm tra tùy chọn giảm chuyển động của hệ điều hành / trình duyệt
     * @returns {boolean} true nếu người dùng yêu cầu giảm chuyển động
     */
    function prefersReducedMotion() {
        try {
            if (typeof window.matchMedia === 'function') {
                var motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
                if (motionQuery && motionQuery.matches) {
                    return true;
                }
            }
        } catch (e) {
            // matchMedia không khả dụng hoặc lỗi môi trường
        }
        return false;
    }

    /**
     * Khởi tạo và kích hoạt hiệu ứng Confetti
     */
    function initConfetti() {
        // Kiểm tra 1: prefers-reduced-motion
        if (prefersReducedMotion()) {
            return;
        }

        // Kiểm tra 2: Điểm phần trăm từ 90 trở lên
        var percentage = getScorePercentage();
        if (percentage === null || isNaN(percentage) || percentage < MIN_PERCENTAGE) {
            return;
        }

        // Kiểm tra 3: Không tạo trùng canvas nếu đã tồn tại
        if (document.getElementById('nihon-confetti-canvas')) {
            return;
        }

        // Tạo thẻ Canvas 2D
        var canvas = document.createElement('canvas');
        canvas.id = 'nihon-confetti-canvas';
        canvas.className = 'confetti-canvas';
        canvas.setAttribute('aria-hidden', 'true');
        canvas.setAttribute('tabindex', '-1');

        // pointer-events: none để tương tác chuột click xuyên qua canvas
        canvas.style.pointerEvents = 'none';
        canvas.style.position = 'fixed';
        canvas.style.top = '0';
        canvas.style.left = '0';
        canvas.style.width = '100vw';
        canvas.style.height = '100vh';
        canvas.style.zIndex = '9999';

        var ctx = canvas.getContext('2d');
        if (!ctx) {
            return;
        }

        document.body.appendChild(canvas);

        function resize() {
            var dpr = window.devicePixelRatio || 1;
            canvas.width = Math.floor(window.innerWidth * dpr);
            canvas.height = Math.floor(window.innerHeight * dpr);
            ctx.scale(dpr, dpr);
        }
        resize();
        window.addEventListener('resize', resize);

        // Khởi tạo các mảnh confetti
        var particleCount = 100;
        var particles = [];
        var width = window.innerWidth;
        var height = window.innerHeight;

        for (var p = 0; p < particleCount; p++) {
            particles.push({
                x: Math.random() * width,
                y: Math.random() * (height * 0.5) - (height * 0.3),
                w: 7 + Math.random() * 6,
                h: 10 + Math.random() * 8,
                vx: (Math.random() - 0.5) * 3.5,
                vy: 2.5 + Math.random() * 4.5,
                rotation: Math.random() * Math.PI * 2,
                vRotation: (Math.random() - 0.5) * 0.16,
                wobble: Math.random() * Math.PI * 2,
                vWobble: 0.05 + Math.random() * 0.05,
                color: COLORS[Math.floor(Math.random() * COLORS.length)]
            });
        }

        var startTime = performance.now();
        var animationFrameId = null;
        var isCleanedUp = false;

        function cleanup() {
            if (isCleanedUp) {
                return;
            }
            isCleanedUp = true;
            if (animationFrameId) {
                cancelAnimationFrame(animationFrameId);
                animationFrameId = null;
            }
            window.removeEventListener('resize', resize);
            if (canvas && canvas.parentNode) {
                canvas.parentNode.removeChild(canvas);
            }
        }

        // Hẹn giờ cố định 3 giây để dừng hiệu ứng và xóa canvas
        var timerId = setTimeout(cleanup, CONFETTI_DURATION_MS);

        function render(now) {
            if (isCleanedUp) {
                return;
            }

            var elapsed = now - startTime;
            if (elapsed >= CONFETTI_DURATION_MS) {
                clearTimeout(timerId);
                cleanup();
                return;
            }

            // Tính độ mờ mượt dần trong 600ms cuối
            var opacity = 1.0;
            if (CONFETTI_DURATION_MS - elapsed < 600) {
                opacity = Math.max(0, (CONFETTI_DURATION_MS - elapsed) / 600);
            }

            var currentWidth = window.innerWidth;
            var currentHeight = window.innerHeight;

            ctx.clearRect(0, 0, currentWidth, currentHeight);

            for (var j = 0; j < particles.length; j++) {
                var item = particles[j];

                // Cập nhật tọa độ và góc xoay
                item.x += item.vx + Math.sin(item.wobble) * 1.5;
                item.y += item.vy;
                item.rotation += item.vRotation;
                item.wobble += item.vWobble;
                item.vy += 0.04; // Trọng lực nhẹ

                // Vẽ mảnh pháo giấy
                ctx.save();
                ctx.translate(item.x, item.y);
                ctx.rotate(item.rotation);
                ctx.scale(Math.cos(item.wobble), 1);
                ctx.fillStyle = item.color;
                ctx.globalAlpha = opacity;
                ctx.fillRect(-item.w / 2, -item.h / 2, item.w, item.h);
                ctx.restore();
            }

            animationFrameId = requestAnimationFrame(render);
        }

        animationFrameId = requestAnimationFrame(render);
    }

    // Tự động khởi chạy khi DOM tải xong
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initConfetti);
    } else {
        initConfetti();
    }

    // Xuất ra global scope phục vụ kiểm thử và gọi thủ công nếu cần
    window.NihonConfetti = {
        init: initConfetti,
        getScorePercentage: getScorePercentage,
        prefersReducedMotion: prefersReducedMotion,
        DURATION_MS: CONFETTI_DURATION_MS,
        MIN_PERCENTAGE: MIN_PERCENTAGE
    };
})();

