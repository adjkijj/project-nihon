package com.nihon.service;

import com.nihon.dto.ImportIssue;
import com.nihon.dto.ParsedVocabularyFile;
import com.nihon.dto.ParsedVocabularyFile.ParsedKanji;
import com.nihon.dto.ParsedVocabularyFile.ParsedTopic;
import com.nihon.dto.ParsedVocabularyFile.ParsedVocabulary;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phân tích file Markdown từ vựng theo đúng định dạng đã kiểm tra:
 * <ul>
 *   <li>"## " = chủ đề chính, "### " = chủ đề con của chủ đề chính gần nhất.</li>
 *   <li>Bảng từ vựng: | Tiếng Nhật | Romaji | Nghĩa tiếng Việt |</li>
 *   <li>Bảng Kanji:   | Kanji | Hán Việt | Ý nghĩa | On | Kun | (chỉ trong mục Kanji)</li>
 * </ul>
 * Không ghi database; không sửa nội dung ô (chỉ bỏ khoảng trắng đệm hai đầu ô của Markdown).
 */
@Component
public class VocabularyFileParser {

    static final List<String> VOCAB_HEADER = List.of("Tiếng Nhật", "Romaji", "Nghĩa tiếng Việt");
    static final List<String> KANJI_HEADER = List.of("Kanji", "Hán Việt", "Ý nghĩa", "On", "Kun");

    // Giới hạn khớp với độ dài cột trong entity.
    static final int MAX_TOPIC = 255;
    static final int MAX_JAPANESE = 255;
    static final int MAX_READING = 255;
    static final int MAX_ROMAJI = 255;
    static final int MAX_MEANING_VOCAB = 500;
    static final int MAX_KANJI_CHAR = 8;
    static final int MAX_HAN_VIET = 100;
    static final int MAX_MEANING_KANJI = 255;
    static final int MAX_ON_KUN = 100;

    private static final Pattern SEPARATOR_CELL = Pattern.compile("^:?-{3,}:?$");
    private static final Pattern READING_GROUP = Pattern.compile("[（(]([^）)]*)[）)]");
    private static final Pattern KANJI_TOPIC = Pattern.compile("^\\d+\\.\\s*Kanji$");

    private enum TableMode { VOCAB, KANJI }

    public ParsedVocabularyFile parse(Reader reader) throws IOException {
        BufferedReader br = reader instanceof BufferedReader b ? b : new BufferedReader(reader);
        State s = new State();

        String raw;
        int lineNo = 0;
        while ((raw = br.readLine()) != null) {
            lineNo++;
            String line = raw.strip();
            if (lineNo == 1 && line.startsWith("\uFEFF")) {
                line = line.substring(1).strip();
            }

            if (line.startsWith("### ")) {
                s.mode = null;
                handleSubtopic(s, lineNo, line.substring(4).strip());
            } else if (line.startsWith("## ")) {
                s.mode = null;
                handleTopic(s, lineNo, line.substring(3).strip());
            } else if (line.length() > 1 && line.startsWith("|") && line.endsWith("|")) {
                handleTableLine(s, lineNo, line);
            } else {
                // Dòng trống, đoạn văn, tiêu đề "#": kết thúc bảng hiện tại.
                s.mode = null;
            }
        }

        return new ParsedVocabularyFile(s.topics, s.vocabularies, s.kanji, s.rowsRead,
                s.skippedDuplicateInFile, s.readingNotProvided, s.readingAmbiguous, s.errors);
    }

    private void handleTopic(State s, int lineNo, String name) {
        if (!validTopicName(s, lineNo, name)) {
            s.currentRoot = null;
            s.currentTopic = null;
            return;
        }
        ParsedTopic t = new ParsedTopic(lineNo, name, null, KANJI_TOPIC.matcher(name).matches());
        s.topics.add(t);
        s.currentRoot = t;
        s.currentTopic = t;
    }

    private void handleSubtopic(State s, int lineNo, String name) {
        if (s.currentRoot == null) {
            s.errors.add(new ImportIssue(lineNo, "Chủ đề con không nằm trong chủ đề chính hợp lệ"));
            s.currentTopic = null;
            return;
        }
        if (!validTopicName(s, lineNo, name)) {
            s.currentTopic = null;
            return;
        }
        ParsedTopic t = new ParsedTopic(lineNo, name, s.currentRoot.sourceLine(), s.currentRoot.kanjiSection());
        s.topics.add(t);
        s.currentTopic = t;
    }

    private boolean validTopicName(State s, int lineNo, String name) {
        if (name.isEmpty()) {
            s.errors.add(new ImportIssue(lineNo, "Tên chủ đề trống"));
            return false;
        }
        if (name.length() > MAX_TOPIC) {
            s.errors.add(new ImportIssue(lineNo, "Tên chủ đề vượt quá " + MAX_TOPIC + " ký tự"));
            return false;
        }
        return true;
    }

    private void handleTableLine(State s, int lineNo, String line) {
        List<String> cells = Arrays.stream(line.substring(1, line.length() - 1).split("\\|", -1))
                .map(String::strip)
                .toList();

        if (cells.stream().allMatch(c -> SEPARATOR_CELL.matcher(c).matches())) {
            return;
        }
        if (cells.equals(VOCAB_HEADER)) {
            s.mode = TableMode.VOCAB;
            return;
        }
        if (cells.equals(KANJI_HEADER)) {
            s.mode = TableMode.KANJI;
            return;
        }

        s.rowsRead++;
        if (s.mode == null) {
            s.errors.add(new ImportIssue(lineNo, "Hàng dữ liệu không thuộc bảng có tiêu đề hợp lệ"));
            return;
        }
        if (s.currentTopic == null) {
            s.errors.add(new ImportIssue(lineNo, "Hàng dữ liệu không thuộc chủ đề hợp lệ"));
            return;
        }
        if (s.mode == TableMode.VOCAB) {
            handleVocabRow(s, lineNo, cells);
        } else {
            handleKanjiRow(s, lineNo, cells);
        }
    }

    private void handleVocabRow(State s, int lineNo, List<String> cells) {
        if (cells.size() != VOCAB_HEADER.size()) {
            s.errors.add(new ImportIssue(lineNo, "Số cột không đúng: cần " + VOCAB_HEADER.size() + ", có " + cells.size()));
            return;
        }
        String japanese = cells.get(0);
        String romaji = cells.get(1);
        String meaning = cells.get(2);

        int before = s.errors.size();
        require(s, lineNo, japanese, "Tiếng Nhật", MAX_JAPANESE);
        require(s, lineNo, romaji, "Romaji", MAX_ROMAJI);
        require(s, lineNo, meaning, "Nghĩa tiếng Việt", MAX_MEANING_VOCAB);
        if (s.errors.size() > before) {
            return;
        }

        String key = s.currentTopic.sourceLine() + "\u0000" + japanese + "\u0000" + romaji + "\u0000" + meaning;
        if (!s.vocabKeys.add(key)) {
            s.skippedDuplicateInFile++;
            return;
        }

        boolean hasKanji = containsKanji(japanese);
        String reading = null;
        if (hasKanji) {
            reading = extractReading(japanese);
            if (reading == null) {
                if (READING_GROUP.matcher(japanese).find()) {
                    s.readingAmbiguous++;
                } else {
                    s.readingNotProvided++;
                }
            } else if (reading.length() > MAX_READING) {
                s.errors.add(new ImportIssue(lineNo, "Cách đọc vượt quá " + MAX_READING + " ký tự"));
                return;
            }
        }

        s.vocabularies.add(new ParsedVocabulary(lineNo, s.currentTopic.sourceLine(), japanese, reading,
                romaji, meaning, hasKanji));
    }

    private void handleKanjiRow(State s, int lineNo, List<String> cells) {
        if (cells.size() != KANJI_HEADER.size()) {
            s.errors.add(new ImportIssue(lineNo, "Số cột không đúng: cần " + KANJI_HEADER.size() + ", có " + cells.size()));
            return;
        }
        if (!s.currentTopic.kanjiSection()) {
            s.errors.add(new ImportIssue(lineNo, "Bảng Kanji nằm ngoài khu vực Kanji"));
            return;
        }
        int before = s.errors.size();
        require(s, lineNo, cells.get(0), "Kanji", MAX_KANJI_CHAR);
        require(s, lineNo, cells.get(1), "Hán Việt", MAX_HAN_VIET);
        require(s, lineNo, cells.get(2), "Ý nghĩa", MAX_MEANING_KANJI);
        require(s, lineNo, cells.get(3), "On", MAX_ON_KUN);
        require(s, lineNo, cells.get(4), "Kun", MAX_ON_KUN);
        if (s.errors.size() > before) {
            return;
        }
        if (!s.kanjiChars.add(cells.get(0))) {
            s.skippedDuplicateInFile++;
            return;
        }
        s.kanji.add(new ParsedKanji(lineNo, s.currentTopic.sourceLine(), cells.get(0), cells.get(1),
                cells.get(2), cells.get(3), cells.get(4)));
    }

    private void require(State s, int lineNo, String value, String field, int max) {
        if (value.isEmpty()) {
            s.errors.add(new ImportIssue(lineNo, "Thiếu trường bắt buộc: " + field));
        } else if (value.length() > max) {
            s.errors.add(new ImportIssue(lineNo, "Trường " + field + " vượt quá " + max + " ký tự"));
        }
    }

    static boolean containsKanji(String text) {
        return text.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    /**
     * Chỉ lấy cách đọc khi nguồn ghi đúng MỘT cụm ngoặc nằm ở cuối, ví dụ "私（わたし）".
     * Trường hợp khác ("初(はじ)めまして", "（お）財布（さいふ）", không có ngoặc) trả về null,
     * không tự ghép hay suy đoán.
     */
    static String extractReading(String japanese) {
        Matcher m = READING_GROUP.matcher(japanese);
        if (!m.find()) {
            return null;
        }
        String content = m.group(1).strip();
        boolean atEnd = m.end() == japanese.length();
        if (m.find() || !atEnd || content.isEmpty()) {
            return null;
        }
        return content;
    }

    private static final class State {
        final List<ParsedTopic> topics = new ArrayList<>();
        final List<ParsedVocabulary> vocabularies = new ArrayList<>();
        final List<ParsedKanji> kanji = new ArrayList<>();
        final List<ImportIssue> errors = new ArrayList<>();
        final Set<String> vocabKeys = new HashSet<>();
        final Set<String> kanjiChars = new HashSet<>();
        ParsedTopic currentRoot;
        ParsedTopic currentTopic;
        TableMode mode;
        int rowsRead;
        int skippedDuplicateInFile;
        int readingNotProvided;
        int readingAmbiguous;
    }
}
