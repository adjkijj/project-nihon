// Practice result pages: pressing Enter clicks the "next question" button.
document.addEventListener('keydown', function (e) {
    if (e.key === 'Enter') {
        var btn = document.getElementById('btnNext');
        if (btn) {
            btn.click();
        }
    }
});
