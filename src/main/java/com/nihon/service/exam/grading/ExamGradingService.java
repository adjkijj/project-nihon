package com.nihon.service.exam.grading;

import com.nihon.dto.exam.result.ExamQuestionResultDto;
import com.nihon.dto.exam.result.ExamResultViewDto;
import com.nihon.dto.exam.result.ExamSectionResultDto;
import com.nihon.dto.exam.taker.ExamAttemptStatus;
import com.nihon.dto.exam.taker.ExamSessionAttempt;
import com.nihon.dto.exam.taker.ExamTakerChoiceDto;
import com.nihon.dto.exam.taker.ExamTakerPaperDto;
import com.nihon.dto.exam.taker.ExamTakerQuestionDto;
import com.nihon.dto.exam.taker.ExamTakerSectionDto;
import com.nihon.entity.ExamAttempt;
import com.nihon.entity.ExamAttemptAnswer;
import com.nihon.entity.ExamChoice;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import com.nihon.entity.ExamSection;
import com.nihon.repository.ExamAttemptAnswerRepository;
import com.nihon.repository.ExamAttemptRepository;
import com.nihon.repository.ExamPaperRepository;
import com.nihon.repository.ExamQuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dịch vụ chấm điểm máy chủ và quản lý kết quả bài thi trực tuyến (F40 - F45).
 * Điều phối chấm điểm từng dạng câu hỏi, lưu bền vững vào CSDL,
 * tạo báo cáo kết quả chi tiết, tự chấm điểm bài luận và luyện lại các câu sai.
 */
@Service("examGradingService")
public class ExamGradingService {

    private static final Logger log = LoggerFactory.getLogger(ExamGradingService.class);

    private final ExamAttemptRepository attemptRepository;
    private final ExamAttemptAnswerRepository attemptAnswerRepository;
    private final ExamPaperRepository paperRepository;
    private final ExamQuestionRepository questionRepository;
    private final Map<ExamQuestionType, QuestionGrader> graders;

    @org.springframework.beans.factory.annotation.Value("${app.history.retention-days:180}")
    private int retentionDays = 180;

    @org.springframework.beans.factory.annotation.Value("${app.exam.grade.s:90}")
    private BigDecimal gradeSThreshold = new BigDecimal("90");

    @org.springframework.beans.factory.annotation.Value("${app.exam.grade.a:80}")
    private BigDecimal gradeAThreshold = new BigDecimal("80");

    @org.springframework.beans.factory.annotation.Value("${app.exam.grade.b:65}")
    private BigDecimal gradeBThreshold = new BigDecimal("65");

    /**
     * Tính xếp hạng bài thi (S, A, B hoặc C) dựa theo tỷ lệ phần trăm điểm đạt được.
     * Ngưỡng được cấu hình trong application.properties:
     * - app.exam.grade.s=90 (S)
     * - app.exam.grade.a=80 (A)
     * - app.exam.grade.b=65 (B)
     * - Dưới ngưỡng B trả về C.
     * Đây là ngưỡng riêng của Project Nihon, không ghi là chuẩn JLPT.
     *
     * @param percentage Tỷ lệ phần trăm điểm (0 - 100)
     * @return Mã xếp hạng: "S", "A", "B", hoặc "C"
     */
    public String calculateGrade(BigDecimal percentage) {
        if (percentage == null) {
            return "C";
        }
        if (percentage.compareTo(gradeSThreshold) >= 0) {
            return "S";
        }
        if (percentage.compareTo(gradeAThreshold) >= 0) {
            return "A";
        }
        if (percentage.compareTo(gradeBThreshold) >= 0) {
            return "B";
        }
        return "C";
    }

    public String calculateGrade(double percentage) {
        return calculateGrade(BigDecimal.valueOf(percentage));
    }

    public void setGradeThresholds(BigDecimal s, BigDecimal a, BigDecimal b) {
        if (s != null) this.gradeSThreshold = s;
        if (a != null) this.gradeAThreshold = a;
        if (b != null) this.gradeBThreshold = b;
    }

    public ExamGradingService(ExamAttemptRepository attemptRepository,
                              ExamAttemptAnswerRepository attemptAnswerRepository,
                              ExamPaperRepository paperRepository,
                              ExamQuestionRepository questionRepository,
                              List<QuestionGrader> graderList) {
        this.attemptRepository = attemptRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.paperRepository = paperRepository;
        this.questionRepository = questionRepository;
        this.graders = graderList.stream()
                .collect(Collectors.toMap(QuestionGrader::supportsType, Function.identity()));
    }

    /**
     * Chấm điểm toàn bộ bài thi từ bộ đệm phiên làm việc (ExamSessionAttempt) và lưu bền vững vào CSDL.
     */
    @Transactional
    public ExamAttempt gradeAndPersist(ExamSessionAttempt sessionAttempt, String learnerId) {
        if (sessionAttempt == null) {
            throw new IllegalArgumentException("Dữ liệu bài làm thi không hợp lệ.");
        }

        // Nếu bài thi này đã được lưu trước đó, trả về bản ghi hiện tại
        Optional<ExamAttempt> existing = attemptRepository.findByIdWithDetails(sessionAttempt.getAttemptId());
        if (existing.isPresent()) {
            return existing.get();
        }

        ExamPaper paper = null;
        if (sessionAttempt.getPaperId() != null) {
            paper = paperRepository.findById(sessionAttempt.getPaperId()).orElse(null);
        }

        Instant now = Instant.now();
        Instant startedAt = sessionAttempt.getStartedAt() != null ? sessionAttempt.getStartedAt() : now;
        Instant submittedAt = sessionAttempt.getSubmittedAt() != null ? sessionAttempt.getSubmittedAt() : now;
        int durationSeconds = (int) Math.max(0, Duration.between(startedAt, submittedAt).toSeconds());

        ExamAttempt attempt = ExamAttempt.builder()
                .id(sessionAttempt.getAttemptId())
                .paper(paper)
                .learnerId(learnerId != null ? learnerId : sessionAttempt.getLearnerId())
                .startedAt(startedAt)
                .submittedAt(submittedAt)
                .durationSeconds(durationSeconds)
                .totalPoints(BigDecimal.ZERO)
                .maxPoints(BigDecimal.ZERO)
                .status(sessionAttempt.getStatus() != null ? sessionAttempt.getStatus() : ExamAttemptStatus.FINISHED)
                .answers(new ArrayList<>())
                .build();

        BigDecimal totalPoints = BigDecimal.ZERO;
        BigDecimal maxPoints = BigDecimal.ZERO;

        // Nạp danh sách câu hỏi cần chấm từ Paper hoặc QuestionRepository
        List<ExamQuestion> questions = getQuestionsForGrading(paper, sessionAttempt);

        for (ExamQuestion q : questions) {
            BigDecimal qMaxPoints = q.getPoints() != null ? q.getPoints() : BigDecimal.ONE;
            maxPoints = maxPoints.add(qMaxPoints);

            String studentAnswer = sessionAttempt.getAnswers() != null ? sessionAttempt.getAnswers().get(q.getId()) : null;
            boolean flagged = sessionAttempt.getMarkedForReview() != null && sessionAttempt.getMarkedForReview().contains(q.getId());

            QuestionGrader grader = graders.get(q.getType());
            GradingResult result;
            if (grader != null) {
                result = grader.grade(q, studentAnswer);
            } else {
                result = GradingResult.builder()
                        .pointsAwarded(BigDecimal.ZERO)
                        .correct(false)
                        .feedback("Không tìm thấy bộ chấm phù hợp cho dạng câu hỏi: " + q.getType())
                        .build();
            }

            BigDecimal awarded = result.getPointsAwarded() != null ? result.getPointsAwarded() : BigDecimal.ZERO;
            totalPoints = totalPoints.add(awarded);

            ExamAttemptAnswer attemptAnswer = ExamAttemptAnswer.builder()
                    .attempt(attempt)
                    .questionId(q.getId())
                    .answerValue(studentAnswer)
                    .pointsAwarded(awarded)
                    .correct(result.getCorrect())
                    .flagged(flagged)
                    .build();

            attempt.getAnswers().add(attemptAnswer);
        }

        attempt.setTotalPoints(totalPoints.setScale(2, RoundingMode.HALF_UP));
        attempt.setMaxPoints(maxPoints.setScale(2, RoundingMode.HALF_UP));

        return attemptRepository.save(attempt);
    }

    /**
     * Lập DTO chi tiết hiển thị trang kết quả thi.
     * Kiểm tra bảo mật: chỉ đúng learnerId mới được xem, người khác trả về 404 Not Found.
     */
    @Transactional(readOnly = true)
    public ExamResultViewDto buildResultView(String attemptId, String currentLearnerId) {
        if (attemptId == null || attemptId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài thi.");
        }

        ExamAttempt attempt = attemptRepository.findByIdWithDetails(attemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài thi."));

        // Bảo mật: Người khác nhận 404 (không tiết lộ sự tồn tại của bài thi)
        if (currentLearnerId == null || !currentLearnerId.equals(attempt.getLearnerId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài thi.");
        }

        ExamPaper paper = attempt.getPaper();
        Map<Long, ExamAttemptAnswer> answerMap = attempt.getAnswers().stream()
                .filter(a -> a.getQuestionId() != null)
                .collect(Collectors.toMap(ExamAttemptAnswer::getQuestionId, Function.identity(), (a1, a2) -> a1));

        List<ExamQuestion> questions = paper != null ? paper.getAllQuestions() : List.of();
        if (questions.isEmpty() && !attempt.getAnswers().isEmpty()) {
            List<Long> qIds = attempt.getAnswers().stream().map(ExamAttemptAnswer::getQuestionId).filter(Objects::nonNull).toList();
            questions = questionRepository.findAllById(qIds);
        }

        int globalIndex = 1;
        int correctCount = 0;
        int incorrectCount = 0;
        int essayCount = 0;

        List<ExamQuestionResultDto> questionResultDtos = new ArrayList<>();
        Map<Long, List<ExamQuestionResultDto>> bySection = new LinkedHashMap<>();

        for (ExamQuestion q : questions) {
            ExamAttemptAnswer ans = answerMap.get(q.getId());
            Long answerId = ans != null ? ans.getId() : null;
            boolean selfApproved = ans != null && ans.isSelfApproved();
            String studentAnswer = ans != null ? ans.getAnswerValue() : null;
            boolean flagged = ans != null && ans.isFlagged();
            BigDecimal awarded = ans != null ? ans.getPointsAwarded() : BigDecimal.ZERO;
            Boolean correct = ans != null ? ans.getCorrect() : null;

            QuestionGrader grader = graders.get(q.getType());
            GradingResult gradingResult = grader != null ? grader.grade(q, studentAnswer) : null;

            // Nếu câu tự luận đã được người học tự chấm trước đó, dùng điểm từ DB
            if (q.getType() == ExamQuestionType.ESSAY && ans != null && ans.getPointsAwarded().compareTo(BigDecimal.ZERO) > 0) {
                if (gradingResult != null) {
                    gradingResult.setPointsAwarded(ans.getPointsAwarded());
                    gradingResult.setCorrect(ans.getCorrect());
                }
            }

            // Nếu câu dịch thuật đã được tự xác nhận, cập nhật kết quả tương ứng
            if (q.getType() == ExamQuestionType.TRANSLATION && selfApproved) {
                if (gradingResult != null) {
                    gradingResult.setPointsAwarded(awarded);
                    gradingResult.setCorrect(true);
                }
            }

            if (correct == null) {
                essayCount++;
            } else if (Boolean.TRUE.equals(correct)) {
                correctCount++;
            } else {
                incorrectCount++;
            }

            String displayAnswer = formatAnswerDisplay(q, studentAnswer);

            ExamQuestionResultDto qDto = ExamQuestionResultDto.builder()
                    .questionId(q.getId())
                    .answerId(answerId)
                    .questionIndex(globalIndex++)
                    .sectionId(q.getSection() != null ? q.getSection().getId() : null)
                    .sectionTitle(q.getSection() != null ? q.getSection().getTitle() : "Phần thi")
                    .question(q)
                    .studentAnswer(studentAnswer)
                    .studentAnswerDisplay(displayAnswer)
                    .pointsAwarded(awarded)
                    .maxPoints(q.getPoints() != null ? q.getPoints() : BigDecimal.ONE)
                    .correct(correct)
                    .flagged(flagged)
                    .selfApproved(selfApproved)
                    .gradingResult(gradingResult)
                    .build();

            questionResultDtos.add(qDto);

            Long secId = q.getSection() != null ? q.getSection().getId() : 0L;
            bySection.computeIfAbsent(secId, k -> new ArrayList<>()).add(qDto);
        }

        // Tính điểm số theo từng phần thi (Section)
        List<ExamSectionResultDto> sectionResultDtos = new ArrayList<>();
        if (paper != null && paper.getSections() != null) {
            for (ExamSection s : paper.getSections()) {
                List<ExamQuestionResultDto> secQuestions = bySection.getOrDefault(s.getId(), List.of());
                BigDecimal secEarned = secQuestions.stream()
                        .map(ExamQuestionResultDto::getPointsAwarded)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal secMax = secQuestions.stream()
                        .map(ExamQuestionResultDto::getMaxPoints)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                double secPct = secMax.compareTo(BigDecimal.ZERO) > 0
                        ? secEarned.multiply(BigDecimal.valueOf(100)).divide(secMax, 1, RoundingMode.HALF_UP).doubleValue()
                        : 0.0;

                sectionResultDtos.add(ExamSectionResultDto.builder()
                        .sectionId(s.getId())
                        .title(s.getTitle())
                        .sortOrder(s.getSortOrder())
                        .pointsEarned(secEarned)
                        .maxPoints(secMax)
                        .percentage(secPct)
                        .build());
            }
        }

        BigDecimal pctBd = attempt.getMaxPoints().compareTo(BigDecimal.ZERO) > 0
                ? attempt.getTotalPoints().multiply(BigDecimal.valueOf(100)).divide(attempt.getMaxPoints(), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        double percentage = pctBd.setScale(1, RoundingMode.HALF_UP).doubleValue();
        String grade = calculateGrade(pctBd);

        return ExamResultViewDto.builder()
                .attempt(attempt)
                .paper(paper)
                .totalPoints(attempt.getTotalPoints())
                .maxPoints(attempt.getMaxPoints())
                .percentage(percentage)
                .grade(grade)
                .durationSeconds(attempt.getDurationSeconds())
                .totalQuestions(questions.size())
                .correctCount(correctCount)
                .incorrectCount(incorrectCount)
                .essayCount(essayCount)
                .sectionResults(sectionResultDtos)
                .questionResults(questionResultDtos)
                .build();
    }

    /**
     * Tự chấm điểm cho câu hỏi tự luận (ESSAY - F45).
     * Điểm số nằm trong khoảng từ 0.00 đến tối đa điểm của câu hỏi.
     */
    @Transactional
    public void gradeEssay(String attemptId, Long questionId, BigDecimal points, String currentLearnerId) {
        if (attemptId == null || questionId == null || points == null) {
            throw new IllegalArgumentException("Dữ liệu tự chấm điểm không hợp lệ.");
        }

        ExamAttempt attempt = attemptRepository.findByIdWithDetails(attemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài thi."));

        if (currentLearnerId == null || !currentLearnerId.equals(attempt.getLearnerId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài thi.");
        }

        ExamQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy câu hỏi với ID: " + questionId));

        if (question.getType() != ExamQuestionType.ESSAY) {
            throw new IllegalStateException("Chỉ câu hỏi tự luận (ESSAY) mới được phép tự chấm điểm.");
        }

        BigDecimal maxPoints = question.getPoints() != null ? question.getPoints() : BigDecimal.ONE;
        if (points.compareTo(BigDecimal.ZERO) < 0 || points.compareTo(maxPoints) > 0) {
            throw new IllegalArgumentException(String.format("Điểm tự chấm phải nằm trong khoảng từ 0.0 đến %.2f.", maxPoints));
        }

        ExamAttemptAnswer targetAnswer = attempt.getAnswers().stream()
                .filter(a -> questionId.equals(a.getQuestionId()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy câu trả lời của câu hỏi ID: " + questionId));

        targetAnswer.setPointsAwarded(points.setScale(2, RoundingMode.HALF_UP));
        targetAnswer.setCorrect(points.compareTo(BigDecimal.ZERO) > 0);
        attemptAnswerRepository.save(targetAnswer);

        // Tính lại tổng điểm của bài thi
        BigDecimal newTotal = attempt.getAnswers().stream()
                .map(ExamAttemptAnswer::getPointsAwarded)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        attempt.setTotalPoints(newTotal.setScale(2, RoundingMode.HALF_UP));
        attemptRepository.save(attempt);
    }

    /**
     * Tự xác nhận hoặc hủy tự xác nhận điểm cho câu hỏi dịch thuật (TRANSLATION).
     * Chỉ áp dụng cho câu hỏi loại TRANSLATION, sau khi nộp bài và xem kết quả.
     *
     * @param attemptId ID lượt làm bài
     * @param answerId ID câu trả lời trong exam_attempt_answer
     * @param approve true nếu tự xác nhận (lấy điểm tối đa), false nếu hủy xác nhận (khôi phục điểm tự động)
     * @param currentLearnerId learner_id từ cookie
     */
    @Transactional
    public void selfApproveTranslation(String attemptId, Long answerId, boolean approve, String currentLearnerId) {
        if (attemptId == null || answerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dữ liệu xác nhận không hợp lệ.");
        }

        ExamAttempt attempt = attemptRepository.findByIdWithDetails(attemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài thi."));

        // Kiểm tra learner_id trong cookie khớp với attempt
        if (currentLearnerId == null || !currentLearnerId.equals(attempt.getLearnerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Mã học viên trong cookie không khớp với bài thi.");
        }

        // Chỉ áp dụng sau khi nộp bài và xem kết quả
        if (attempt.getStatus() == ExamAttemptStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ được phép tự xác nhận sau khi nộp bài thi.");
        }

        // Kiểm tra câu trả lời thuộc về bài thi
        ExamAttemptAnswer foundAnswer = attempt.getAnswers().stream()
                .filter(a -> answerId.equals(a.getId()))
                .findFirst()
                .orElse(null);

        if (foundAnswer == null) {
            // Kiểm tra xem answer có tồn tại nhưng thuộc attempt khác hay không
            ExamAttemptAnswer existAns = attemptAnswerRepository.findById(answerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy câu trả lời với ID: " + answerId));
            if (existAns.getAttempt() == null || !attemptId.equals(existAns.getAttempt().getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Câu trả lời không thuộc bài thi được chỉ định.");
            }
            foundAnswer = existAns;
        }

        final ExamAttemptAnswer targetAnswer = foundAnswer;

        ExamQuestion question = questionRepository.findById(targetAnswer.getQuestionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy câu hỏi với ID: " + targetAnswer.getQuestionId()));

        // Chỉ áp dụng cho câu hỏi loại TRANSLATION
        if (question.getType() != ExamQuestionType.TRANSLATION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ câu hỏi dịch thuật (TRANSLATION) mới được phép tự xác nhận.");
        }

        BigDecimal maxPoints = question.getPoints() != null ? question.getPoints() : BigDecimal.ONE;

        if (approve) {
            // Điểm cập nhật bằng điểm tối đa của câu
            targetAnswer.setSelfApproved(true);
            targetAnswer.setPointsAwarded(maxPoints.setScale(2, RoundingMode.HALF_UP));
            targetAnswer.setCorrect(true);
        } else {
            // Hủy xác nhận: khôi phục điểm chấm tự động ban đầu
            targetAnswer.setSelfApproved(false);
            QuestionGrader grader = graders.get(ExamQuestionType.TRANSLATION);
            GradingResult autoResult = grader != null ? grader.grade(question, targetAnswer.getAnswerValue()) : null;
            BigDecimal autoPoints = autoResult != null ? autoResult.getPointsAwarded() : BigDecimal.ZERO;
            Boolean autoCorrect = autoResult != null ? autoResult.getCorrect() : false;
            targetAnswer.setPointsAwarded(autoPoints.setScale(2, RoundingMode.HALF_UP));
            targetAnswer.setCorrect(autoCorrect);
        }

        attemptAnswerRepository.save(targetAnswer);

        // Tổng điểm và xếp loại tính lại
        BigDecimal newTotal = attempt.getAnswers().stream()
                .map(ExamAttemptAnswer::getPointsAwarded)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        attempt.setTotalPoints(newTotal.setScale(2, RoundingMode.HALF_UP));
        attemptRepository.save(attempt);
    }

    /**
     * Tạo một bài thi mini để luyện lại các câu trả lời sai (F48).
     * Lưu tạm trong session, không ghi CSDL bài thi chính thức.
     */
    @Transactional(readOnly = true)
    public ExamSessionAttempt createRetryMiniAttempt(String originalAttemptId, String currentLearnerId) {
        ExamAttempt attempt = attemptRepository.findByIdWithDetails(originalAttemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài thi gốc."));

        if (currentLearnerId == null || !currentLearnerId.equals(attempt.getLearnerId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài thi gốc.");
        }

        // Lọc các câu sai (correct == false)
        List<ExamAttemptAnswer> incorrectAnswers = attempt.getAnswers().stream()
                .filter(a -> Boolean.FALSE.equals(a.getCorrect()))
                .toList();

        if (incorrectAnswers.isEmpty()) {
            throw new IllegalStateException("Bạn không có câu trả lời sai nào trong bài thi này để luyện tập lại!");
        }

        List<Long> incorrectQIds = incorrectAnswers.stream().map(ExamAttemptAnswer::getQuestionId).toList();
        List<ExamQuestion> incorrectQuestions = questionRepository.findAllById(incorrectQIds);

        ExamPaper originalPaper = attempt.getPaper();
        String originalTitle = originalPaper != null ? originalPaper.getTitle() : "Đề thi";
        String originalCode = originalPaper != null ? originalPaper.getCode() : "RETRY";

        String miniAttemptId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        int miniDuration = Math.max(3, (int) Math.ceil(incorrectQuestions.size() * 1.5));
        Instant deadline = now.plus(Duration.ofMinutes(miniDuration));

        // Xây dựng DTO câu hỏi mini
        int qIdx = 1;
        List<ExamTakerQuestionDto> qDtos = new ArrayList<>();
        Map<Long, List<Long>> shuffledChoiceIds = new HashMap<>();

        for (ExamQuestion q : incorrectQuestions) {
            List<ExamTakerChoiceDto> choiceDtos = new ArrayList<>();
            if (q.getChoices() != null) {
                int cOrder = 1;
                for (ExamChoice c : q.getChoices()) {
                    choiceDtos.add(ExamTakerChoiceDto.builder()
                            .id(c.getId())
                            .sortOrder(cOrder++)
                            .text(c.getText())
                            .build());
                }
            }

            qDtos.add(ExamTakerQuestionDto.builder()
                    .id(q.getId())
                    .sectionId(1L)
                    .questionIndex(qIdx++)
                    .sortOrder(q.getSortOrder())
                    .type(q.getType())
                    .promptJa(q.getPromptJa())
                    .promptVi(q.getPromptVi())
                    .points(q.getPoints())
                    .choices(choiceDtos)
                    .build());
        }

        ExamTakerSectionDto miniSection = ExamTakerSectionDto.builder()
                .id(1L)
                .title("Các câu hỏi đã làm sai cần ôn tập lại")
                .sortOrder(1)
                .instructions("Hãy tập trung làm lại những câu hỏi này để củng cố kiến thức.")
                .questions(qDtos)
                .build();

        ExamTakerPaperDto paperDto = ExamTakerPaperDto.builder()
                .id(originalPaper != null ? originalPaper.getId() : null)
                .code(originalCode + "-RETRY")
                .title("[Luyện lại câu sai] " + originalTitle)
                .description("Bài luyện tập tập trung các câu trả lời chưa chính xác.")
                .durationMinutes(miniDuration)
                .category("REVIEW")
                .totalQuestions(qDtos.size())
                .totalPoints(qDtos.stream().map(ExamTakerQuestionDto::getPoints).reduce(BigDecimal.ZERO, BigDecimal::add))
                .shuffleChoices(false)
                .sections(List.of(miniSection))
                .build();

        return ExamSessionAttempt.builder()
                .attemptId(miniAttemptId)
                .learnerId(currentLearnerId)
                .paperId(originalPaper != null ? originalPaper.getId() : null)
                .paperCode(originalCode + "-RETRY")
                .paperTitle("[Luyện lại câu sai] " + originalTitle)
                .paperDescription("Bài luyện tập tập trung các câu trả lời chưa chính xác.")
                .durationMinutes(miniDuration)
                .category("REVIEW")
                .startedAt(now)
                .deadline(deadline)
                .status(ExamAttemptStatus.RUNNING)
                .answers(new HashMap<>())
                .markedForReview(new HashSet<>())
                .shuffledChoiceIds(shuffledChoiceIds)
                .paperDto(paperDto)
                .build();
    }

    /**
     * Xóa một bài thi của người học (khớp learnerId).
     */
    @Transactional
    public void deleteAttempt(String attemptId, String currentLearnerId) {
        if (currentLearnerId != null && attemptId != null) {
            attemptRepository.deleteByLearnerIdAndId(currentLearnerId, attemptId);
        }
    }

    /**
     * Xóa toàn bộ bài thi của người học (khớp learnerId).
     */
    @Transactional
    public int deleteAllAttempts(String currentLearnerId) {
        if (currentLearnerId == null) {
            return 0;
        }
        return attemptRepository.deleteByLearnerId(currentLearnerId);
    }

    /**
     * Dọn dẹp định kỳ các bài thi quá hạn lưu trữ (retention cleanup).
     */
    @Transactional
    public int cleanupExpiredAttempts(Instant cutoff) {
        if (cutoff == null) {
            return 0;
        }
        return attemptRepository.deleteOlderThan(cutoff);
    }

    /**
     * Chạy hằng ngày theo cron để dọn dẹp các bài thi cũ quá retention-days.
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "${app.history.cleanup-cron:0 15 3 * * *}")
    public void scheduledCleanup() {
        try {
            Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
            int deleted = cleanupExpiredAttempts(cutoff);
            if (deleted > 0) {
                log.info("Exam attempt retention cleanup: deleted {} expired attempts older than {} days", deleted, retentionDays);
            }
        } catch (RuntimeException e) {
            log.warn("Exam attempt retention cleanup failed: {}", e.getMessage());
        }
    }

    private List<ExamQuestion> getQuestionsForGrading(ExamPaper paper, ExamSessionAttempt sessionAttempt) {
        if (paper != null && paper.getSections() != null) {
            List<ExamQuestion> list = paper.getAllQuestions();
            if (!list.isEmpty()) {
                return list;
            }
        }
        // Trường hợp bài thi mini hoặc danh sách câu hỏi riêng
        if (sessionAttempt.getAnswers() != null && !sessionAttempt.getAnswers().isEmpty()) {
            return questionRepository.findAllById(sessionAttempt.getAnswers().keySet());
        }
        return List.of();
    }

    private String formatAnswerDisplay(ExamQuestion question, String studentAnswer) {
        if (studentAnswer == null || studentAnswer.isBlank()) {
            return "(Chưa trả lời)";
        }
        if (question.getType() == ExamQuestionType.MULTIPLE_CHOICE && question.getChoices() != null) {
            for (ExamChoice c : question.getChoices()) {
                if (c.getId() != null && studentAnswer.equals(c.getId().toString())) {
                    return "[" + c.getSortOrder() + "] " + c.getText();
                }
            }
        }
        return studentAnswer;
    }
}
