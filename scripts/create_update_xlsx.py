import openpyxl
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
import shutil

wb = openpyxl.Workbook()

# Sheet 1: New Word
ws_words = wb.active
ws_words.title = "New Word"

# Sheet 2: Kanji
ws_kanji = wb.create_sheet(title="Kanji")

# Header styles
header_font = Font(name="Calibri", size=11, bold=True, color="FFFFFF")
header_fill = PatternFill(start_color="1F4E79", end_color="1F4E79", fill_type="solid")
sub_header_fill = PatternFill(start_color="2F5597", end_color="2F5597", fill_type="solid")
border_thin = Border(
    left=Side(style='thin', color='D9D9D9'),
    right=Side(style='thin', color='D9D9D9'),
    top=Side(style='thin', color='D9D9D9'),
    bottom=Side(style='thin', color='D9D9D9')
)

# 1. Setup New Word sheet
word_headers = ["Topic", "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"]
ws_words.append(word_headers)
for col_idx in range(1, 5):
    cell = ws_words.cell(row=1, column=col_idx)
    cell.font = header_font
    cell.fill = header_fill
    cell.alignment = Alignment(horizontal="center", vertical="center")

words_data = [
    # Lesson 11
    ("Lesson 11: Sở thích và Thời gian rảnh", "ダンス", "dansu", "Nhảy múa"),
    (None, "絵（え）", "e", "Tranh vẽ"),
    (None, "漫画（まんが）", "manga", "Truyện tranh"),
    (None, "アニメ", "anime", "Hoạt hình Nhật Bản"),
    (None, "スポーツ", "supootsu", "Thể thao"),
    # Lesson 12
    ("Lesson 12: Sự kiện và Lễ hội", "イベント", "ibento", "Sự kiện"),
    (None, "試合（しあい）", "shiai", "Trận đấu"),
    (None, "チケット", "chiketto", "Vé xem chương trình"),
    (None, "カレンダー", "karendaa", "Lịch treo tường"),
    (None, "ポスター", "posutaa", "Áp phích quảng cáo")
]

for row in words_data:
    ws_words.append(list(row))

# 2. Setup Kanji sheet
# Row 1 headers
kanji_headers_r1 = ["Topic", "Kanji", "Hán Việt", "Ý nghĩa", "On", "Kun", "Word", None, None]
ws_kanji.append(kanji_headers_r1)
# Row 2 headers
kanji_headers_r2 = [None, None, None, None, None, None, "Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt"]
ws_kanji.append(kanji_headers_r2)

for col_idx in range(1, 10):
    c1 = ws_kanji.cell(row=1, column=col_idx)
    c1.font = header_font
    c1.fill = header_fill
    c1.alignment = Alignment(horizontal="center", vertical="center")
    
    c2 = ws_kanji.cell(row=2, column=col_idx)
    if col_idx >= 7:
        c2.font = header_font
        c2.fill = sub_header_fill
        c2.alignment = Alignment(horizontal="center", vertical="center")

# 14 Kanji with vocabulary words
kanji_rows = [
    # Lesson 11 (7 Kanji)
    # 1. 歌
    ("Lesson 11: Sở thích và Thời gian rảnh", "歌", "CA", "Bài hát", "カ", "うた", "歌（うた）", "uta", "Bài hát"),
    (None, None, None, None, None, None, "歌手（かしゅ）", "kashu", "Ca sĩ"),
    # 2. 画
    (None, "画", "HỌA", "Tranh vẽ, họa", "ガ, カク", "えが・く", "映画（えいが）", "eiga", "Phim ảnh"),
    (None, None, None, None, None, None, "絵画（かいが）", "kaiga", "Bức tranh hội họa"),
    # 3. 音
    (None, "音", "ÂM", "Âm thanh", "オン", "おと", "音楽（おんがく）", "ongaku", "Âm nhạc"),
    (None, None, None, None, None, None, "音（おと）", "oto", "Âm thanh, tiếng động"),
    # 4. 楽
    (None, "楽", "LẠC", "Vui vẻ, dễ chịu", "ガク, ラク", "たの・しい", "楽しい（たのしい）", "tanoshii", "Vui vẻ"),
    # 5. 読
    (None, "読", "ĐỘC", "Đọc", "ドク", "よ・む", "読む（よむ）", "yomu", "Đọc sách"),
    (None, None, None, None, None, None, "読書（どくしょ）", "dokusho", "Việc đọc sách"),
    # 6. 書
    (None, "書", "THƯ", "Viết, sách", "ショ", "か・く", "書く（かく）", "kaku", "Viết chữ"),
    (None, None, None, None, None, None, "図書館（としょかん）", "toshokan", "Thư viện"),
    # 7. 休
    (None, "休", "HƯU", "Nghỉ ngơi", "キュウ", "やす・む", "休み（やすみ）", "yasumi", "Ngày nghỉ"),
    (None, None, None, None, None, None, "休日（きゅうじつ）", "kyuujitsu", "Ngày nghỉ lễ"),

    # Lesson 12 (7 Kanji)
    # 8. 会
    ("Lesson 12: Sự kiện và Lễ hội", "会", "HỘI", "Gặp gỡ, đại hội", "カイ", "あ・う", "会う（あう）", "au", "Gặp gỡ bạn bè"),
    (None, None, None, None, None, None, "花火大会（はなびたいかい）", "hanabi taikai", "Đại hội pháo hoa"),
    # 9. 同
    (None, "同", "ĐỒNG", "Cùng nhau, giống nhau", "ドウ", "おな・じ", "同じ（おなじ）", "onaji", "Giống nhau"),
    (None, None, None, None, None, None, "一緒（いっしょ）", "issho", "Cùng nhau"),
    # 10. 事
    (None, "事", "SỰ", "Sự việc, công việc", "ジ", "こと", "仕事（しごと）", "shigoto", "Công việc"),
    (None, None, None, None, None, None, "行事（ぎょうじ）", "gyouji", "Sự kiện lễ hội"),
    # 11. 社
    (None, "社", "XÃ", "Công ty, đền thờ", "シャ", "やしろ", "会社（かいしゃ）", "kaisha", "Công ty"),
    (None, None, None, None, None, None, "神社（じんじゃ）", "jinja", "Đền thần đạo"),
    # 12. 員
    (None, "員", "VIÊN", "Thành viên", "イン", "-", "社員（しゃいん）", "shain", "Nhân viên công ty"),
    (None, None, None, None, None, None, "会員（かいいん）", "kaiin", "Hội viên"),
    # 13. 行
    (None, "行", "HÀNH", "Đi, tổ chức", "コウ, ギョウ", "い・く", "行く（いく）", "iku", "Đi đến nơi nào đó"),
    (None, None, None, None, None, None, "旅行（りょこう）", "ryokou", "Chuyến du lịch"),
    # 14. 来
    (None, "来", "LAI", "Đến, tới", "ライ", "く・る", "来る（くる）", "kuru", "Đến đây"),
    (None, None, None, None, None, None, "来月（らいげつ）", "raigetsu", "Tháng sau")
]

for row in kanji_rows:
    ws_kanji.append(list(row))

wb.save("Update.xlsx")
wb.save("src/main/resources/static/template/Update.xlsx")
print("Saved Update.xlsx successfully with Lesson 11 and Lesson 12, 14 Kanji, and vocabulary words.")
