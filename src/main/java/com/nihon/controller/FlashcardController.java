package com.nihon.controller;

import com.nihon.dto.VocabularyRowDto;
import com.nihon.entity.Topic;
import com.nihon.service.TopicService;
import com.nihon.service.VocabularyService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Controller phục vụ tính năng Học từ vựng bằng thẻ lật (Flashcards).
 * Hỗ trợ lọc theo chủ đề, đổi mặt trước (JA ⇄ VI), xáo trộn có seed lưu trong phiên
 * và phân trang cố định 50 thẻ/trang.
 */
@Controller
@RequestMapping("/flashcards")
public class FlashcardController {

    public static final int PAGE_SIZE = 50;
    private static final int MAX_PAGE = 100000;

    private final VocabularyService vocabularyService;
    private final TopicService topicService;

    public FlashcardController(VocabularyService vocabularyService, TopicService topicService) {
        this.vocabularyService = vocabularyService;
        this.topicService = topicService;
    }

    @GetMapping
    public String index(@RequestParam(value = "topicId", required = false) Long topicId,
                        @RequestParam(value = "shuffle", required = false) String shuffle,
                        @RequestParam(value = "newSeed", required = false) String newSeed,
                        @RequestParam(value = "side", required = false) String side,
                        @RequestParam(value = "page", required = false) String page,
                        HttpSession session,
                        Model model) {

        // 1. Chuẩn hóa tham số mặt trước (side): whitelist JA hoặc VI, mặc định JA
        String safeSide = "JA";
        if (side != null && "VI".equalsIgnoreCase(side.trim())) {
            safeSide = "VI";
        }

        // 2. Chuẩn hóa tham số phân trang an toàn (0 <= page <= 100000)
        int safePage = 0;
        if (page != null) {
            try {
                safePage = Math.max(0, Math.min(MAX_PAGE, Integer.parseInt(page.trim())));
            } catch (NumberFormatException ignored) {
                safePage = 0;
            }
        }

        // 3. Chuẩn hóa tham số xáo trộn (shuffle & newSeed)
        boolean isShuffle = "true".equalsIgnoreCase(shuffle != null ? shuffle.trim() : null);
        boolean isNewSeed = "true".equalsIgnoreCase(newSeed != null ? newSeed.trim() : null);

        // 4. Lấy danh sách tất cả chủ đề để đưa vào dropdown bộ chọn
        List<Topic> topics = topicService.findAllOrdered();
        model.addAttribute("topics", topics);

        // 5. Kiểm tra tính hợp lệ của topicId
        Long effectiveTopicId = topicId;
        String selectedTopicName = null;
        boolean topicExists = false;

        if (topicId != null) {
            if (topicId <= 0) {
                model.addAttribute("errorMessage", "ID chủ đề không hợp lệ.");
                effectiveTopicId = null;
            } else {
                Topic topic = topicService.findById(topicId).orElse(null);
                if (topic == null) {
                    model.addAttribute("errorMessage", "Chủ đề không tồn tại.");
                    effectiveTopicId = null;
                } else {
                    topicExists = true;
                    selectedTopicName = topic.getName();
                }
            }
        }

        // 6. Tải dữ liệu từ vựng theo cùng logic gộp nhóm của trang danh sách
        List<VocabularyRowDto> allMatching;
        if (topicId != null && !topicExists) {
            allMatching = Collections.emptyList();
        } else {
            allMatching = vocabularyService.findAllMatching(effectiveTopicId, null);
        }

        // 7. Lọc bỏ các từ chưa có nghĩa tiếng Việt và đếm số lượng bị bỏ qua
        List<VocabularyRowDto> validCards = new ArrayList<>();
        int skippedCount = 0;
        for (VocabularyRowDto v : allMatching) {
            if (v.getMeaningVi() == null || v.getMeaningVi().isBlank()) {
                skippedCount++;
            } else {
                validCards.add(v);
            }
        }

        // 8. Xử lý xáo trộn ổn định với seed lưu trong session
        Long seed = null;
        if (isShuffle && !validCards.isEmpty()) {
            String sessionKey = "FLASHCARD_SEED_" + (effectiveTopicId != null ? effectiveTopicId : "ALL");
            seed = (Long) session.getAttribute(sessionKey);
            if (seed == null || isNewSeed) {
                seed = System.currentTimeMillis();
                session.setAttribute(sessionKey, seed);
            }
            List<VocabularyRowDto> shuffled = new ArrayList<>(validCards);
            Collections.shuffle(shuffled, new Random(seed));
            validCards = shuffled;
        }

        // 9. Phân trang cố định 50 thẻ/trang
        int totalElements = validCards.size();
        int totalPages = (totalElements == 0) ? 0 : (int) Math.ceil((double) totalElements / (double) PAGE_SIZE);

        List<VocabularyRowDto> pageCards;
        long fromIndexLong = (long) safePage * (long) PAGE_SIZE;
        if (fromIndexLong >= totalElements || totalElements == 0) {
            pageCards = Collections.emptyList();
        } else {
            int fromIndex = (int) fromIndexLong;
            int toIndex = Math.min(fromIndex + PAGE_SIZE, totalElements);
            pageCards = validCards.subList(fromIndex, toIndex);
        }

        // 10. Truyền dữ liệu sang View Thymeleaf
        model.addAttribute("cards", pageCards);
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("skippedCount", skippedCount);
        model.addAttribute("selectedTopicId", effectiveTopicId);
        model.addAttribute("selectedTopicName", selectedTopicName);
        model.addAttribute("side", safeSide);
        model.addAttribute("shuffle", isShuffle);
        model.addAttribute("seed", seed);
        model.addAttribute("activeNav", "flashcards");

        return "flashcards/index";
    }
}

