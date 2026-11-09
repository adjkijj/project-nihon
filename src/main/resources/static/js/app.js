/*
 * Shared behaviour for every page.
 * Kept in an external file so the Content-Security-Policy can stay strict
 * (script-src 'self', no 'unsafe-inline').
 */
(function () {
    'use strict';

    // <form data-confirm="..."> asks for confirmation before submitting.
    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (form && form.matches && form.matches('form[data-confirm]')) {
            if (!window.confirm(form.getAttribute('data-confirm'))) {
                event.preventDefault();
            }
        }
    });

    // <div data-width="42.5"> -> width: 42.5% (set via CSSOM, which CSP allows).
    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('[data-width]').forEach(function (el) {
            var value = parseFloat(el.getAttribute('data-width'));
            if (!isNaN(value)) {
                el.style.width = value + '%';
            }
        });

        var selectAllCheckbox = document.getElementById('select-all-vocab');
        var rowCheckboxes = Array.prototype.slice.call(document.querySelectorAll('.vocab-row-checkbox'));
        var bulkIdInputs = Array.prototype.slice.call(document.querySelectorAll('.bulk-ids-input'));

        function syncBulkSelection() {
            var selectedIds = [];
            rowCheckboxes.forEach(function (cb) {
                if (cb.checked) {
                    selectedIds.push(cb.value);
                }
            });
            var joined = selectedIds.join(',');
            bulkIdInputs.forEach(function (input) {
                input.value = joined;
            });
            if (selectAllCheckbox && rowCheckboxes.length > 0) {
                selectAllCheckbox.checked = (selectedIds.length === rowCheckboxes.length);
            }
        }

        if (selectAllCheckbox) {
            selectAllCheckbox.addEventListener('change', function () {
                rowCheckboxes.forEach(function (cb) {
                    cb.checked = selectAllCheckbox.checked;
                });
                syncBulkSelection();
            });
        }

        rowCheckboxes.forEach(function (cb) {
            cb.addEventListener('change', syncBulkSelection);
        });

        function getCheckedStats() {
            var checked = rowCheckboxes.filter(function (cb) { return cb.checked; });
            var totalAffected = 0;
            checked.forEach(function (cb) {
                var c = parseInt(cb.getAttribute('data-affected-count') || '1', 10);
                totalAffected += isNaN(c) ? 1 : c;
            });
            return {
                count: checked.length,
                totalAffected: totalAffected
            };
        }

        var bulkDeleteForm = document.getElementById('bulk-delete-form');
        if (bulkDeleteForm) {
            bulkDeleteForm.addEventListener('submit', function (event) {
                syncBulkSelection();
                var stats = getCheckedStats();
                if (stats.count === 0) {
                    window.alert('Vui lòng chọn ít nhất một từ vựng để xóa.');
                    event.preventDefault();
                    return;
                }
                if (stats.count > 200) {
                    window.alert('Chỉ được phép thao tác tối đa 200 từ vựng mỗi lần.');
                    event.preventDefault();
                    return;
                }
                var msg = 'Xóa ' + stats.count + ' từ vựng đã chọn (' + stats.totalAffected + ' bản ghi bị ảnh hưởng)?';
                if (!window.confirm(msg)) {
                    event.preventDefault();
                }
            });
        }

        var bulkMoveForm = document.getElementById('bulk-move-form');
        if (bulkMoveForm) {
            bulkMoveForm.addEventListener('submit', function (event) {
                syncBulkSelection();
                var stats = getCheckedStats();
                if (stats.count === 0) {
                    window.alert('Vui lòng chọn ít nhất một từ vựng để chuyển.');
                    event.preventDefault();
                    return;
                }
                if (stats.count > 200) {
                    window.alert('Chỉ được phép thao tác tối đa 200 từ vựng mỗi lần.');
                    event.preventDefault();
                    return;
                }
                var targetSelect = document.getElementById('bulk-target-topic');
                if (targetSelect && !targetSelect.value) {
                    window.alert('Vui lòng chọn chủ đề đích.');
                    event.preventDefault();
                    return;
                }
                var msg = 'Chuyển ' + stats.count + ' từ vựng đã chọn (' + stats.totalAffected + ' bản ghi bị ảnh hưởng) sang chủ đề đích?';
                if (!window.confirm(msg)) {
                    event.preventDefault();
                }
            });
        }
    });
})();
