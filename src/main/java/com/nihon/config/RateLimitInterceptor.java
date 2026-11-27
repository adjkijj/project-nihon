package com.nihon.config;

import com.nihon.service.excel.RateLimitExceededException;
import com.nihon.service.excel.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor kiểm soát tần suất yêu cầu (Rate Limiting) cho các thao tác trọng yếu của người học:
 * 1. Bắt đầu bài thi: POST /exams/{code}/start (ngưỡng app.rate-limit.exam-start, mặc định 10 lượt/phút)
 * 2. Nộp đáp án: POST /exams/attempt/{id}/answer (ngưỡng app.rate-limit.exam-answer, mặc định 60 lượt/phút)
 * 3. Kết thúc bài thi: POST /exams/attempt/{id}/finish (ngưỡng app.rate-limit.exam-finish, mặc định 10 lượt/phút)
 * 4. Kiểm tra quiz ngữ pháp: POST /grammar/{id}/quiz/check (ngưỡng app.rate-limit.grammar-quiz, mặc định 30 lượt/phút)
 * 5. Xóa lịch sử học tập: POST /history/delete-all, POST /history/exams/delete-all, POST /history/exams/{id}/delete
 *    (ngưỡng app.rate-limit.history-delete, mặc định 5 lượt/phút)
 *
 * Định danh người dùng dựa trên IP thật đã được thẩm định an toàn qua ClientIpResolver.
 * Khi vượt ngưỡng, hệ thống trả về HTTP 429 Too Many Requests với thông báo chung, không để lộ thông tin nội bộ.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final ClientIpResolver clientIpResolver;
    private final RateLimiterService rateLimiterService;

    @Value("${app.rate-limit.exam-start:10}")
    private int examStartLimit = 10;

    @Value("${app.rate-limit.exam-answer:60}")
    private int examAnswerLimit = 60;

    @Value("${app.rate-limit.exam-finish:10}")
    private int examFinishLimit = 10;

    @Value("${app.rate-limit.grammar-quiz:30}")
    private int grammarQuizLimit = 30;

    @Value("${app.rate-limit.history-delete:5}")
    private int historyDeleteLimit = 5;

    public RateLimitInterceptor(
            @Autowired(required = false) ClientIpResolver clientIpResolver,
            @Autowired(required = false) RateLimiterService rateLimiterService) {
        this.clientIpResolver = clientIpResolver;
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        if (request.getContextPath() != null && !request.getContextPath().isEmpty() && uri.startsWith(request.getContextPath())) {
            uri = uri.substring(request.getContextPath().length());
        }
        if (uri.endsWith("/") && uri.length() > 1) {
            uri = uri.substring(0, uri.length() - 1);
        }

        String action = null;
        int limit = 0;

        if (uri.startsWith("/exams/") && uri.endsWith("/start")) {
            action = "exam-start";
            limit = examStartLimit;
        } else if (uri.startsWith("/exams/attempt/") && uri.endsWith("/answer")) {
            action = "exam-answer";
            limit = examAnswerLimit;
        } else if (uri.startsWith("/exams/attempt/") && uri.endsWith("/finish")) {
            action = "exam-finish";
            limit = examFinishLimit;
        } else if (uri.startsWith("/grammar/") && uri.endsWith("/quiz/check")) {
            action = "grammar-quiz";
            limit = grammarQuizLimit;
        } else if (uri.equals("/history/delete-all")
                || uri.equals("/history/exams/delete-all")
                || (uri.startsWith("/history/exams/") && uri.endsWith("/delete"))) {
            action = "history-delete";
            limit = historyDeleteLimit;
        }

        if (action == null || rateLimiterService == null) {
            return true;
        }

        String clientIp = (clientIpResolver != null)
                ? clientIpResolver.resolveClientIp(request)
                : (request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1");

        String rateLimitKey = action + ":" + clientIp;

        if (!rateLimiterService.tryAcquire(rateLimitKey, limit)) {
            log.warn("Rate limit exceeded for action [{}] from IP [{}] (limit: {}/min)", action, clientIp, limit);
            throw new RateLimitExceededException("Bạn đã thực hiện thao tác quá nhiều lần. Vui lòng thử lại sau ít phút.");
        }

        return true;
    }

    public int getExamStartLimit() {
        return examStartLimit;
    }

    public void setExamStartLimit(int examStartLimit) {
        this.examStartLimit = examStartLimit;
    }

    public int getExamAnswerLimit() {
        return examAnswerLimit;
    }

    public void setExamAnswerLimit(int examAnswerLimit) {
        this.examAnswerLimit = examAnswerLimit;
    }

    public int getExamFinishLimit() {
        return examFinishLimit;
    }

    public void setExamFinishLimit(int examFinishLimit) {
        this.examFinishLimit = examFinishLimit;
    }

    public int getGrammarQuizLimit() {
        return grammarQuizLimit;
    }

    public void setGrammarQuizLimit(int grammarQuizLimit) {
        this.grammarQuizLimit = grammarQuizLimit;
    }

    public int getHistoryDeleteLimit() {
        return historyDeleteLimit;
    }

    public void setHistoryDeleteLimit(int historyDeleteLimit) {
        this.historyDeleteLimit = historyDeleteLimit;
    }
}
