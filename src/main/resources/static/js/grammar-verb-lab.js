/**
 * grammar-verb-lab.js - Tra nhanh thể động từ (F34)
 * Gọi endpoint GET /verbs/lab với Accept: application/json
 * Chèn an toàn vào DOM bằng textContent (KHÔNG innerHTML), tuân thủ CSP nghiêm ngặt.
 */
document.addEventListener('DOMContentLoaded', function () {
    const toggleBtn = document.getElementById('verbLabToggleBtn');
    const contentBox = document.getElementById('verbLabContent');
    const labForm = document.getElementById('grammarVerbLabForm');
    const inputField = document.getElementById('grammarVerbLabInput');
    const groupSelect = document.getElementById('grammarVerbLabGroup');
    const resultBox = document.getElementById('grammarVerbLabResult');
    const errorBox = document.getElementById('grammarVerbLabError');

    if (toggleBtn && contentBox) {
        toggleBtn.addEventListener('click', function () {
            const isHidden = contentBox.hasAttribute('hidden');
            if (isHidden) {
                contentBox.removeAttribute('hidden');
                toggleBtn.setAttribute('aria-expanded', 'true');
                if (inputField) inputField.focus();
            } else {
                contentBox.setAttribute('hidden', '');
                toggleBtn.setAttribute('aria-expanded', 'false');
            }
        });
    }

    if (!labForm || !inputField || !resultBox) {
        return;
    }

    labForm.addEventListener('submit', function (e) {
        e.preventDefault();
        const rawInput = inputField.value.trim();
        if (!rawInput) {
            return;
        }

        const groupVal = groupSelect ? groupSelect.value : 'AUTO';

        // Xóa kết quả cũ an toàn
        resultBox.textContent = '';
        if (errorBox) {
            errorBox.textContent = '';
            errorBox.setAttribute('hidden', '');
        }

        const url = '/verbs/lab?input=' + encodeURIComponent(rawInput) + '&group=' + encodeURIComponent(groupVal);

        fetch(url, {
            method: 'GET',
            headers: {
                'Accept': 'application/json'
            }
        })
        .then(function (res) {
            if (!res.ok) {
                throw new Error('Lỗi máy chủ (' + res.status + ') khi tra cứu động từ.');
            }
            return res.json();
        })
        .then(function (data) {
            if (!data.hasResult && data.errorMessage) {
                showError(data.errorMessage);
                return;
            }
            renderVerbForms(data);
        })
        .catch(function (err) {
            showError(err.message || 'Không thể kết nối đến máy chủ.');
        });
    });

    function showError(msg) {
        if (!errorBox) return;
        errorBox.textContent = msg;
        errorBox.removeAttribute('hidden');
    }

    function renderVerbForms(data) {
        // Tạo grid hiển thị các thể
        const grid = document.createElement('div');
        grid.className = 'verb-lab-results';

        const forms = [
            { label: 'Thể từ điển (辞書形)', val: data.dictionaryForm, kanji: data.kanjiDictionaryForm },
            { label: 'Thể Masu (ます形)', val: data.masuForm, kanji: data.kanjiMasuForm },
            { label: 'Thể Te (て形)', val: data.teForm, kanji: data.kanjiTeForm },
            { label: 'Thể Ta (た形)', val: data.taForm, kanji: data.kanjiTaForm },
            { label: 'Thể Nai (ない形)', val: data.naiForm, kanji: data.kanjiNaiForm }
        ];

        forms.forEach(function (f) {
            const tile = document.createElement('div');
            tile.className = 'verb-form-tile';

            const title = document.createElement('div');
            title.className = 'verb-form-tile-title';
            title.textContent = f.label;

            const val = document.createElement('div');
            val.className = 'verb-form-tile-value';
            
            // Nếu có dạng Kanji, hiển thị dạng Kanji kèm cách đọc Hiragana
            let text = f.val || '';
            if (f.kanji && f.kanji !== f.val) {
                text = f.kanji + ' (' + f.val + ')';
            }
            val.textContent = text;

            tile.appendChild(title);
            tile.appendChild(val);
            grid.appendChild(tile);
        });

        resultBox.appendChild(grid);

        // Hiển thị ghi chú quy tắc nếu có
        if (data.explanation) {
            const note = document.createElement('div');
            note.className = 'grammar-notes-box mt-2';
            
            const strong = document.createElement('strong');
            strong.textContent = '💡 Quy tắc: ';
            const span = document.createElement('span');
            span.textContent = data.explanation;

            note.appendChild(strong);
            note.appendChild(span);
            resultBox.appendChild(note);
        }
    }
});
