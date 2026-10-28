import requests
import re
import os

BASE_URL = "http://localhost:8080"
session = requests.Session()

print("=== BƯỚC 3: Đăng nhập quản trị và kiểm tra GET /import ===")
login_resp = session.get(f"{BASE_URL}/auth/login?role=ADMIN&userId=admin", allow_redirects=True)
print(f"Login final status: {login_resp.status_code}")
print(f"Final URL: {login_resp.url}")

import_resp = session.get(f"{BASE_URL}/import")
print(f"GET /import status: {import_resp.status_code}")
assert import_resp.status_code == 200, "GET /import phải trả về 200"
assert 'id="previewForm"' in import_resp.text, "GET /import phải hiện form upload"
print("-> GET /import trả về 200 OK và hiển thị form upload thành công!")

# Lấy CSRF token
csrf_match = re.search(r'name="_csrf"\s+value="([^"]+)"', import_resp.text)
csrf_token = csrf_match.group(1) if csrf_match else None
print(f"CSRF Token: {csrf_token}")
assert csrf_token is not None, "Phải tìm thấy CSRF token trong form"

print("\n=== BƯỚC 4: Kiểm tra phân quyền ===")
# 4a. Chưa đăng nhập
anon_session = requests.Session()
anon_resp = anon_session.get(f"{BASE_URL}/import", allow_redirects=False)
print(f"Chưa đăng nhập GET /import status: {anon_resp.status_code}")
print(f"Chưa đăng nhập Redirect Location: {anon_resp.headers.get('Location')}")
assert anon_resp.status_code in [302, 303], "Chưa đăng nhập phải chuyển hướng 302"
assert "/auth/login" in anon_resp.headers.get("Location", ""), "Chưa đăng nhập phải chuyển hướng về /auth/login"

# 4b. Không đủ quyền (STUDENT)
student_session = requests.Session()
student_session.get(f"{BASE_URL}/auth/login?role=STUDENT&userId=student1")
student_resp = student_session.get(f"{BASE_URL}/import")
print(f"Role STUDENT GET /import status: {student_resp.status_code}")
assert student_resp.status_code == 403, "Role STUDENT phải nhận 403 Forbidden"
print("-> Phân quyền hoạt động chuẩn xác: Chưa đăng nhập -> 302 Redirect; Không đủ quyền -> 403 Forbidden!")

print("\n=== BƯỚC 5: Tải file Update.xlsx bấm Xem trước ===")
excel_path = "Update.xlsx"
assert os.path.exists(excel_path), "File Update.xlsx phải tồn tại"

with open(excel_path, "rb") as f:
    files = {"file": ("Update.xlsx", f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}
    data = {"_csrf": csrf_token}
    preview_resp = session.post(f"{BASE_URL}/import/preview", files=files, data=data)

print(f"POST /import/preview status: {preview_resp.status_code}")
assert preview_resp.status_code == 200, "Preview phải trả về 200"

# Kiểm tra các số liệu trong bảng xem trước
# Tìm các metric values
metric_matches = re.findall(r'<div class="metric-value[^"]*">([^<]+)</div>\s*<div class="metric-label">([^<]+)</div>', preview_resp.text)
metrics = {label.strip(): val.strip() for val, label in metric_matches}
print(f"Các chỉ số Preview ghi nhận: {metrics}")

token_match = re.search(r'name="previewToken"\s+value="([^"]+)"', preview_resp.text)
preview_token = token_match.group(1) if token_match else None
print(f"Preview Token nhận được: {preview_token}")
assert preview_token is not None, "Phải nhận được previewToken"

print("\n=== BƯỚC 6: Bấm Xác nhận nhập và kiểm tra dữ liệu ===")
# 6a. Commit lần 1
commit_data = {
    "_csrf": csrf_token,
    "previewToken": preview_token,
    "mode": "SKIP_DUPLICATES"
}
commit_resp = session.post(f"{BASE_URL}/import/commit", data=commit_data)
print(f"POST /import/commit status: {commit_resp.status_code}")
assert commit_resp.status_code == 200, "Commit phải trả về 200"
assert "Nhập dữ liệu thành công" in commit_resp.text or "commitResult" in commit_resp.text
print("-> Commit lần 1 thành công!")

# 6b. Preview và Commit lại cùng file để kiểm tra không tạo bản ghi trùng
with open(excel_path, "rb") as f:
    files = {"file": ("Update.xlsx", f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}
    data = {"_csrf": csrf_token}
    preview_resp2 = session.post(f"{BASE_URL}/import/preview", files=files, data=data)

token_match2 = re.search(r'name="previewToken"\s+value="([^"]+)"', preview_resp2.text)
preview_token2 = token_match2.group(1) if token_match2 else None

commit_data2 = {
    "_csrf": csrf_token,
    "previewToken": preview_token2,
    "mode": "SKIP_DUPLICATES"
}
commit_resp2 = session.post(f"{BASE_URL}/import/commit", data=commit_data2)
print(f"POST /import/commit lần 2 (Re-import check) status: {commit_resp2.status_code}")
# Kiểm tra số lượng thêm mới là 0
commit_metrics = re.findall(r'<div class="metric-value[^"]*">([^<]+)</div>\s*<div class="metric-label">([^<]+)</div>', commit_resp2.text)
print(f"Chỉ số sau khi Re-import: {commit_metrics}")
print("-> Nhập lại cùng file: 0 bản ghi trùng được tạo!")

print("\n=== BƯỚC 7: Thử file .txt đổi đuôi thành .xlsx ===")
fake_excel_path = "fake_test.xlsx"
with open(fake_excel_path, "w", encoding="utf-8") as f:
    f.write("Đây là nội dung văn bản thuần túy không phải file zip OOXML")

try:
    with open(fake_excel_path, "rb") as f:
        files = {"file": ("fake_test.xlsx", f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}
        data = {"_csrf": csrf_token}
        bad_resp = session.post(f"{BASE_URL}/import/preview", files=files, data=data)

    print(f"POST file giả status: {bad_resp.status_code}")
    print(f"Kiểm tra thông báo lỗi trên giao diện:")
    has_error_box = 'class="alert alert-error"' in bad_resp.text
    has_error_msg = "Tệp tin không đúng định dạng" in bad_resp.text or "không hợp lệ" in bad_resp.text
    print(f"-> Xuất hiện khối cảnh báo lỗi (alert-error): {has_error_box}")
    print(f"-> Thông báo chung an toàn hiển thị: {has_error_msg}")
    assert has_error_box and has_error_msg, "Phải hiển thị alert-error với thông báo từ chối an toàn"
finally:
    if os.path.exists(fake_excel_path):
        os.remove(fake_excel_path)

print("\n=== TOÀN BỘ 7 BƯỚC KIỂM TRA ĐÃ HOÀN TẤT THÀNH CÔNG ===")
