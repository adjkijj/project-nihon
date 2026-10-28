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
    });
})();
