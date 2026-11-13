package com.nihon.service.exam;

import com.nihon.dto.exam.taker.ExamAnswerResponse;
import com.nihon.dto.exam.taker.ExamAnswerSubmission;
import com.nihon.dto.exam.taker.ExamAttemptStatus;
import com.nihon.dto.exam.taker.ExamSessionAttempt;
import com.nihon.dto.exam.taker.ExamTakerChoiceDto;
import com.nihon.dto.exam.taker.ExamTakerPaperDto;
import com.nihon.dto.exam.taker.ExamTakerQuestionDto;
import com.nihon.dto.exam.taker.ExamTakerSectionDto;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamChoice;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import com.nihon.entity.ExamSection;
import com.nihon.history.LearnerContext;
import com.nihon.repository.ExamPaperRepository;
import com.nihon.service.exam.grading.ExamGradingService;
import com.nihon.validation.TextNormalizer;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.UUID;

/**
 * Dịch vụ điều phối quá trình làm bài thi trực tuyến của học viên (F42 - F48).
 * Quản lý vòng đời ExamSessionAttempt trong HttpSession, đếm ngược hạn chót máy chủ,
 * xáo trộn lựa chọn theo seed, tự động lưu nháp và chấm điểm bền vững qua ExamGradingService.
 */
@Service
@Transactional(readOnly = true)
public class ExamTakingService {

    private final ExamPaperRepository paperRepository;
    private final ExamGradingService gradingService;

    public ExamTakingService(ExamPaperRepository paperRepository,
                             ExamGradingService gradingService) {
        this.paperRepository = paperRepository;
        this.gradingService = gradingService;
    }

    /**
     * Lấy danh sách các đề thi đã được xuất bản (published = true).
     */
    public List<ExamPaper> getPublishedExams(ExamCategory category) {
        return paperRepository.findPublishedExams(category);
    }

    /**
     * Lấy thông tin chi tiết đề thi đã xuất bản theo mã code.
     */
    public ExamPaper getPublishedExamByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new NoSuchElementException("Mã đề thi không hợp lệ.");
        }
        String cleanCode = TextNormalizer.normalize(code.trim().toUpperCase());
        ExamPaper paper = paperRepository.findByCodeAndPublishedTrue(cleanCode)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đề thi hoặc đề thi chưa được xuất bản: " + cleanCode));

        // Nạp các quan hệ lười (lazy collections)
        if (paper.getSections() != null) {
            for (ExamSection section : paper.getSections()) {
                if (section.getQuestions() != null) {
                    for (ExamQuestion q : section.getQuestions()) {
                        if (q.getChoices() != null) {
                            q.getChoices().size();
                        }
                    }
                }
            }
        }
        return paper;
    }

    /**
     * Bắt đầu một lượt làm bài thi mới, tạo ExamSessionAttempt và lưu trong HttpSession.
     */
    @Transactional
    public ExamSessionAttempt startExam(String code, HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Phiên làm việc (Session) không tồn tại.");
        }

        ExamPaper paper = getPublishedExamByCode(code);
        if (paper.getTotalQuestions() == 0) {
            throw new IllegalStateException("Đề thi '" + paper.getTitle() + "' hiện chưa có câu hỏi nào để bắt đầu làm bài.");
        }

        String attemptId = UUID.randomUUID().toString();
        long seed = System.currentTimeMillis();
        Instant now = Instant.now();
        Instant deadline = now.plus(Duration.ofMinutes(paper.getDurationMinutes()));
        String learnerId = LearnerContext.current().orElseGet(() -> UUID.randomUUID().toString());

        Map<Long, List<Long>> shuffledChoiceIds = new HashMap<>();
        ExamTakerPaperDto paperDto = sanitizePaperForTaking(paper, seed, shuffledChoiceIds);

        ExamSessionAttempt attempt = ExamSessionAttempt.builder()
                .attemptId(attemptId)
                .learnerId(learnerId)
                .paperId(paper.getId())
                .paperCode(paper.getCode())
                .paperTitle(paper.getTitle())
                .paperDescription(paper.getDescription())
                .durationMinutes(paper.getDurationMinutes())
                .category(paper.getCategory().name())
                .startedAt(now)
                .deadline(deadline)
                .status(ExamAttemptStatus.RUNNING)
                .answers(new HashMap<>())
                .markedForReview(new HashSet<>())
                .seed(seed)
                .shuffledChoiceIds(shuffledChoiceIds)
                .paperDto(paperDto)
                .build();

        storeAttemptInSession(session, attempt);
        return attempt;
    }

    /**
     * Lấy bài làm hiện tại theo attemptId từ HttpSession.
     */
    public ExamSessionAttempt getAttempt(String attemptId, HttpSession session) {
        if (session == null || attemptId == null || attemptId.isBlank()) {
            return null;
        }

        Map<String, ExamSessionAttempt> attemptsMap = getSessionAttemptsMap(session);
        ExamSessionAttempt attempt = attemptsMap.get(attemptId);
        if (attempt == null) {
            return null;
        }

        // Tự động kiểm tra hết hạn nếu đang RUNNING
        if (attempt.getStatus() == ExamAttemptStatus.RUNNING) {
            if (attempt.isExpired(Instant.now())) {
                finalizeAttempt(attempt, ExamAttemptStatus.EXPIRED);
                String learnerId = LearnerContext.current().orElse(attempt.getLearnerId());
                gradingService.gradeAndPersist(attempt, learnerId);
            }
        }

        return attempt;
    }

    /**
     * Lưu một ExamSessionAttempt trực tiếp vào session (dùng cho mini attempt hoặc luyện lại).
     */
    public void putSessionAttempt(HttpSession session, ExamSessionAttempt attempt) {
        if (session != null && attempt != null) {
            storeAttemptInSession(session, attempt);
        }
    }

    /**
     * Lưu câu trả lời hoặc đánh dấu xem lại cho câu hỏi (F48 - Tự lưu nháp).
     */
    public ExamAnswerResponse saveAnswer(String attemptId, ExamAnswerSubmission submission, HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Phiên làm việc không hợp lệ.");
        }
        ExamSessionAttempt attempt = getAttempt(attemptId, session);
        if (attempt == null) {
            throw new IllegalArgumentException("Không tìm thấy bài làm của phiên hiện tại.");
        }

        Instant now = Instant.now();
        if (attempt.getStatus() == ExamAttemptStatus.EXPIRED || attempt.isExpired(now)) {
            finalizeAttempt(attempt, ExamAttemptStatus.EXPIRED);
            String learnerId = LearnerContext.current().orElse(attempt.getLearnerId());
            gradingService.gradeAndPersist(attempt, learnerId);

            return ExamAnswerResponse.builder()
                    .status("EXPIRED")
                    .questionId(submission.getQuestionId())
                    .message("Hết thời gian làm bài.")
                    .remainingSeconds(0)
                    .answeredCount(attempt.getAnsweredCount())
                    .build();
        }

        if (attempt.getStatus() != ExamAttemptStatus.RUNNING) {
            return ExamAnswerResponse.builder()
                    .status("ERROR")
                    .questionId(submission.getQuestionId())
                    .message("Bài thi đã kết thúc hoặc đã nộp, không thể lưu câu trả lời.")
                    .remainingSeconds(0)
                    .answeredCount(attempt.getAnsweredCount())
                    .build();
        }

        if (submission.getAnswer() != null && submission.getAnswer().length() > 1000) {
            throw new IllegalArgumentException("Câu trả lời không được vượt quá 1000 ký tự.");
        }

        // Cập nhật câu trả lời nếu được truyền lên
        if (submission.getAnswer() != null) {
            String cleanAnswer = TextNormalizer.normalize(submission.getAnswer().trim());
            attempt.getAnswers().put(submission.getQuestionId(), cleanAnswer);
        }

        // Cập nhật trạng thái đánh dấu xem lại nếu có
        if (submission.getMarkedForReview() != null) {
            if (submission.getMarkedForReview()) {
                attempt.getMarkedForReview().add(submission.getQuestionId());
            } else {
                attempt.getMarkedForReview().remove(submission.getQuestionId());
            }
        }

        return ExamAnswerResponse.builder()
                .status("OK")
                .questionId(submission.getQuestionId())
                .message("Đã lưu nháp thành công.")
                .remainingSeconds(attempt.getRemainingSeconds(now))
                .answeredCount(attempt.getAnsweredCount())
                .build();
    }

    /**
     * Kết thúc bài thi (Nộp bài) và chấm điểm bền vững vào CSDL (F40 - F45).
     */
    @Transactional
    public ExamSessionAttempt finishExam(String attemptId, HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Phiên làm việc không hợp lệ.");
        }
        ExamSessionAttempt attempt = getAttempt(attemptId, session);
        if (attempt == null) {
            throw new NoSuchElementException("Không tìm thấy bài làm thi để hoàn tất.");
        }

        // Nếu đã hoàn thành trước đó (do đã nộp hoặc hết hạn), không xử lý lại
        if (attempt.getStatus() != ExamAttemptStatus.RUNNING) {
            return attempt;
        }

        Instant now = Instant.now();
        ExamAttemptStatus finalStatus = attempt.isExpired(now) ? ExamAttemptStatus.EXPIRED : ExamAttemptStatus.FINISHED;
        finalizeAttempt(attempt, finalStatus);

        String learnerId = (attempt.getLearnerId() != null && !attempt.getLearnerId().isBlank())
                ? attempt.getLearnerId()
                : LearnerContext.current().orElse(null);
        gradingService.gradeAndPersist(attempt, learnerId);

        return attempt;
    }

    private void finalizeAttempt(ExamSessionAttempt attempt, ExamAttemptStatus finalStatus) {
        if (attempt.getStatus() == ExamAttemptStatus.RUNNING) {
            attempt.setStatus(finalStatus);
            attempt.setSubmittedAt(Instant.now());
        }
    }

    /**
     * Lọc sạch dữ liệu bài thi để hiển thị cho thí sinh.
     * TUYỆT ĐỐI KHÔNG chứa: correct, explanation hay answerKeys!
     */
    private ExamTakerPaperDto sanitizePaperForTaking(ExamPaper paper,
                                                     long seed,
                                                     Map<Long, List<Long>> shuffledChoiceIds) {
        int globalQuestionIndex = 1;
        List<ExamTakerSectionDto> sectionDtos = new ArrayList<>();

        if (paper.getSections() != null) {
            for (ExamSection s : paper.getSections()) {
                List<ExamTakerQuestionDto> questionDtos = new ArrayList<>();
                if (s.getQuestions() != null) {
                    for (ExamQuestion q : s.getQuestions()) {
                        List<ExamTakerChoiceDto> choiceDtos = new ArrayList<>();

                        if (q.getType() == ExamQuestionType.MULTIPLE_CHOICE && q.getChoices() != null) {
                            List<ExamChoice> choicesCopy = new ArrayList<>(q.getChoices());
                            if (paper.isShuffleChoices() && choicesCopy.size() > 1) {
                                Collections.shuffle(choicesCopy, new Random(seed + q.getId()));
                            }
                            List<Long> orderedIds = new ArrayList<>();
                            int choiceOrder = 1;
                            for (ExamChoice c : choicesCopy) {
                                orderedIds.add(c.getId());
                                choiceDtos.add(ExamTakerChoiceDto.builder()
                                        .id(c.getId())
                                        .sortOrder(choiceOrder++)
                                        .text(c.getText())
                                        .build());
                            }
                            shuffledChoiceIds.put(q.getId(), orderedIds);
                        } else if (q.getType() == ExamQuestionType.ORDERING && q.getChoices() != null) {
                            int choiceOrder = 1;
                            for (ExamChoice c : q.getChoices()) {
                                choiceDtos.add(ExamTakerChoiceDto.builder()
                                        .id(c.getId())
                                        .sortOrder(choiceOrder++)
                                        .text(c.getText())
                                        .build());
                            }
                        }

                        questionDtos.add(ExamTakerQuestionDto.builder()
                                .id(q.getId())
                                .sectionId(s.getId())
                                .questionIndex(globalQuestionIndex++)
                                .sortOrder(q.getSortOrder())
                                .type(q.getType())
                                .promptJa(q.getPromptJa())
                                .promptVi(q.getPromptVi())
                                .points(q.getPoints())
                                .choices(choiceDtos)
                                .build());
                    }
                }

                sectionDtos.add(ExamTakerSectionDto.builder()
                        .id(s.getId())
                        .title(s.getTitle())
                        .sortOrder(s.getSortOrder())
                        .instructions(s.getInstructions())
                        .questions(questionDtos)
                        .build());
            }
        }

        return ExamTakerPaperDto.builder()
                .id(paper.getId())
                .code(paper.getCode())
                .title(paper.getTitle())
                .description(paper.getDescription())
                .durationMinutes(paper.getDurationMinutes())
                .category(paper.getCategory().name())
                .totalQuestions(paper.getTotalQuestions())
                .totalPoints(paper.getTotalPoints())
                .shuffleChoices(paper.isShuffleChoices())
                .sections(sectionDtos)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, ExamSessionAttempt> getSessionAttemptsMap(HttpSession session) {
        Object attr = session.getAttribute(ExamSessionAttempt.SESSION_ATTEMPTS_MAP);
        if (attr instanceof Map<?, ?> map) {
            return (Map<String, ExamSessionAttempt>) map;
        }
        Map<String, ExamSessionAttempt> newMap = new HashMap<>();
        session.setAttribute(ExamSessionAttempt.SESSION_ATTEMPTS_MAP, newMap);
        return newMap;
    }

    private void storeAttemptInSession(HttpSession session, ExamSessionAttempt attempt) {
        Map<String, ExamSessionAttempt> attemptsMap = getSessionAttemptsMap(session);
        attemptsMap.put(attempt.getAttemptId(), attempt);
        session.setAttribute(ExamSessionAttempt.SESSION_ATTEMPTS_MAP, attemptsMap);
    }
}
