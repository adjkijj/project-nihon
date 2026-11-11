/**
 * UI Enhancements - Project Nihon
 * F02: Responsive navigation & mobile drawer
 * F04: Accessible keyboard navigation & focus management
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        initNavigation();
        initDropdown();
        initAccessibleConfirm();
        initGlobalKeyboardNav();
    });

    /* ==========================================================================
       1. Mobile Drawer Navigation (F02)
       ========================================================================== */
    function initNavigation() {
        var navToggle = document.getElementById('navToggle');
        var navDrawer = document.getElementById('navDrawer');
        var navBackdrop = document.getElementById('navBackdrop');
        var navDrawerClose = document.getElementById('navDrawerClose');

        if (!navToggle || !navDrawer) {
            return;
        }

        function openDrawer() {
            navDrawer.classList.add('open');
            navDrawer.setAttribute('aria-hidden', 'false');
            navToggle.setAttribute('aria-expanded', 'true');
            if (navBackdrop) {
                navBackdrop.classList.add('show');
                navBackdrop.setAttribute('aria-hidden', 'false');
            }
            // Focus vào nút đóng hoặc phần tử đầu tiên trong drawer
            if (navDrawerClose) {
                navDrawerClose.focus();
            } else {
                var firstLink = navDrawer.querySelector('a, button');
                if (firstLink) firstLink.focus();
            }
        }

        function closeDrawer() {
            if (!navDrawer.classList.contains('open')) {
                return;
            }
            navDrawer.classList.remove('open');
            navDrawer.setAttribute('aria-hidden', 'true');
            navToggle.setAttribute('aria-expanded', 'false');
            if (navBackdrop) {
                navBackdrop.classList.remove('show');
                navBackdrop.setAttribute('aria-hidden', 'true');
            }
            // Trả focus về nút toggle
            navToggle.focus();
        }

        navToggle.addEventListener('click', function (e) {
            e.stopPropagation();
            if (navDrawer.classList.contains('open')) {
                closeDrawer();
            } else {
                openDrawer();
            }
        });

        if (navDrawerClose) {
            navDrawerClose.addEventListener('click', function (e) {
                e.stopPropagation();
                closeDrawer();
            });
        }

        if (navBackdrop) {
            navBackdrop.addEventListener('click', function () {
                closeDrawer();
            });
        }

        // Bấm vào bất kỳ liên kết nào trong drawer thì đóng drawer
        navDrawer.querySelectorAll('a').forEach(function (link) {
            link.addEventListener('click', function () {
                if (window.innerWidth < 768) {
                    closeDrawer();
                }
            });
        });

        // Bấm ra ngoài drawer thì đóng drawer trên màn hình nhỏ
        document.addEventListener('click', function (e) {
            if (window.innerWidth < 768 && navDrawer.classList.contains('open')) {
                if (!navDrawer.contains(e.target) && !navToggle.contains(e.target)) {
                    closeDrawer();
                }
            }
        });

        // Expose helper cho keyboard nav
        window.__nihonCloseDrawer = closeDrawer;
        window.__nihonIsDrawerOpen = function () {
            return navDrawer.classList.contains('open');
        };
    }

    /* ==========================================================================
       2. Practice Dropdown Menu (F02)
       ========================================================================== */
    function initDropdown() {
        var dropdown = document.getElementById('practiceDropdown');
        var toggle = document.getElementById('practiceDropdownToggle');
        var menu = document.getElementById('practiceDropdownMenu');

        if (!dropdown || !toggle) {
            return;
        }

        function openDropdown() {
            dropdown.classList.add('open');
            toggle.setAttribute('aria-expanded', 'true');
        }

        function closeDropdown() {
            if (!dropdown.classList.contains('open')) {
                return;
            }
            dropdown.classList.remove('open');
            toggle.setAttribute('aria-expanded', 'false');
        }

        toggle.addEventListener('click', function (e) {
            e.preventDefault();
            e.stopPropagation();
            if (dropdown.classList.contains('open')) {
                closeDropdown();
            } else {
                openDropdown();
            }
        });

        // Đóng khi click ngoài
        document.addEventListener('click', function (e) {
            if (!dropdown.contains(e.target)) {
                closeDropdown();
            }
        });

        // Hỗ trợ mũi tên lên/xuống bên trong dropdown
        if (menu) {
            menu.addEventListener('keydown', function (e) {
                var items = Array.prototype.slice.call(menu.querySelectorAll('a[role="menuitem"]'));
                var index = items.indexOf(document.activeElement);

                if (e.key === 'ArrowDown') {
                    e.preventDefault();
                    var nextIndex = (index + 1) % items.length;
                    items[nextIndex].focus();
                } else if (e.key === 'ArrowUp') {
                    e.preventDefault();
                    var prevIndex = (index - 1 + items.length) % items.length;
                    items[prevIndex].focus();
                }
            });
        }

        // Expose helper cho keyboard nav
        window.__nihonCloseDropdown = closeDropdown;
        window.__nihonIsDropdownOpen = function () {
            return dropdown.classList.contains('open');
        };
        window.__nihonFocusDropdownToggle = function () {
            toggle.focus();
        };
    }

    /* ==========================================================================
       3. Accessible Confirmation Modal & Focus Management (F04)
       ========================================================================== */
    var lastFocusedTrigger = null;
    var pendingFormSubmission = null;

    function initAccessibleConfirm() {
        var dialog = document.getElementById('nihonConfirmDialog');
        var cancelBtn = document.getElementById('nihonDialogCancelBtn');
        var confirmBtn = document.getElementById('nihonDialogConfirmBtn');
        var closeBtn = document.getElementById('nihonDialogCloseBtn');
        var messageEl = document.getElementById('nihonDialogMessage');

        if (!dialog) {
            return;
        }

        function openConfirm(message, triggerEl, onConfirm) {
            lastFocusedTrigger = triggerEl;
            pendingFormSubmission = onConfirm;
            if (messageEl) {
                messageEl.textContent = message;
            }

            if (typeof dialog.showModal === 'function') {
                dialog.showModal();
            } else {
                dialog.setAttribute('open', '');
            }

            // Quản lý focus: mở hộp thoại thì focus vào nút bên trong (ưu tiên nút Hủy để an toàn)
            if (cancelBtn) {
                cancelBtn.focus();
            } else if (confirmBtn) {
                confirmBtn.focus();
            }
        }

        function closeConfirm() {
            if (typeof dialog.close === 'function') {
                dialog.close();
            } else {
                dialog.removeAttribute('open');
            }
            pendingFormSubmission = null;

            // Quản lý focus: đóng thì trả focus về nút đã mở
            if (lastFocusedTrigger && typeof lastFocusedTrigger.focus === 'function') {
                lastFocusedTrigger.focus();
            }
            lastFocusedTrigger = null;
        }

        if (cancelBtn) {
            cancelBtn.addEventListener('click', function () {
                closeConfirm();
            });
        }

        if (closeBtn) {
            closeBtn.addEventListener('click', function () {
                closeConfirm();
            });
        }

        if (confirmBtn) {
            confirmBtn.addEventListener('click', function () {
                var action = pendingFormSubmission;
                closeConfirm();
                if (action && typeof action === 'function') {
                    action();
                }
            });
        }

        // Đóng khi click vào backdrop (ngoài dialog card)
        dialog.addEventListener('click', function (e) {
            if (e.target === dialog) {
                closeConfirm();
            }
        });

        // Bắt sự kiện submit của form[data-confirm] ở capture phase
        document.addEventListener('submit', function (e) {
            var form = e.target;
            if (!form || !form.matches || !form.matches('form[data-confirm]')) {
                return;
            }
            if (form.getAttribute('data-nihon-confirmed') === 'true') {
                return; // Đã xác nhận xong, cho phép submit
            }

            e.preventDefault();
            e.stopImmediatePropagation();

            var message = form.getAttribute('data-confirm') || 'Bạn có chắc chắn muốn thực hiện thao tác này?';
            var trigger = document.activeElement || form.querySelector('button[type="submit"]');

            openConfirm(message, trigger, function () {
                form.setAttribute('data-nihon-confirmed', 'true');
                if (typeof form.requestSubmit === 'function') {
                    form.requestSubmit();
                } else {
                    form.submit();
                }
            });
        }, true);

        // Bắt sự kiện click vào nút submit của form[data-confirm]
        document.addEventListener('click', function (e) {
            var btn = e.target.closest('button[type="submit"], input[type="submit"]');
            if (!btn) return;
            var form = btn.closest('form[data-confirm]');
            if (!form) return;
            if (form.getAttribute('data-nihon-confirmed') === 'true') {
                return;
            }

            e.preventDefault();
            e.stopImmediatePropagation();

            var message = form.getAttribute('data-confirm') || 'Bạn có chắc chắn muốn thực hiện thao tác này?';
            openConfirm(message, btn, function () {
                form.setAttribute('data-nihon-confirmed', 'true');
                if (typeof form.requestSubmit === 'function') {
                    form.requestSubmit(btn);
                } else {
                    form.submit();
                }
            });
        }, true);

        // Expose helper cho keyboard nav
        window.__nihonCloseConfirm = closeConfirm;
        window.__nihonIsConfirmOpen = function () {
            return dialog.hasAttribute('open');
        };
    }

    /* ==========================================================================
       4. Global Keyboard Navigation (F04)
       ========================================================================== */
    function initGlobalKeyboardNav() {
        document.addEventListener('keydown', function (e) {
            // 1. Phím Escape: Đóng hộp thoại xác nhận, dropdown menu, ngăn kéo
            if (e.key === 'Escape' || e.key === 'Esc') {
                if (window.__nihonIsConfirmOpen && window.__nihonIsConfirmOpen()) {
                    e.preventDefault();
                    window.__nihonCloseConfirm();
                    return;
                }
                if (window.__nihonIsDropdownOpen && window.__nihonIsDropdownOpen()) {
                    e.preventDefault();
                    window.__nihonCloseDropdown();
                    if (window.__nihonFocusDropdownToggle) {
                        window.__nihonFocusDropdownToggle();
                    }
                    return;
                }
                if (window.__nihonIsDrawerOpen && window.__nihonIsDrawerOpen()) {
                    e.preventDefault();
                    window.__nihonCloseDrawer();
                    return;
                }
                return;
            }

            // 2. Phím Enter: Kích hoạt nút đang được chọn nếu có role="button" và không phải native button/link
            if (e.key === 'Enter') {
                var active = document.activeElement;
                if (active && active.getAttribute('role') === 'button' && active.tagName !== 'BUTTON' && active.tagName !== 'A') {
                    e.preventDefault();
                    active.click();
                    return;
                }
            }

            // Kiểm tra người dùng có đang nhập dữ liệu trong form control không
            var currentTarget = document.activeElement;
            var tag = currentTarget && currentTarget.tagName ? currentTarget.tagName.toUpperCase() : '';
            var isTyping = tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || (currentTarget && currentTarget.isContentEditable);
            if (isTyping) {
                return; // Tuyệt đối không can thiệp phím mũi tên khi đang gõ chữ
            }

            // 3. Mũi tên trái/phải điều hướng ở những trang có chuyển câu hoặc chuyển thẻ
            // (Không can thiệp nếu đang trên trang flashcards vì flashcards.js đã xử lý trực tiếp)
            if (document.querySelector('.flashcard-container')) {
                return;
            }

            if (e.key === 'ArrowRight') {
                var nextBtn = document.getElementById('btnNext')
                    || document.getElementById('btn-next-question')
                    || document.querySelector('.btn-next')
                    || document.querySelector('.btn-next-question');
                if (nextBtn) {
                    e.preventDefault();
                    nextBtn.click();
                }
            } else if (e.key === 'ArrowLeft') {
                var prevBtn = document.getElementById('btnPrev')
                    || document.querySelector('.btn-prev');
                if (prevBtn) {
                    e.preventDefault();
                    prevBtn.click();
                }
            }
        });
    }

})();

