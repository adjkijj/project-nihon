import requests
import re
import sys

BASE_URL = "http://localhost:8080"

print("--- 1. Kiểm tra GET /import không cần tài khoản ---")
session = requests.Session()
resp = session.get(f"{BASE_URL}/import")
print("Status code:", resp.status_code)
assert resp.status_code == 200, f"Expected 200, got {resp.status_code}"
assert "previewForm" in resp.text, "Form previewForm không có trên trang"
assert "Nhập Học Liệu Từ Tệp Excel" in resp.text, "Tiêu đề trang không đúng"
print("OK: GET /import trả 200 và hiện form upload.")

# Extract CSRF token from HTML form or cookie
csrf_match = re.search(r'name="_csrf"\s+value="([^"]+)"', resp.text)
csrf_token = csrf_match.group(1) if csrf_match else None
cookie_csrf = session.cookies.get("CSRF-TOKEN")
print(f"CSRF Form Token: {csrf_token}, Cookie Token: {cookie_csrf}")
assert csrf_token is not None, "Không tìm thấy token CSRF trong form"

print("\n--- 2. Tải file Update.xlsx có dữ liệu Lesson 11 và Lesson 12, bấm Xem trước ---")
with open("Update.xlsx", "rb") as f:
    files = {"file": ("Update.xlsx", f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}
    data = {"_csrf": csrf_token}
    preview_resp = session.post(f"{BASE_URL}/import/preview", files=files, data=data)

print("Preview status code:", preview_resp.status_code)
assert preview_resp.status_code == 200, f"Expected 200, got {preview_resp.status_code}"

# Extract preview report metrics
metrics = re.findall(r'<div class="metric-value[^"]*">(\d+)</div>\s*<div class="metric-label">([^<]+)</div>', preview_resp.text)
metric_dict = {label.strip(): int(val) for val, label in metrics}
print("Preview metrics:", metric_dict)

kanji_count = metric_dict.get("Kanji mới", 0) + metric_dict.get("Kanji trùng", 0)
kanji_word_count = metric_dict.get("Từ Kanji mới", 0) + metric_dict.get("Từ Kanji trùng", 0)
word_count = metric_dict.get("Từ vựng mới", 0) + metric_dict.get("Từ vựng trùng", 0)
error_count = metric_dict.get("Lỗi phát hiện", 0)

print(f"Tổng Kanji trong file: {kanji_count}")
print(f"Tổng từ Kanji cần nhớ: {kanji_word_count}")
print(f"Tổng từ vựng: {word_count}")
print(f"Lỗi phát hiện: {error_count}")

assert kanji_count == 14, f"Kỳ vọng 14 Kanji, nhưng nhận {kanji_count}"
assert error_count == 0, f"Kỳ vọng 0 lỗi, nhưng nhận {error_count}"

preview_token_match = re.search(r'name="previewToken"\s+value="([^"]+)"', preview_resp.text)
assert preview_token_match is not None, "Không tìm thấy previewToken sau khi xem trước"
preview_token = preview_token_match.group(1)
print(f"PreviewToken nhận được: {preview_token}")

# Cập nhật CSRF token mới nếu có
csrf_match2 = re.search(r'name="_csrf"\s+value="([^"]+)"', preview_resp.text)
if csrf_match2:
    csrf_token = csrf_match2.group(1)

print("\n--- 3. Bấm Xác nhận nhập (Commit) ---")
commit_data = {
    "_csrf": csrf_token,
    "previewToken": preview_token,
    "mode": "SKIP_DUPLICATES"
}
commit_resp = session.post(f"{BASE_URL}/import/commit", data=commit_data)
print("Commit status code:", commit_resp.status_code)
assert commit_resp.status_code == 200, f"Expected 200, got {commit_resp.status_code}"
assert "Nhập dữ liệu thành công!" in commit_resp.text, "Không thấy thông báo thành công"
print("OK: Commit dữ liệu thành công vào database.")

# Cập nhật CSRF token
csrf_match3 = re.search(r'name="_csrf"\s+value="([^"]+)"', commit_resp.text)
if csrf_match3:
    csrf_token = csrf_match3.group(1)

print("\n--- 4. Nhập lại cùng file Update.xlsx để xác nhận không tạo bản ghi trùng ---")
with open("Update.xlsx", "rb") as f:
    files = {"file": ("Update.xlsx", f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}
    data = {"_csrf": csrf_token}
    preview2_resp = session.post(f"{BASE_URL}/import/preview", files=files, data=data)

preview_token_match2 = re.search(r'name="previewToken"\s+value="([^"]+)"', preview2_resp.text)
assert preview_token_match2 is not None
preview_token2 = preview_token_match2.group(1)

csrf_match4 = re.search(r'name="_csrf"\s+value="([^"]+)"', preview2_resp.text)
if csrf_match4:
    csrf_token = csrf_match4.group(1)

commit2_data = {
    "_csrf": csrf_token,
    "previewToken": preview_token2,
    "mode": "SKIP_DUPLICATES"
}
commit2_resp = session.post(f"{BASE_URL}/import/commit", data=commit2_data)
assert commit2_resp.status_code == 200
commit_metrics = re.findall(r'<div class="metric-value">(\d+)</div>\s*<div class="metric-label">([^<]+)</div>', commit2_resp.text)
commit_dict = {label.strip(): int(val) for val, label in commit_metrics}
print("Re-import commit metrics:", commit_dict)
words_added = commit_dict.get("Từ vựng thêm mới", 0)
kanji_added = commit_dict.get("Kanji thêm mới", 0)
print(f"Từ vựng thêm mới lần 2: {words_added}, Kanji thêm mới lần 2: {kanji_added}")
assert words_added == 0 and kanji_added == 0, "Nhập lại không được tạo bản ghi mới!"
print("OK: Nhập lại cùng file xác nhận 0 bản ghi trùng được tạo.")

print("\n--- 5. Kiểm tra từ chối khi thiếu CSRF Token ---")
no_csrf_resp = requests.post(f"{BASE_URL}/import/commit", data={"previewToken": "test"})
print("Status code thiếu CSRF:", no_csrf_resp.status_code)
assert no_csrf_resp.status_code == 403, f"Expected 403, got {no_csrf_resp.status_code}"
print("OK: POST thiếu CSRF bị từ chối 403.")

print("\n=== TOÀN BỘ CÁC BƯỚC KIỂM TRA ĐỀU THÀNH CÔNG RỰC RỠ! ===")
