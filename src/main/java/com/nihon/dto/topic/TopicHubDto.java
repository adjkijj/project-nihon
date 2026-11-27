package com.nihon.dto.topic;

import com.nihon.dto.verb.VerbDetailDto;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.GrammarTopic;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * DTO gom toàn bộ dữ liệu học tập liên quan đến một chủ đề (F12 - All-in-One Topic Hub):
 * Từ vựng, Kanji, Ngữ pháp (kèm ví dụ), Động từ 5 thể, Đề thi và các liên kết luyện tập / Flashcard / Quiz.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicHubDto {

    private Topic topic;

    @Builder.Default
    private List<Vocabulary> vocabularyList = Collections.emptyList();

    @Builder.Default
    private List<KanjiEntry> kanjiList = Collections.emptyList();

    @Builder.Default
    private List<GrammarTopic> grammarList = Collections.emptyList();

    @Builder.Default
    private List<VerbDetailDto> verbList = Collections.emptyList();

    @Builder.Default
    private List<ExamPaper> examList = Collections.emptyList();

    // Liên kết học tập và kiểm tra (Flashcard, Quiz, Mini Test, Đấu trường)
    private String flashcardUrl;
    private String quizChoiceUrl;
    private String quizTypingJaViUrl;
    private String quizTypingViJaUrl;
    private String quizTimedUrl;
    private String quizVerbUrl;
    private String topicTestUrl;
    private String grammarArenaUrl;

    public int getVocabularyCount() {
        return vocabularyList != null ? vocabularyList.size() : 0;
    }

    public int getKanjiCount() {
        return kanjiList != null ? kanjiList.size() : 0;
    }

    public int getGrammarCount() {
        return grammarList != null ? grammarList.size() : 0;
    }

    public int getVerbCount() {
        return verbList != null ? verbList.size() : 0;
    }

    public int getExamCount() {
        return examList != null ? examList.size() : 0;
    }
}

