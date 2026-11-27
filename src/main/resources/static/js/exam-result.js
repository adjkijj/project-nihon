/**
 * Script điều khiển bộ lọc xem kết quả thi (F40 - F45).
 * Tuân thủ CSP nghiêm ngặt: không sử dụng inline event handler hay eval.
 */
document.addEventListener('DOMContentLoaded', function () {
    const filterBtns = document.querySelectorAll('.result-filter-btn');
    const questionCards = document.querySelectorAll('.result-question-item');

    if (!filterBtns.length || !questionCards.length) {
        return;
    }

    filterBtns.forEach(btn => {
        btn.addEventListener('click', function () {
            filterBtns.forEach(b => b.classList.remove('active'));
            this.classList.add('active');

            const filter = this.dataset.filter; // 'all' hoặc 'incorrect'

            questionCards.forEach(card => {
                const status = card.dataset.status; // 'correct', 'incorrect', 'essay'
                if (filter === 'all') {
                    card.classList.remove('d-none');
                } else if (filter === 'incorrect') {
                    if (status === 'incorrect') {
                        card.classList.remove('d-none');
                    } else {
                        card.classList.add('d-none');
                    }
                }
            });
        });
    });
});

