// Practice question page: Enter in the reading field jumps to the meaning field
// when the meaning is still empty, instead of submitting the form.
document.addEventListener('DOMContentLoaded', function () {
    var readingInput = document.getElementById('userReading');
    var meaningInput = document.getElementById('userMeaning');

    if (readingInput && meaningInput) {
        readingInput.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') {
                if (readingInput.value.trim() !== '' && meaningInput.value.trim() === '') {
                    e.preventDefault();
                    meaningInput.focus();
                }
            }
        });
    }
});
