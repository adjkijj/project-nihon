import urllib.request
import urllib.parse
import re
import sys
import io

# Force stdout to UTF-8
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

BASE_URL = "http://localhost:8080"

def get(path):
    req = urllib.request.Request(f"{BASE_URL}{path}", headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req) as r:
        return r.status, r.read().decode("utf-8")

def check_vocab_topic(topic_id, expected_count, sample_words):
    status, html = get(f"/vocabulary?topicId={topic_id}")
    assert status == 200, f"Status: {status}"
    
    # Check totalElements
    m = re.search(r'trên tổng số\s*<strong>(\d+)</strong>\s*từ vựng', html)
    assert m, "totalElements not found"
    total = int(m.group(1))
    print(f"Topic {topic_id}: total words = {total} (expected {expected_count})")
    assert total == expected_count, f"Expected {expected_count}, got {total}"
    
    # Check sample words presence
    for w in sample_words:
        assert w in html, f"Word {w} not found in topic {topic_id}"
        print(f"  ✓ Found '{w}'")
    
    # Check columns exist
    for col in ["Tiếng Nhật", "Cách đọc", "Romaji", "Nghĩa tiếng Việt", "Chủ đề", "Thao tác", "Sửa", "Xóa"]:
        assert col in html, f"Column/button '{col}' not found"

def check_kanji_page():
    # 1. Check Lesson 11 on /kanji?topicId=86
    status, html_86 = get("/kanji?topicId=86")
    assert status == 200, f"Status: {status}"
    kanjis_11 = ["酒", "作", "物", "茶", "持", "願"]
    for k in kanjis_11:
        assert k in html_86, f"Kanji {k} not found on /kanji?topicId=86"
        print(f"  ✓ Kanji '{k}' is present on /kanji?topicId=86")
        
    subwords_11 = ["お酒（おさけ）", "作ります（つくります）", "乗り物（のりもの）", "お茶（おちゃ）", "持ちます（もちます）", "お願い（おねがい）"]
    for sw in subwords_11:
        assert sw in html_86, f"Subword '{sw}' not found under Kanji cards on /kanji?topicId=86"
        print(f"  ✓ Subword '{sw}' displayed under Kanji card on Lesson 11")

    # 2. Check Lesson 12 on /kanji?topicId=87
    status, html_87 = get("/kanji?topicId=87")
    assert status == 200, f"Status: {status}"
    kanjis_12 = ["料", "理", "味", "色", "野", "菜", "中", "入"]
    for k in kanjis_12:
        assert k in html_87, f"Kanji {k} not found on /kanji?topicId=87"
        print(f"  ✓ Kanji '{k}' is present on /kanji?topicId=87")

    subwords_12 = ["料理（りょうり）", "味（あじ）", "中（なか）", "入ります（はいります）"]
    for sw in subwords_12:
        assert sw in html_87, f"Subword '{sw}' not found under Kanji cards on /kanji?topicId=87"
        print(f"  ✓ Subword '{sw}' displayed under Kanji card on Lesson 12")

    # 3. Check search by keyword '酒'
    status, html_sake = get("/kanji?keyword=" + urllib.parse.quote("酒"))
    assert status == 200
    assert "お酒（おさけ）" in html_sake, "Word お酒（おさけ） not found in search keyword=酒"
    print("  ✓ Search keyword=酒 returns Kanji 酒 with word 'お酒（おさけ）'")

def check_other_pages():
    for p in ["/practice", "/stats"]:
        status, html = get(p)
        assert status == 200, f"Page {p} returned {status}"
        assert "Đã xảy ra sự cố" not in html, f"Error page on {p}"
        print(f"  ✓ Page {p} returns 200 OK without errors")

if __name__ == "__main__":
    print("--- CHECK 3: /vocabulary for Lesson 11 (topic 86) and Lesson 12 (topic 87) ---")
    check_vocab_topic(86, 15, ["お酒（おさけ）", "作ります（つくります）", "乗り物（のりもの）", "お茶（おちゃ）", "持ちます（もちます）", "お願い（おねがい）"])
    check_vocab_topic(87, 19, ["資料（しりょう）", "料金（りょうきん）", "料理（りょうり）", "味（あじ）", "地味（な）（じみ）", "色（いろ）", "野菜（やさい）", "中（なか）", "入ります（はいります）"])
    
    print("\n--- CHECK 4: /kanji shows words under Kanji cards ---")
    check_kanji_page()
    
    print("\n--- CHECK 6: /practice and /stats ---")
    check_other_pages()
    
    print("\nALL VERIFICATIONS PASSED SUCCESSFULLY!")
