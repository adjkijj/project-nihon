/**
 * Project Nihon - Exam Administration JavaScript (exams-admin.js)
 * F41: Dynamic Question Type Switching, Print Handlers, Accessibility
 * Strictly CSP-compliant: Zero inline scripts, zero eval, zero inline events.
 */

(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        initQuestionTypeSwitcher();
        initPrintButtons();
        initDragAndDropDropzone();
    });

    /**
     * Tự động hiển thị trường form tương ứng theo loại câu hỏi (MULTIPLE_CHOICE, TYPING, ORDERING, TRANSLATION, ESSAY).
     */
    function initQuestionTypeSwitcher() {
        var typeSelect = document.getElementById('questionTypeSelect');
        if (!typeSelect) return;

        function updateSections() {
            var selectedType = typeSelect.value;
            var sections = document.querySelectorAll('.type-section');
            sections.forEach(function (sec) {
                sec.classList.remove('active');
            });

            var targetId = 'section_' + selectedType;
            var targetSection = document.getElementById(targetId);
            if (targetSection) {
                targetSection.classList.add('active');
            }

            // Gợi ý điểm số mặc định nếu người dùng chưa sửa
            var pointsInput = document.getElementById('pointsInput');
            if (pointsInput && (!pointsInput.value || pointsInput.value === '1.00' || pointsInput.value === '2.00')) {
                if (selectedType === 'ORDERING') {
                    pointsInput.value = '2.00';
                } else {
                    pointsInput.value = '1.00';
                }
            }
        }

        typeSelect.addEventListener('change', updateSections);
        updateSections();
    }

    /**
     * Nút in bài thi (window.print)
     */
    function initPrintButtons() {
        var printBtns = document.querySelectorAll('[data-action="print"]');
        printBtns.forEach(function (btn) {
            btn.addEventListener('click', function () {
                window.print();
            });
        });
    }

    /**
     * Kéo thả tệp tin Excel tại màn hình Import
     */
    function initDragAndDropDropzone() {
        var dropzone = document.getElementById('examDropzone');
        var fileInput = document.getElementById('excelFileInput');
        if (!dropzone || !fileInput) return;

        ['dragenter', 'dragover'].forEach(function (eventName) {
            dropzone.addEventListener(eventName, function (e) {
                e.preventDefault();
                e.stopPropagation();
                dropzone.classList.add('dragover');
            });
        });

        ['dragleave', 'drop'].forEach(function (eventName) {
            dropzone.addEventListener(eventName, function (e) {
                e.preventDefault();
                e.stopPropagation();
                dropzone.classList.remove('dragover');
            });
        });

        dropzone.addEventListener('drop', function (e) {
            var dt = e.dataTransfer;
            if (dt && dt.files && dt.files.length > 0) {
                fileInput.files = dt.files;
            }
        });
    }
})();

