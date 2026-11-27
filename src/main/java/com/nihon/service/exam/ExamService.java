package com.nihon.service.exam;

import com.nihon.dto.exam.ExamPaperForm;
import com.nihon.dto.exam.ExamQuestionForm;
import com.nihon.dto.exam.ExamSectionForm;
import com.nihon.entity.ExamAnswerKey;
import com.nihon.entity.ExamAnswerKind;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamChoice;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import com.nihon.entity.ExamSection;
import com.nihon.entity.Topic;
import com.nihon.repository.ExamAnswerKeyRepository;
import com.nihon.repository.ExamChoiceRepository;
import com.nihon.repository.ExamPaperRepository;
import com.nihon.repository.ExamQuestionRepository;
import com.nihon.repository.ExamSectionRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.validation.TextNormalizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Service quản lý toàn bộ vòng đời của Đề thi, Phần thi và Câu hỏi thi (F41).
 */
@Service
@Transactional
public class ExamService {

    private final ExamPaperRepository paperRepository;
    private final ExamSectionRepository sectionRepository;
    private final ExamQuestionRepository questionRepository;
    private final ExamChoiceRepository choiceRepository;
    private final ExamAnswerKeyRepository answerKeyRepository;
    private final TopicRepository topicRepository;
    private final com.nihon.repository.ExamAttemptRepository examAttemptRepository;

    public ExamService(ExamPaperRepository paperRepository,
                       ExamSectionRepository sectionRepository,
                       ExamQuestionRepository questionRepository,
                       ExamChoiceRepository choiceRepository,
                       ExamAnswerKeyRepository answerKeyRepository,
                       TopicRepository topicRepository,
                       com.nihon.repository.ExamAttemptRepository examAttemptRepository) {
        this.paperRepository = paperRepository;
        this.sectionRepository = sectionRepository;
        this.questionRepository = questionRepository;
        this.choiceRepository = choiceRepository;
        this.answerKeyRepository = answerKeyRepository;
        this.topicRepository = topicRepository;
        this.examAttemptRepository = examAttemptRepository;
    }

    // =========================================================================
    // 1. Quản lý Đề thi (ExamPaper)
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<ExamPaper> searchExams(ExamCategory category, Boolean published, String keyword, Pageable pageable) {
        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return paperRepository.searchAdmin(category, published, cleanKeyword, pageable);
    }

    @Transactional(readOnly = true)
    public ExamPaper getExamById(Long id) {
        return paperRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đề thi với ID: " + id));
    }

    @Transactional(readOnly = true)
    public ExamPaper getExamWithDetails(Long id) {
        ExamPaper paper = paperRepository.findByIdWithSections(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đề thi với ID: " + id));
        for (ExamSection s : paper.getSections()) {
            for (ExamQuestion q : s.getQuestions()) {
                q.getChoices().size();
                q.getAnswerKeys().size();
            }
        }
        return paper;
    }

    public ExamPaper createExam(ExamPaperForm form) {
        String cleanCode = TextNormalizer.normalize(form.getCode().trim().toUpperCase());
        if (paperRepository.existsByCode(cleanCode)) {
            throw new IllegalArgumentException("Mã đề thi '" + cleanCode + "' đã tồn tại trong hệ thống.");
        }

        ExamCategory category = ExamCategory.fromString(form.getCategory());
        Topic topic = null;
        if (form.getTopicId() != null && form.getTopicId() > 0) {
            topic = topicRepository.findById(form.getTopicId()).orElse(null);
        }

        ExamPaper paper = ExamPaper.builder()
                .code(cleanCode)
                .title(TextNormalizer.normalize(form.getTitle()))
                .description(TextNormalizer.normalize(form.getDescription()))
                .durationMinutes(form.getDurationMinutes())
                .category(category)
                .topic(topic)
                .published(form.isPublished())
                .shuffleChoices(form.isShuffleChoices())
                .build();

        return paperRepository.save(paper);
    }

    public ExamPaper updateExam(Long id, ExamPaperForm form) {
        ExamPaper paper = getExamById(id);
        String cleanCode = TextNormalizer.normalize(form.getCode().trim().toUpperCase());

        if (paperRepository.existsByCodeAndIdNot(cleanCode, id)) {
            throw new IllegalArgumentException("Mã đề thi '" + cleanCode + "' đã tồn tại trên một đề thi khác.");
        }

        ExamCategory category = ExamCategory.fromString(form.getCategory());
        Topic topic = null;
        if (form.getTopicId() != null && form.getTopicId() > 0) {
            topic = topicRepository.findById(form.getTopicId()).orElse(null);
        }

        paper.setCode(cleanCode);
        paper.setTitle(TextNormalizer.normalize(form.getTitle()));
        paper.setDescription(TextNormalizer.normalize(form.getDescription()));
        paper.setDurationMinutes(form.getDurationMinutes());
        paper.setCategory(category);
        paper.setTopic(topic);
        paper.setPublished(form.isPublished());
        paper.setShuffleChoices(form.isShuffleChoices());

        return paperRepository.save(paper);
    }

    public ExamPaper togglePublish(Long id) {
        ExamPaper paper = getExamById(id);
        paper.setPublished(!paper.isPublished());
        return paperRepository.save(paper);
    }

    public void deleteExam(Long id) {
        ExamPaper paper = getExamById(id);

        if (examAttemptRepository.existsByPaperId(id)) {
            throw new IllegalStateException("Đề thi đã có bài làm của học viên, không thể xóa. Vui lòng chọn Hủy xuất bản.");
        }

        paperRepository.delete(paper);
    }

    // =========================================================================
    // 2. Quản lý Phần thi (ExamSection)
    // =========================================================================

    public ExamSection addSection(Long paperId, ExamSectionForm form) {
        ExamPaper paper = getExamById(paperId);

        long currentSectionCount = sectionRepository.countByPaperId(paperId);
        if (currentSectionCount >= ExamValidator.MAX_SECTIONS_PER_EXAM) {
            throw new IllegalArgumentException(String.format(
                    "Đề thi đã đạt giới hạn tối đa %d phần thi.",
                    ExamValidator.MAX_SECTIONS_PER_EXAM
            ));
        }

        int nextSortOrder = (int) currentSectionCount + 1;
        ExamSection section = ExamSection.builder()
                .paper(paper)
                .title(TextNormalizer.normalize(form.getTitle()))
                .instructions(TextNormalizer.normalize(form.getInstructions()))
                .sortOrder(nextSortOrder)
                .build();

        return sectionRepository.save(section);
    }

    public ExamSection updateSection(Long sectionId, ExamSectionForm form) {
        ExamSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy phần thi với ID: " + sectionId));

        section.setTitle(TextNormalizer.normalize(form.getTitle()));
        section.setInstructions(TextNormalizer.normalize(form.getInstructions()));
        return sectionRepository.save(section);
    }

    public void deleteSection(Long sectionId) {
        ExamSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy phần thi với ID: " + sectionId));
        Long paperId = section.getPaper().getId();
        sectionRepository.delete(section);
        reindexSections(paperId);
    }

    public void moveSection(Long sectionId, boolean moveUp) {
        ExamSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy phần thi với ID: " + sectionId));
        Long paperId = section.getPaper().getId();
        List<ExamSection> sections = sectionRepository.findByPaperIdOrderBySortOrderAsc(paperId);

        int currentIndex = -1;
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).getId().equals(sectionId)) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex < 0) return;

        int targetIndex = moveUp ? currentIndex - 1 : currentIndex + 1;
        if (targetIndex >= 0 && targetIndex < sections.size()) {
            ExamSection other = sections.get(targetIndex);
            int tempOrder = section.getSortOrder();
            section.setSortOrder(other.getSortOrder());
            other.setSortOrder(tempOrder);
            sectionRepository.save(section);
            sectionRepository.save(other);
            reindexSections(paperId);
        }
    }

    private void reindexSections(Long paperId) {
        List<ExamSection> sections = sectionRepository.findByPaperIdOrderBySortOrderAsc(paperId);
        for (int i = 0; i < sections.size(); i++) {
            sections.get(i).setSortOrder(i + 1);
        }
        sectionRepository.saveAll(sections);
    }

    // =========================================================================
    // 3. Quản lý Câu hỏi (ExamQuestion)
    // =========================================================================

    @Transactional(readOnly = true)
    public ExamQuestion getQuestionById(Long id) {
        ExamQuestion q = questionRepository.findByIdWithChoices(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy câu hỏi với ID: " + id));
        q.getAnswerKeys().size();
        return q;
    }

    public ExamQuestion addQuestion(Long sectionId, ExamQuestionForm form) {
        ExamSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy phần thi với ID: " + sectionId));

        ExamPaper paper = section.getPaper();
        long totalQuestionsInPaper = questionRepository.countBySectionPaperId(paper.getId());
        if (totalQuestionsInPaper >= ExamValidator.MAX_QUESTIONS_PER_EXAM) {
            throw new IllegalArgumentException(String.format(
                    "Đề thi đã đạt giới hạn tối đa %d câu hỏi.",
                    ExamValidator.MAX_QUESTIONS_PER_EXAM
            ));
        }

        ExamQuestionType type = ExamQuestionType.fromString(form.getType());
        List<String> rawChoices = extractChoicesFromForm(form);
        List<ExamValidator.AnswerKeyItem> answerKeyItems = extractAnswerKeysFromForm(type, form, rawChoices);

        // Thẩm định tính toàn vẹn nghiệp vụ của câu hỏi
        ExamValidator.validateQuestion(
                type,
                form.getPromptJa(),
                form.getPromptVi(),
                rawChoices,
                form.getCorrectChoiceIndex(),
                answerKeyItems
        );

        List<ExamQuestion> existing = questionRepository.findBySectionIdOrderBySortOrderAsc(sectionId);
        int nextOrder = existing.size() + 1;

        ExamQuestion question = ExamQuestion.builder()
                .section(section)
                .sortOrder(nextOrder)
                .type(type)
                .promptJa(TextNormalizer.normalize(form.getPromptJa()))
                .promptVi(TextNormalizer.normalize(form.getPromptVi()))
                .points(form.getPoints() != null ? form.getPoints() : BigDecimal.ONE)
                .explanation(TextNormalizer.normalize(form.getExplanation()))
                .build();

        question = questionRepository.save(question);
        saveChoicesAndKeys(question, type, rawChoices, form.getCorrectChoiceIndex(), answerKeyItems);

        return question;
    }

    public ExamQuestion updateQuestion(Long questionId, ExamQuestionForm form) {
        ExamQuestion question = getQuestionById(questionId);
        ExamQuestionType type = ExamQuestionType.fromString(form.getType());
        List<String> rawChoices = extractChoicesFromForm(form);
        List<ExamValidator.AnswerKeyItem> answerKeyItems = extractAnswerKeysFromForm(type, form, rawChoices);

        // Thẩm định nghiệp vụ
        ExamValidator.validateQuestion(
                type,
                form.getPromptJa(),
                form.getPromptVi(),
                rawChoices,
                form.getCorrectChoiceIndex(),
                answerKeyItems
        );

        question.setType(type);
        question.setPromptJa(TextNormalizer.normalize(form.getPromptJa()));
        question.setPromptVi(TextNormalizer.normalize(form.getPromptVi()));
        question.setPoints(form.getPoints() != null ? form.getPoints() : BigDecimal.ONE);
        question.setExplanation(TextNormalizer.normalize(form.getExplanation()));

        questionRepository.save(question);

        // Xóa lựa chọn và answer keys cũ để lưu mới
        choiceRepository.deleteByQuestionId(questionId);
        answerKeyRepository.deleteByQuestionId(questionId);

        saveChoicesAndKeys(question, type, rawChoices, form.getCorrectChoiceIndex(), answerKeyItems);

        return question;
    }

    public void deleteQuestion(Long questionId) {
        ExamQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy câu hỏi với ID: " + questionId));
        Long sectionId = question.getSection().getId();
        questionRepository.delete(question);
        reindexQuestions(sectionId);
    }

    public void moveQuestion(Long questionId, boolean moveUp) {
        ExamQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy câu hỏi với ID: " + questionId));
        Long sectionId = question.getSection().getId();
        List<ExamQuestion> questions = questionRepository.findBySectionIdOrderBySortOrderAsc(sectionId);

        int currentIndex = -1;
        for (int i = 0; i < questions.size(); i++) {
            if (questions.get(i).getId().equals(questionId)) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex < 0) return;

        int targetIndex = moveUp ? currentIndex - 1 : currentIndex + 1;
        if (targetIndex >= 0 && targetIndex < questions.size()) {
            ExamQuestion other = questions.get(targetIndex);
            int tempOrder = question.getSortOrder();
            question.setSortOrder(other.getSortOrder());
            other.setSortOrder(tempOrder);
            questionRepository.save(question);
            questionRepository.save(other);
            reindexQuestions(sectionId);
        }
    }

    private void reindexQuestions(Long sectionId) {
        List<ExamQuestion> questions = questionRepository.findBySectionIdOrderBySortOrderAsc(sectionId);
        for (int i = 0; i < questions.size(); i++) {
            questions.get(i).setSortOrder(i + 1);
        }
        questionRepository.saveAll(questions);
    }

    private List<String> extractChoicesFromForm(ExamQuestionForm form) {
        List<String> list = new ArrayList<>();
        addIfNotBlank(list, form.getChoice1());
        addIfNotBlank(list, form.getChoice2());
        addIfNotBlank(list, form.getChoice3());
        addIfNotBlank(list, form.getChoice4());
        addIfNotBlank(list, form.getChoice5());
        addIfNotBlank(list, form.getChoice6());
        return list;
    }

    private void addIfNotBlank(List<String> list, String val) {
        if (val != null && !val.isBlank()) {
            list.add(val.trim());
        }
    }

    private List<ExamValidator.AnswerKeyItem> extractAnswerKeysFromForm(ExamQuestionType type,
                                                                       ExamQuestionForm form,
                                                                       List<String> choices) {
        List<ExamValidator.AnswerKeyItem> list = new ArrayList<>();
        switch (type) {
            case TYPING -> {
                if (form.getTypingAcceptedAnswers() != null) {
                    String[] parts = form.getTypingAcceptedAnswers().split("[|\\n\\r]+");
                    for (String p : parts) {
                        if (!p.isBlank()) {
                            list.add(new ExamValidator.AnswerKeyItem(ExamAnswerKind.ACCEPTED, p.trim()));
                        }
                    }
                }
            }
            case ORDERING -> {
                if (form.getOrderingOrder() != null && !form.getOrderingOrder().isBlank()) {
                    list.add(new ExamValidator.AnswerKeyItem(ExamAnswerKind.ORDER, "ORDER:" + form.getOrderingOrder().trim()));
                }
                if (form.getOrderingStar() != null && form.getOrderingStar() > 0) {
                    list.add(new ExamValidator.AnswerKeyItem(ExamAnswerKind.ORDER, "STAR:" + form.getOrderingStar()));
                }
            }
            case TRANSLATION -> {
                if (form.getTranslationAccepted() != null) {
                    String[] parts = form.getTranslationAccepted().split("[|\\n\\r]+");
                    for (String p : parts) {
                        if (!p.isBlank()) {
                            list.add(new ExamValidator.AnswerKeyItem(ExamAnswerKind.ACCEPTED, p.trim()));
                        }
                    }
                }
                if (form.getTranslationSample() != null && !form.getTranslationSample().isBlank()) {
                    list.add(new ExamValidator.AnswerKeyItem(ExamAnswerKind.SAMPLE, form.getTranslationSample().trim()));
                }
            }
            case ESSAY -> {
                if (form.getEssaySample() != null && !form.getEssaySample().isBlank()) {
                    list.add(new ExamValidator.AnswerKeyItem(ExamAnswerKind.SAMPLE, form.getEssaySample().trim()));
                }
                if (form.getEssayKeywords() != null) {
                    String[] parts = form.getEssayKeywords().split("[,;|\\n\\r]+");
                    for (String p : parts) {
                        if (!p.isBlank()) {
                            list.add(new ExamValidator.AnswerKeyItem(ExamAnswerKind.KEYWORD, p.trim()));
                        }
                    }
                }
            }
            case MULTIPLE_CHOICE -> {}
        }
        return list;
    }

    private void saveChoicesAndKeys(ExamQuestion question,
                                   ExamQuestionType type,
                                   List<String> choices,
                                   Integer correctChoiceIndex,
                                   List<ExamValidator.AnswerKeyItem> answerKeys) {
        if (type == ExamQuestionType.MULTIPLE_CHOICE || type == ExamQuestionType.ORDERING) {
            for (int i = 0; i < choices.size(); i++) {
                boolean isCorrect = (type == ExamQuestionType.MULTIPLE_CHOICE) && (correctChoiceIndex != null && correctChoiceIndex == (i + 1));
                ExamChoice choice = ExamChoice.builder()
                        .question(question)
                        .sortOrder(i + 1)
                        .text(TextNormalizer.normalize(choices.get(i)))
                        .correct(isCorrect)
                        .build();
                choiceRepository.save(choice);
            }
        }

        for (ExamValidator.AnswerKeyItem keyItem : answerKeys) {
            ExamAnswerKey key = ExamAnswerKey.builder()
                    .question(question)
                    .kind(keyItem.kind())
                    .answerText(TextNormalizer.normalize(keyItem.text()))
                    .build();
            answerKeyRepository.save(key);
        }
    }
}

