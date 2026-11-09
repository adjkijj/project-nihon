# Bảng Đối Chiếu Độ Dài Cột Database, Entity, Parser và Form (`FIELD_LIMITS`)

Tài liệu này tổng hợp độ dài tối đa của toàn bộ các trường chuỗi trong các Entity JPA, cột cơ sở dữ liệu (MySQL `utf8mb4` / H2), hằng số dùng chung [`KanjiLimits`](../src/main/java/com/nihon/validation/KanjiLimits.java), các bộ phân tích cú pháp (`KanjiSheetParser`, `WordSheetParser`, `VocabularyFileParser`), bước kiểm tra trước khi lưu tại `ImportService` và các Form DTO.

---

## 1. Hằng số dùng chung (`com.nihon.validation.KanjiLimits`)

| Hằng số trong `KanjiLimits` | Giá trị | Ý nghĩa áp dụng |
| :--- | :---: | :--- |
| `MAX_TOPIC_LENGTH` | `255` | Tên chủ đề (`topic.name`) |
| `MAX_KANJI_CHAR_LENGTH` | `8` | Ký tự Hán tự trong DB (`kanji_entry.kanji_character`); khi nhập kiểm tra đúng 1 code point `UnicodeScript.HAN` |
| `MAX_HAN_VIET_LENGTH` | `255` | Âm Hán Việt của Kanji (`kanji_entry.han_viet`) |
| `MAX_KANJI_MEANING_LENGTH` | `255` | Ý nghĩa tiếng Việt của Kanji (`kanji_entry.meaning_vi`) |
| `MAX_ONYOMI_LENGTH` | `255` | Âm đọc Onyomi của Kanji (`kanji_entry.onyomi`) |
| `MAX_KUNYOMI_LENGTH` | `255` | Âm đọc Kunyomi của Kanji (`kanji_entry.kunyomi`) |
| `MAX_ON_KUN_LENGTH` | `255` | Hằng số dùng chung cho cả âm On và âm Kun của Kanji |
| `MAX_JAPANESE_LENGTH` | `255` | Từ tiếng Nhật (`vocabulary.japanese_text`) |
| `MAX_READING_LENGTH` | `255` | Cách đọc Hiragana/Katakana (`vocabulary.reading`) |
| `MAX_ROMAJI_LENGTH` | `255` | Phiên âm Romaji (`vocabulary.romaji`) |
| `MAX_VOCAB_MEANING_LENGTH` | `500` | Nghĩa tiếng Việt của từ vựng / từ cần nhớ (`vocabulary.meaning_vi`) |

---

## 2. Bảng đối chiếu chi tiết giữa Entity, Database, Parser và Form

### 2.1. Bảng `topic` (`com.nihon.entity.Topic`)

| Trường Entity | Cột Database | Kiểu SQL (`schema.sql` / Migration) | `@Column(length)` | `KanjiLimits` | `WordSheetParser` | `KanjiSheetParser` | `VocabularyFileParser` | `ImportService` (Pre-save) | Form DTO (`TopicForm`) |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `name` | `name` | `VARCHAR(255) NOT NULL` | `255` | `MAX_TOPIC_LENGTH` (`255`) | `MAX_TOPIC_LENGTH` (`255`) | `MAX_TOPIC_LENGTH` (`255`) | `MAX_TOPIC` (`255`) | Kiểm tra `<= 255` | `@Size(max = 255)` |

### 2.2. Bảng `vocabulary` (`com.nihon.entity.Vocabulary`)

| Trường Entity | Cột Database | Kiểu SQL (`schema.sql` / `V2`) | `@Column(length)` | `KanjiLimits` | `WordSheetParser` (Sheet `New Word`) | `KanjiSheetParser` (Từ cần nhớ cột G-I) | `VocabularyFileParser` | `ImportService` (Pre-save) | Form DTO (`VocabularyForm` / Practice) |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `japaneseText` | `japanese_text` | `VARCHAR(255) NOT NULL` | `255` | `MAX_JAPANESE_LENGTH` (`255`) | `MAX_JAPANESE_LENGTH` (`255`) | `MAX_WORD_JAPANESE_LENGTH` (`255`) | `MAX_JAPANESE` (`255`) | Kiểm tra `<= 255` | `VocabularyForm`: `@Size(max = 255)`<br>`PracticeViJaAnswerForm`: `@Size(max = 255)` |
| `reading` | `reading` | `VARCHAR(255) NULL` | `255` | `MAX_READING_LENGTH` (`255`) | `MAX_READING_LENGTH` (`255`) | Trích từ cột G (`<= 255`) | `MAX_READING` (`255`) | Kiểm tra `<= 255` | `VocabularyForm`: `@Size(max = 255)`<br>`PracticeAnswerForm`: `@Size(max = 255)` |
| `romaji` | `romaji` | `VARCHAR(255) NULL` | `255` | `MAX_ROMAJI_LENGTH` (`255`) | `MAX_ROMAJI_LENGTH` (`255`) | `MAX_WORD_ROMAJI_LENGTH` (`255`) | `MAX_ROMAJI` (`255`) | Kiểm tra `<= 255` | `VocabularyForm`: `@Size(max = 255)` |
| `meaningVi` | `meaning_vi` | `VARCHAR(500) NULL` | `500` | `MAX_VOCAB_MEANING_LENGTH` (`500`) | `MAX_MEANING_LENGTH` (`500`) | `MAX_WORD_MEANING_LENGTH` (`500`) | `MAX_MEANING_VOCAB` (`500`) | Kiểm tra `<= 500` | `VocabularyForm`: `@Size(max = 500)`<br>`PracticeAnswerForm`: `@Size(max = 500)` |

### 2.3. Bảng `kanji_entry` (`com.nihon.entity.KanjiEntry`)

| Trường Entity | Cột Database | Kiểu SQL (`schema.sql` / `V4`) | `@Column(length)` | `KanjiLimits` | `KanjiSheetParser` (Sheet `Kanji` cột B-F) | `VocabularyFileParser` | `ImportService` (Pre-save) | Form DTO (`KanjiForm`) |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| `character` | `kanji_character` | `VARCHAR(8) NOT NULL` | `8` | `MAX_KANJI_CHAR_LENGTH` (`8`) | `MAX_KANJI_CHAR_LENGTH` (`8`) + kiểm tra đúng 1 code point `HAN` | `MAX_KANJI_CHAR` (`8`) | Kiểm tra `<= 8` | `@SingleKanji` |
| `hanViet` | `han_viet` | `VARCHAR(255) NULL` | `255` | `MAX_HAN_VIET_LENGTH` (`255`) | `MAX_HAN_VIET_LENGTH` (`255`) | `MAX_HAN_VIET` (`255`) | Kiểm tra `<= 255` | `@Size(max = 255)` `@HanVietText` |
| `meaningVi` | `meaning_vi` | `VARCHAR(255) NULL` | `255` | `MAX_KANJI_MEANING_LENGTH` (`255`) | `MAX_MEANING_LENGTH` (`255`) | `MAX_MEANING_KANJI` (`255`) | Kiểm tra `<= 255` | `@Size(max = 255)` `@VietnameseText` |
| `onyomi` | `onyomi` | `VARCHAR(255) NULL` | `255` | `MAX_ONYOMI_LENGTH` (`255`) | `MAX_ON_KUN_LENGTH` (`255`) | `MAX_ON_KUN` (`255`) | Kiểm tra `<= 255` | `@Size(max = 255)` `@OnyomiReading` |
| `kunyomi` | `kunyomi` | `VARCHAR(255) NULL` | `255` | `MAX_KUNYOMI_LENGTH` (`255`) | `MAX_ON_KUN_LENGTH` (`255`) | `MAX_ON_KUN` (`255`) | Kiểm tra `<= 255` | `@Size(max = 255)` `@KanaReading` |

### 2.4. Bảng sao lưu `kanji_word_backup_20261109` (Đã gỡ khỏi Entity từ `V7`)

| Cột Database | Kiểu SQL | Ghi chú |
| :--- | :--- | :--- |
| `japanese` | `VARCHAR(255) NOT NULL` | Đã hợp nhất hoàn toàn sang `vocabulary.japanese_text` và đổi tên bảng sang `kanji_word_backup_20261109` tại `V7` |
| `romaji` | `VARCHAR(255) NULL` | Đã hợp nhất hoàn toàn sang `vocabulary.romaji` |
| `meaning_vi` | `VARCHAR(500) NULL` | Đã hợp nhất hoàn toàn sang `vocabulary.meaning_vi` |

### 2.5. Bảng `import_audit_log` (`com.nihon.entity.ImportAuditLog`)

| Trường Entity | Cột Database | Kiểu SQL (`schema.sql`) | `@Column(length)` | Kiểm tra tại `ImportAuditService` |
| :--- | :--- | :--- | :---: | :--- |
| `userId` | `user_id` | `VARCHAR(100) NOT NULL` | `100` | Cắt an toàn tối đa `100` ký tự trước khi lưu |
| `fileName` | `file_name` | `VARCHAR(255) NOT NULL` | `255` | Cắt an toàn tối đa `255` ký tự trước khi lưu |
| `fileHashSha256` | `file_hash_sha256` | `VARCHAR(64) NOT NULL` | `64` | SHA-256 hex cố định `64` ký tự |
| `result` | `result` | `VARCHAR(50) NOT NULL` | `50` | Mã trạng thái (`SUCCESS`, `FAILED`, `REJECTED`) |
| `errorMessage` | `error_message` | `VARCHAR(1000) NULL` | `1000` | Cắt an toàn tối đa `1000` ký tự (`997 + "..."`) trước khi lưu |

