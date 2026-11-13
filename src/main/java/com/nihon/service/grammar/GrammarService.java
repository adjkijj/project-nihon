package com.nihon.service.grammar;

import com.nihon.dto.grammar.GrammarExampleForm;
import com.nihon.dto.grammar.GrammarQuizChoiceForm;
import com.nihon.dto.grammar.GrammarQuizQuestionForm;
import com.nihon.dto.grammar.GrammarTopicForm;
import com.nihon.entity.GrammarCategory;
import com.nihon.entity.GrammarExample;
import com.nihon.entity.GrammarQuizChoice;
import com.nihon.entity.GrammarQuizQuestion;
import com.nihon.entity.GrammarTopic;
import com.nihon.entity.Topic;
import com.nihon.repository.GrammarExampleRepository;
import com.nihon.repository.GrammarQuizChoiceRepository;
import com.nihon.repository.GrammarQuizQuestionRepository;
import com.nihon.repository.GrammarTopicRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.validation.TextNormalizer;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Service quản lý kho Ngữ pháp và Kiến thức bổ sung (F30-F32).
 */
@Service
public class GrammarService {

    private static final Logger log = LoggerFactory.getLogger(GrammarService.class);

    private final GrammarTopicRepository topicRepository;
    private final GrammarExampleRepository exampleRepository;
    private final GrammarQuizQuestionRepository questionRepository;
    private final GrammarQuizChoiceRepository choiceRepository;
    private final TopicRepository generalTopicRepository;

    public GrammarService(GrammarTopicRepository topicRepository,
                          GrammarExampleRepository exampleRepository,
                          GrammarQuizQuestionRepository questionRepository,
                          GrammarQuizChoiceRepository choiceRepository,
                          TopicRepository generalTopicRepository) {
        this.topicRepository = topicRepository;
        this.exampleRepository = exampleRepository;
        this.questionRepository = questionRepository;
        this.choiceRepository = choiceRepository;
        this.generalTopicRepository = generalTopicRepository;
    }

    // =========================================================================
    // 1. Quản lý Chủ đề Ngữ pháp (GrammarTopic)
    // =========================================================================

    public Page<GrammarTopic> getGrammarTopics(GrammarCategory category, String keyword, Pageable pageable) {
        return getGrammarTopics(category, null, keyword, pageable);
    }

    public Page<GrammarTopic> getGrammarTopics(GrammarCategory category, Long topicId, String keyword, Pageable pageable) {
        Specification<GrammarTopic> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }

            if (topicId != null && topicId > 0) {
                predicates.add(cb.equal(root.get("topic").get("id"), topicId));
            }

            if (keyword != null && !keyword.isBlank()) {
                String normalizedKw = "%" + keyword.trim().toLowerCase() + "%";
                Predicate codeMatch = cb.like(cb.lower(root.get("code")), normalizedKw);
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), normalizedKw);
                Predicate formulaMatch = cb.like(cb.lower(root.get("formula")), normalizedKw);
                predicates.add(cb.or(codeMatch, titleMatch, formulaMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return topicRepository.findAll(spec, pageable);
    }

    public List<GrammarTopic> getAllTopics() {
        return topicRepository.findAll();
    }

    public GrammarTopic getTopicById(Long id) {
        if (id == null) {
            throw new NoSuchElementException("ID chủ đề ngữ pháp không hợp lệ.");
        }
        return topicRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chủ đề ngữ pháp với ID: " + id));
    }

    public GrammarTopic getTopicWithDetails(Long id) {
        GrammarTopic topic = getTopicById(id);
        // Force initialization of lazy collections
        if (topic.getExamples() != null) {
            topic.getExamples().size();
        }
        if (topic.getQuestions() != null) {
            for (GrammarQuizQuestion q : topic.getQuestions()) {
                if (q.getChoices() != null) {
                    q.getChoices().size();
                }
            }
        }
        return topic;
    }

    public GrammarTopic getTopicByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new NoSuchElementException("Mã chủ đề ngữ pháp không hợp lệ.");
        }
        return topicRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chủ đề ngữ pháp với mã: " + code));
    }

    @Transactional
    public GrammarTopic createTopic(GrammarTopicForm form) {
        String cleanCode = form.getCode().trim().toUpperCase();
        if (topicRepository.existsByCode(cleanCode)) {
            throw new IllegalArgumentException("Mã chủ đề ngữ pháp '" + cleanCode + "' đã tồn tại.");
        }

        Topic generalTopic = null;
        if (form.getTopicId() != null) {
            generalTopic = generalTopicRepository.findById(form.getTopicId()).orElse(null);
        }

        Integer sortOrder = form.getSortOrder();
        if (sortOrder == null || sortOrder <= 0) {
            Integer maxSort = topicRepository.findMaxSortOrder();
            sortOrder = (maxSort != null) ? maxSort + 1 : 1;
        }

        GrammarCategory category = GrammarCategory.fromString(form.getCategory());

        GrammarTopic topic = GrammarTopic.builder()
                .code(cleanCode)
                .title(TextNormalizer.normalize(form.getTitle()))
                .category(category)
                .topic(generalTopic)
                .formula(form.getFormula() != null ? form.getFormula().trim() : null)
                .explanation(form.getExplanation() != null ? form.getExplanation().trim() : null)
                .notes(form.getNotes() != null ? form.getNotes().trim() : null)
                .sortOrder(sortOrder)
                .build();

        return topicRepository.save(topic);
    }

    @Transactional
    public GrammarTopic updateTopic(Long id, GrammarTopicForm form) {
        GrammarTopic topic = getTopicById(id);

        String cleanCode = form.getCode().trim().toUpperCase();
        if (topicRepository.existsByCodeAndIdNot(cleanCode, id)) {
            throw new IllegalArgumentException("Mã chủ đề ngữ pháp '" + cleanCode + "' đã tồn tại trên một chủ đề khác.");
        }

        Topic generalTopic = null;
        if (form.getTopicId() != null) {
            generalTopic = generalTopicRepository.findById(form.getTopicId()).orElse(null);
        }

        topic.setCode(cleanCode);
        topic.setTitle(TextNormalizer.normalize(form.getTitle()));
        topic.setCategory(GrammarCategory.fromString(form.getCategory()));
        topic.setTopic(generalTopic);
        topic.setFormula(form.getFormula() != null ? form.getFormula().trim() : null);
        topic.setExplanation(form.getExplanation() != null ? form.getExplanation().trim() : null);
        topic.setNotes(form.getNotes() != null ? form.getNotes().trim() : null);
        if (form.getSortOrder() != null && form.getSortOrder() > 0) {
            topic.setSortOrder(form.getSortOrder());
        }

        return topicRepository.save(topic);
    }

    @Transactional
    public void deleteTopic(Long id) {
        GrammarTopic topic = getTopicById(id);
        topicRepository.delete(topic);
        topicRepository.flush();
    }

    // =========================================================================
    // 2. Quản lý Ví dụ (GrammarExample)
    // =========================================================================

    public GrammarExample getExampleById(Long exampleId) {
        if (exampleId == null) {
            throw new NoSuchElementException("ID ví dụ không hợp lệ.");
        }
        return exampleRepository.findById(exampleId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy ví dụ với ID: " + exampleId));
    }

    @Transactional
    public GrammarExample addExample(Long topicId, GrammarExampleForm form) {
        GrammarTopic topic = getTopicById(topicId);
        long currentCount = exampleRepository.countByGrammarTopicId(topicId);
        if (currentCount >= GrammarTopic.MAX_EXAMPLES_PER_TOPIC) {
            throw new IllegalStateException("Chủ đề ngữ pháp đã đạt số lượng ví dụ tối đa (" + GrammarTopic.MAX_EXAMPLES_PER_TOPIC + " câu).");
        }

        Integer sortOrder = form.getSortOrder();
        if (sortOrder == null || sortOrder <= 0) {
            Integer maxSort = exampleRepository.findMaxSortOrderByGrammarTopicId(topicId);
            sortOrder = (maxSort != null) ? maxSort + 1 : 1;
        }

        GrammarExample example = GrammarExample.builder()
                .grammarTopic(topic)
                .sortOrder(sortOrder)
                .japanese(TextNormalizer.normalize(form.getJapanese()))
                .reading(TextNormalizer.normalize(form.getReading()))
                .meaningVi(TextNormalizer.normalize(form.getMeaningVi()))
                .build();

        topic.addExample(example);
        return exampleRepository.save(example);
    }

    @Transactional
    public GrammarExample updateExample(Long exampleId, GrammarExampleForm form) {
        GrammarExample example = getExampleById(exampleId);

        example.setJapanese(TextNormalizer.normalize(form.getJapanese()));
        example.setReading(TextNormalizer.normalize(form.getReading()));
        example.setMeaningVi(TextNormalizer.normalize(form.getMeaningVi()));
        if (form.getSortOrder() != null && form.getSortOrder() > 0) {
            example.setSortOrder(form.getSortOrder());
        }

        return exampleRepository.save(example);
    }

    @Transactional
    public void deleteExample(Long exampleId) {
        GrammarExample example = getExampleById(exampleId);
        exampleRepository.delete(example);
    }

    @Transactional
    public void moveExample(Long exampleId, boolean moveUp) {
        GrammarExample example = getExampleById(exampleId);
        Long topicId = example.getGrammarTopic().getId();
        List<GrammarExample> examples = exampleRepository.findByGrammarTopicIdOrderBySortOrderAsc(topicId);

        int index = -1;
        for (int i = 0; i < examples.size(); i++) {
            if (examples.get(i).getId().equals(exampleId)) {
                index = i;
                break;
            }
        }

        if (index == -1) {
            return;
        }

        if (moveUp && index > 0) {
            GrammarExample prev = examples.get(index - 1);
            int temp = example.getSortOrder();
            example.setSortOrder(prev.getSortOrder());
            prev.setSortOrder(temp);
            exampleRepository.save(example);
            exampleRepository.save(prev);
        } else if (!moveUp && index < examples.size() - 1) {
            GrammarExample next = examples.get(index + 1);
            int temp = example.getSortOrder();
            example.setSortOrder(next.getSortOrder());
            next.setSortOrder(temp);
            exampleRepository.save(example);
            exampleRepository.save(next);
        }
    }

    // =========================================================================
    // 3. Quản lý Câu hỏi Trắc nghiệm (GrammarQuizQuestion & Choice)
    // =========================================================================

    public GrammarQuizQuestion getQuestionById(Long questionId) {
        if (questionId == null) {
            throw new NoSuchElementException("ID câu hỏi không hợp lệ.");
        }
        return questionRepository.findByIdWithChoices(questionId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy câu hỏi với ID: " + questionId));
    }

    @Transactional
    public GrammarQuizQuestion addQuestion(Long topicId, GrammarQuizQuestionForm form) {
        GrammarTopic topic = getTopicById(topicId);
        long currentCount = questionRepository.countByGrammarTopicId(topicId);
        if (currentCount >= GrammarTopic.MAX_QUESTIONS_PER_TOPIC) {
            throw new IllegalStateException("Chủ đề ngữ pháp đã đạt số lượng câu hỏi tối đa (" + GrammarTopic.MAX_QUESTIONS_PER_TOPIC + " câu).");
        }

        if (form.getCorrectChoiceIndex() != null && form.getChoices() != null) {
            for (int i = 0; i < form.getChoices().size(); i++) {
                form.getChoices().get(i).setCorrect(i == form.getCorrectChoiceIndex());
            }
        }
        validateQuizChoices(form.getChoices());

        Integer sortOrder = form.getSortOrder();
        if (sortOrder == null || sortOrder <= 0) {
            Integer maxSort = questionRepository.findMaxSortOrderByGrammarTopicId(topicId);
            sortOrder = (maxSort != null) ? maxSort + 1 : 1;
        }

        GrammarQuizQuestion question = GrammarQuizQuestion.builder()
                .grammarTopic(topic)
                .sortOrder(sortOrder)
                .prompt(form.getPrompt().trim())
                .explanation(form.getExplanation() != null ? form.getExplanation().trim() : null)
                .build();
        topic.addQuestion(question);

        int choiceOrder = 1;
        for (GrammarQuizChoiceForm cf : form.getChoices()) {
            if (cf.getText() != null && !cf.getText().isBlank()) {
                GrammarQuizChoice choice = GrammarQuizChoice.builder()
                        .sortOrder(choiceOrder++)
                        .text(cf.getText().trim())
                        .correct(cf.isCorrect())
                        .build();
                question.addChoice(choice);
            }
        }

        return questionRepository.save(question);
    }

    @Transactional
    public GrammarQuizQuestion updateQuestion(Long questionId, GrammarQuizQuestionForm form) {
        GrammarQuizQuestion question = getQuestionById(questionId);

        if (form.getCorrectChoiceIndex() != null && form.getChoices() != null) {
            for (int i = 0; i < form.getChoices().size(); i++) {
                form.getChoices().get(i).setCorrect(i == form.getCorrectChoiceIndex());
            }
        }
        validateQuizChoices(form.getChoices());

        question.setPrompt(form.getPrompt().trim());
        question.setExplanation(form.getExplanation() != null ? form.getExplanation().trim() : null);
        if (form.getSortOrder() != null && form.getSortOrder() > 0) {
            question.setSortOrder(form.getSortOrder());
        }

        question.getChoices().clear();
        int choiceOrder = 1;
        for (GrammarQuizChoiceForm cf : form.getChoices()) {
            if (cf.getText() != null && !cf.getText().isBlank()) {
                GrammarQuizChoice choice = GrammarQuizChoice.builder()
                        .sortOrder(choiceOrder++)
                        .text(cf.getText().trim())
                        .correct(cf.isCorrect())
                        .build();
                question.addChoice(choice);
            }
        }

        return questionRepository.save(question);
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        GrammarQuizQuestion question = getQuestionById(questionId);
        questionRepository.delete(question);
    }

    @Transactional
    public void moveQuestion(Long questionId, boolean moveUp) {
        GrammarQuizQuestion question = getQuestionById(questionId);
        Long topicId = question.getGrammarTopic().getId();
        List<GrammarQuizQuestion> questions = questionRepository.findByGrammarTopicIdOrderBySortOrderAsc(topicId);

        int index = -1;
        for (int i = 0; i < questions.size(); i++) {
            if (questions.get(i).getId().equals(questionId)) {
                index = i;
                break;
            }
        }

        if (index == -1) {
            return;
        }

        if (moveUp && index > 0) {
            GrammarQuizQuestion prev = questions.get(index - 1);
            int temp = question.getSortOrder();
            question.setSortOrder(prev.getSortOrder());
            prev.setSortOrder(temp);
            questionRepository.save(question);
            questionRepository.save(prev);
        } else if (!moveUp && index < questions.size() - 1) {
            GrammarQuizQuestion next = questions.get(index + 1);
            int temp = question.getSortOrder();
            question.setSortOrder(next.getSortOrder());
            next.setSortOrder(temp);
            questionRepository.save(question);
            questionRepository.save(next);
        }
    }

    private void validateQuizChoices(List<GrammarQuizChoiceForm> choices) {
        if (choices == null) {
            throw new IllegalArgumentException("Câu hỏi phải có ít nhất 2 lựa chọn.");
        }
        List<GrammarQuizChoiceForm> nonBlankChoices = choices.stream()
                .filter(c -> c.getText() != null && !c.getText().isBlank())
                .toList();

        if (nonBlankChoices.size() < 2 || nonBlankChoices.size() > 6) {
            throw new IllegalArgumentException("Mỗi câu hỏi trắc nghiệm phải có từ 2 đến 6 lựa chọn (hiện có " + nonBlankChoices.size() + " lựa chọn).");
        }

        long correctCount = nonBlankChoices.stream().filter(GrammarQuizChoiceForm::isCorrect).count();
        if (correctCount != 1) {
            throw new IllegalArgumentException("Mỗi câu hỏi trắc nghiệm phải có đúng 1 đáp án đúng (hiện có " + correctCount + " đáp án được chọn).");
        }
    }
}

