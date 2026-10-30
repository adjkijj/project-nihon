import requests
import re
import sys
import io

# Force stdout to UTF-8
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

BASE_URL = "http://localhost:8080"
EXCEL_PATH = "Update.xlsx"

session = requests.Session()

print("1. Fetch GET /import and get CSRF token")
r_get = session.get(f"{BASE_URL}/import")
assert r_get.status_code == 200, f"GET /import status: {r_get.status_code}"
csrf_m = re.search(r'name="_csrf"\s+value="([^"]+)"', r_get.text)
assert csrf_m, "CSRF token not found"
csrf_token = csrf_m.group(1)
print(f"  ✓ CSRF token: {csrf_token}")

print("2. Upload Update.xlsx to /import/preview")
with open(EXCEL_PATH, "rb") as f:
    files = {"file": ("Update.xlsx", f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}
    data = {"_csrf": csrf_token}
    r_prev = session.post(f"{BASE_URL}/import/preview", files=files, data=data)

print("Preview status:", r_prev.status_code, "url:", r_prev.url)
assert "Bước 2: Kết Quả Xem Trước" in r_prev.text, "Preview title not found"

# Parse metrics
metric_boxes = re.findall(r'<div class="metric-value[^"]*"[^>]*>\s*(\d+)\s*</div>\s*<div class="metric-label"[^>]*>\s*([^<]+)\s*</div>', r_prev.text)
metrics = {label.strip(): int(val.strip()) for val, label in metric_boxes}
print("  Preview Metrics:", metrics)

errors = metrics.get("Lỗi phát hiện", -1)
kanji_new = metrics.get("Kanji mới", -1)
kanji_dup = metrics.get("Kanji trùng", -1)
kanji_words_new = metrics.get("Từ Kanji mới", -1)
kanji_words_dup = metrics.get("Từ Kanji trùng", -1)

assert errors == 0, f"Expected 0 errors, got {errors}"
assert kanji_new == 0, f"Expected 0 new kanji, got {kanji_new}"
assert kanji_dup == 14, f"Expected 14 dup kanji, got {kanji_dup}"
assert kanji_words_new == 0, f"Expected 0 new kanji words, got {kanji_words_new}"
assert kanji_words_dup == 34, f"Expected 34 dup kanji words, got {kanji_words_dup}"
print("  ✓ Preview metrics verified: 0 errors, 14 duplicate kanji, 0 new kanji words, 34 duplicate kanji words!")

print("3. Submit /import/commit with SKIP_DUPLICATES")
csrf_commit_m = re.search(r'name="_csrf"\s+value="([^"]+)"', r_prev.text)
token_commit_m = re.search(r'name="previewToken"\s+value="([^"]+)"', r_prev.text)
assert csrf_commit_m and token_commit_m

commit_data = {
    "_csrf": csrf_commit_m.group(1),
    "previewToken": token_commit_m.group(1),
    "mode": "SKIP_DUPLICATES"
}

r_commit = session.post(f"{BASE_URL}/import/commit", data=commit_data)
assert r_commit.status_code == 200, f"Commit status: {r_commit.status_code}"
assert "Kết Quả Nhập Dữ Liệu Vào Hệ Thống" in r_commit.text, "Commit result page not found"

commit_boxes = re.findall(r'<div class="metric-value[^"]*"[^>]*>\s*(\d+)\s*</div>\s*<div class="metric-label"[^>]*>\s*([^<]+)\s*</div>', r_commit.text)
commit_metrics = {label.strip(): int(val.strip()) for val, label in commit_boxes}
print("  Commit Metrics:", commit_metrics)

kanji_added = commit_metrics.get("Kanji thêm mới", -1)
kanji_updated = commit_metrics.get("Kanji cập nhật", -1)
words_added = commit_metrics.get("Từ vựng thêm mới", -1)
words_updated = commit_metrics.get("Từ vựng cập nhật", -1)
kanji_words_added = commit_metrics.get("Từ Kanji thêm mới", -1)
kanji_words_updated = commit_metrics.get("Từ Kanji cập nhật", -1)

assert kanji_added == 0, f"Expected 0 kanji added, got {kanji_added}"
assert words_added == 0, f"Expected 0 words added, got {words_added}"
assert kanji_words_added == 0, f"Expected 0 kanji words added, got {kanji_words_added}"
print("  ✓ Commit verified: 0 kanji added, 0 words added, 0 kanji words added. NO DUPLICATES CREATED!")
