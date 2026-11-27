package com.nihon.service;

import com.nihon.dto.topic.TopicHubDto;
import com.nihon.dto.verb.VerbDetailDto;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.GrammarTopic;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Verb;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.ExamPaperRepository;
import com.nihon.repository.GrammarTopicRepository;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VerbRepository;
import com.nihon.repository.VocabularyRepository;
import com.nihon.conjugation.ConjugatedForms;
import com.nihon.conjugation.VerbConjugator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

/**
 * Service tổng hợp dữ liệu học tập All-in-One cho một chủ đề bài học (F12).
 * Tối ưu hóa truy vấn bằng JOIN FETCH, đảm bảo số truy vấn cố định <= 6 (không vượt quá 8 query).
 */
@Service
public class TopicHubService {

    private final TopicRepository topicRepository;
    private final VocabularyRepository vocabularyRepository;
    private final KanjiEntryRepository kanjiEntryRepository;
    private final GrammarTopicRepository grammarTopicRepository;
    private final VerbRepository verbRepository;
    private final ExamPaperRepository examPaperRepository;

    public TopicHubService(TopicRepository topicRepository,
                           VocabularyRepository vocabularyRepository,
                           KanjiEntryRepository kanjiEntryRepository,
                           GrammarTopicRepository grammarTopicRepository,
                           VerbRepository verbRepository,
                           ExamPaperRepository examPaperRepository) {
        this.topicRepository = topicRepository;
        this.vocabularyRepository = vocabularyRepository;
        this.kanjiEntryRepository = kanjiEntryRepository;
        this.grammarTopicRepository = grammarTopicRepository;
        this.verbRepository = verbRepository;
        this.examPaperRepository = examPaperRepository;
    }

    /**
     * Nạp toàn bộ học liệu của một chủ đề: Từ vựng, Kanji, Ngữ pháp kèm ví dụ,
     * Động từ 5 thể, Đề thi và liên kết flashcard / quiz.
     *
     * @param topicId ID của chủ đề
     * @return DTO chứa đầy đủ thông tin học liệu
     * @throws ResponseStatusException (404 NOT_FOUND) nếu topicId không hợp lệ hoặc không tồn tại
     */
    @Transactional(readOnly = true)
    public TopicHubDto getTopicHubData(Long topicId) {
        // 1. Thẩm định tính hợp lệ của topicId
        if (topicId == null || topicId <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "topicId không hợp lệ: " + topicId);
        }

        // 2. Nạp Topic kèm Parent bằng JOIN FETCH (Query 1)
        Topic topic = topicRepository.findByIdWithParent(topicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chủ đề với ID: " + topicId));

        // 3. Nạp Từ vựng của chủ đề bằng JOIN FETCH (Query 2)
        List<Vocabulary> vocabularyList = vocabularyRepository.findByTopicIdWithTopic(topicId);

        // 4. Nạp Kanji của chủ đề bằng JOIN FETCH (Query 3)
        List<KanjiEntry> kanjiList = kanjiEntryRepository.findByTopicIdWithTopic(topicId);

        // 5. Nạp Ngữ pháp kèm các câu ví dụ bằng LEFT JOIN FETCH examples (Query 4)
        List<GrammarTopic> grammarList = grammarTopicRepository.findByTopicIdWithExamples(topicId);

        // 6. Nạp Động từ bằng JOIN FETCH và chia thể hoàn toàn trong bộ nhớ (Query 5)
        List<Verb> verbs = verbRepository.findByTopicIdWithTopic(topicId);
        List<VerbDetailDto> verbList = verbs.stream()
                .map(v -> {
                    ConjugatedForms forms = VerbConjugator.conjugate(v.getDictionaryForm(), v.getVerbGroup(), v.getKanjiText());
                    return VerbDetailDto.fromEntity(v, forms);
                })
                .toList();

        // 7. Nạp Đề thi đã xuất bản gắn với chủ đề bằng JOIN FETCH (Query 6)
        List<ExamPaper> examList = examPaperRepository.findPublishedByTopicId(topicId);

        return TopicHubDto.builder()
                .topic(topic)
                .vocabularyList(vocabularyList)
                .kanjiList(kanjiList)
                .grammarList(grammarList)
                .verbList(verbList)
                .examList(examList)
                .flashcardUrl("/flashcards?topicId=" + topicId)
                .quizChoiceUrl("/practice/choice?topicId=" + topicId)
                .quizTypingJaViUrl("/practice?topicId=" + topicId)
                .quizTypingViJaUrl("/practice/vi-ja?topicId=" + topicId)
                .quizTimedUrl("/practice/timed?topicId=" + topicId)
                .quizVerbUrl("/verbs/practice?topicId=" + topicId)
                .topicTestUrl("/topics/" + topicId + "/test")
                .grammarArenaUrl("/grammar/arena?topicId=" + topicId)
                .build();
    }
}

