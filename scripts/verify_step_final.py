import subprocess
import time
import re
import sys
import requests
from bs4 import BeautifulSoup

EXCEL_PATH = r"Update.xlsx"
MYSQL_EXE = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
BASE_URL = "http://localhost:8080"

def run_mysql(query):
    cmd = [MYSQL_EXE, "-u", "root", "-p123456", "--default-character-set=utf8mb4", "nihon_db", "-e", query]
    res = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8")
    return res.stdout.strip()

def main():
    print("=== BẮT ĐẦU KIỂM TRA 5 BƯỚC CUỐI CÙNG ===")
    
    # 1. Kiểm tra kết nối tới Spring Boot
    print("[1/5] Kiểm tra kết nối tới Spring Boot trên cổng 8080...")
    session = requests.Session()
    try:
        r = session.get(f"{BASE_URL}/import", timeout=5)
        assert r.status_code == 200, f"Status: {r.status_code}"
        print("Spring Boot đang chạy và sẵn sàng tại port 8080!")
    except Exception as e:
        print(f"LỖI: Không kết nối được tới server tại {BASE_URL}: {e}")
        sys.exit(1)

    # 2. Mở GET /import và lấy CSRF token
    print("\n[2/5] Kiểm tra GET /import và lấy CSRF token...")
    r_get = session.get(f"{BASE_URL}/import")
    assert r_get.status_code == 200, f"GET /import trả về {r_get.status_code}"
    soup_get = BeautifulSoup(r_get.text, "html.parser")
    csrf_input = soup_get.find("input", {"name": "_csrf"})
    assert csrf_input is not None, "Không tìm thấy CSRF token trên form /import"
    csrf_token = csrf_input.get("value")
    print(f"CSRF token: {csrf_token}")

    # 3. Tải lại Update.xlsx trên trang /import (POST /import/preview)
    print("\n[3/5] Tải lại Update.xlsx để Xem trước (Preview)...")
    with open(EXCEL_PATH, "rb") as f:
        files = {"file": ("Update.xlsx", f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}
        data = {"_csrf": csrf_token}
        r_preview = session.post(f"{BASE_URL}/import/preview", files=files, data=data)

    assert r_preview.status_code == 200, f"Preview trả về status {r_preview.status_code}"
    soup_preview = BeautifulSoup(r_preview.text, "html.parser")

    # Tìm preview report metrics
    metric_boxes = soup_preview.find_all("div", class_="metric-box")
    metrics = {}
    for box in metric_boxes:
        val_el = box.find("div", class_="metric-value")
        lbl_el = box.find("div", class_="metric-label")
        if val_el and lbl_el:
            metrics[lbl_el.text.strip()] = int(val_el.text.strip())

    print("Kết quả Xem trước:", metrics)
    errors_count = metrics.get("Lỗi phát hiện", -1)
    new_kanji = metrics.get("Kanji mới", 0)
    dup_kanji = metrics.get("Kanji trùng", 0)
    new_words = metrics.get("Từ Kanji mới", 0)

    print(f"- Lỗi phát hiện: {errors_count} (Yêu cầu: 0)")
    print(f"- Kanji mới: {new_kanji}, Kanji trùng: {dup_kanji} (Tổng: {new_kanji + dup_kanji}, có 14 Kanji trong file)")
    print(f"- Từ Kanji mới: {new_words} (Khớp 34 hàng có cột G)")

    assert errors_count == 0, f"Preview có lỗi: {errors_count}"
    assert new_kanji + dup_kanji >= 14, "Tổng Kanji không đủ 14"
    assert new_words == 34, f"Số từ Kanji mới {new_words} không khớp 34 hàng cột G"

    # Lấy previewToken và CSRF token mới từ preview form commit
    commit_form = soup_preview.find("form", id="commitForm")
    assert commit_form is not None, "Không tìm thấy form commit sau preview"
    preview_token = commit_form.find("input", {"name": "previewToken"}).get("value")
    csrf_commit = commit_form.find("input", {"name": "_csrf"}).get("value")
    print(f"previewToken: {preview_token}")

    # 4. Bấm Xác nhận nhập (Commit với SKIP_DUPLICATES)
    print("\n[4/5] Xác nhận nhập dữ liệu (Commit)...")
    r_commit = session.post(
        f"{BASE_URL}/import/commit",
        data={
            "_csrf": csrf_commit,
            "previewToken": preview_token,
            "mode": "SKIP_DUPLICATES"
        }
    )
    assert r_commit.status_code == 200, f"Commit trả về status {r_commit.status_code}"
    soup_commit = BeautifulSoup(r_commit.text, "html.parser")
    commit_boxes = soup_commit.find_all("div", class_="metric-box")
    commit_metrics = {}
    for box in commit_boxes:
        val_el = box.find("div", class_="metric-value")
        lbl_el = box.find("div", class_="metric-label")
        if val_el and lbl_el:
            commit_metrics[lbl_el.text.strip()] = int(val_el.text.strip())
    print("Kết quả sau Commit:", commit_metrics)

    # 5. Kiểm tra trực tiếp Database MySQL
    print("\n[5/5] Kiểm tra trực tiếp Database MySQL:")
    # a. Kanji 茶 và 料
    cha_sql = run_mysql("SELECT id, kanji_character, han_viet, onyomi, kunyomi FROM kanji_entry WHERE kanji_character = '茶';")
    print("Kanji '茶':\n", cha_sql)
    ryo_sql = run_mysql("SELECT id, kanji_character, han_viet, onyomi, kunyomi FROM kanji_entry WHERE kanji_character = '料';")
    print("Kanji '料':\n", ryo_sql)

    # Trích xuất id của 茶 và 料
    cha_id_match = re.search(r"(\d+)\s+茶", cha_sql)
    ryo_id_match = re.search(r"(\d+)\s+料", ryo_sql)
    assert cha_id_match is not None, "Không tìm thấy id của Kanji 茶"
    assert ryo_id_match is not None, "Không tìm thấy id của Kanji 料"
    cha_id = cha_id_match.group(1)
    ryo_id = ryo_id_match.group(1)

    # b. Kiểm tra các từ con gắn đúng id cha
    cha_words_sql = run_mysql(f"SELECT id, kanji_id, japanese, romaji, meaning_vi FROM kanji_word WHERE kanji_id = {cha_id};")
    print(f"Từ con của Kanji '茶' (kanji_id={cha_id}):\n", cha_words_sql)
    ryo_words_sql = run_mysql(f"SELECT id, kanji_id, japanese, romaji, meaning_vi FROM kanji_word WHERE kanji_id = {ryo_id};")
    print(f"Từ con của Kanji '料' (kanji_id={ryo_id}):\n", ryo_words_sql)

    assert "NULL" in cha_sql, "kun_reading của 茶 không phải là NULL"
    assert "NULL" in ryo_sql, "kun_reading của 料 không phải là NULL"
    assert "お茶" in cha_words_sql and "喫茶店" in cha_words_sql, "Từ con của 茶 không đầy đủ"
    assert "資料" in ryo_words_sql and "料金" in ryo_words_sql, "Từ con của 料 không đầy đủ"

    # c. Kiểm tra Kanji 野 không có từ con
    no_sql = run_mysql("SELECT id, kanji_character, han_viet, kunyomi FROM kanji_entry WHERE kanji_character = '野';")
    print("Kanji '野':\n", no_sql)
    no_id_match = re.search(r"(\d+)\s+野", no_sql)
    if no_id_match:
        no_id = no_id_match.group(1)
        no_words_sql = run_mysql(f"SELECT count(*) FROM kanji_word WHERE kanji_id = {no_id};")
        print(f"Số từ con của Kanji '野': {no_words_sql.split()[-1]}")
        assert no_words_sql.split()[-1] == "0", "Kanji 野 có từ con (không đúng)"

    # 6. Kiểm tra các trang web UI: /kanji, /vocabulary, /practice, /stats
    print("\nKiểm tra UI và an toàn NULL:")
    for path in ["/kanji", f"/kanji/{cha_id}", f"/kanji/{ryo_id}", "/vocabulary", "/practice", "/stats"]:
        resp = session.get(f"{BASE_URL}{path}")
        assert resp.status_code == 200, f"Truy cập {path} thất bại: {resp.status_code}"
        assert "NullPointerException" not in resp.text, f"NPE tại {path}"
        assert "Đã xảy ra sự cố" not in resp.text, f"Lỗi chung tại {path}"
        assert ">null<" not in resp.text, f"Hiển thị chữ null trần tại {path}"
        print(f"  ✓ {path}: HTTP 200 OK, hiển thị an toàn không có lỗi NULL.")

    print("\n=== TOÀN BỘ 5 BƯỚC KIỂM TRA THÀNH CÔNG RỰC RỠ! ===")

if __name__ == "__main__":
    main()
