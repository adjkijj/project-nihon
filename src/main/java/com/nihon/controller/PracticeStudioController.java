package com.nihon.controller;

import com.nihon.dto.KanjiPracticeMode;
import com.nihon.dto.TopicGroupDto;
import com.nihon.entity.Topic;
import com.nihon.service.KanjiService;
import com.nihon.service.StudyProgressService;
import com.nihon.service.TopicService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Controller cho Tổ hợp Luyện gõ Tiếng Nhật Đa chế độ - Typing Studio (F53).
 * Tích hợp thanh chuyển chế độ trên cùng một giao diện, gọi lại các màn hình:
 * - /practice (Nhật → Việt)
 * - /practice/vi-ja (Việt → Nhật)
 * - /practice/kanji (Luyện chữ Hán Kanji)
 * Giữ nguyên các URL cũ và không lặp lại logic chấm điểm.
 */
@Controller
@RequestMapping("/practice/studio")
public class PracticeStudioController {

    private final TopicService topicService;
    private final KanjiService kanjiService;
    private final StudyProgressService studyProgressService;

    public PracticeStudioController(TopicService topicService,
                                    KanjiService kanjiService,
                                    StudyProgressService studyProgressService) {
        this.topicService = topicService;
        this.kanjiService = kanjiService;
        this.studyProgressService = studyProgressService;
    }

    /**
     * Hiển thị giao diện Typing Studio với thanh chuyển chế độ.
     *
     * @param modeStr      Chế độ luyện gõ (ja-vi, vi-ja, kanji)
     * @param topicId      ID chủ đề bài học (nếu có)
     * @param kanjiModeStr Chế độ Kanji (KANJI_TO_READING, MEANING_TO_KANJI, KANJI_CHOICE)
     * @param session      Phiên làm việc người học (chứa thống kê câu sai)
     * @param model        Spring UI Model
     * @return Tên view template practice/studio
     */
    @GetMapping
    public String studio(@RequestParam(value = "mode", required = false, defaultValue = "ja-vi") String modeStr,
                         @RequestParam(value = "topicId", required = false) Long topicId,
                         @RequestParam(value = "kanjiMode", required = false, defaultValue = "KANJI_TO_READING") String kanjiModeStr,
                         HttpSession session,
                         Model model) {
        String studioMode = normalizeStudioMode(modeStr);
        KanjiPracticeMode kanjiMode = KanjiPracticeMode.fromString(kanjiModeStr);

        // 1. Thẩm định topicId
        Long effectiveTopicId = topicId;
        if (topicId != null && topicId <= 0) {
            effectiveTopicId = null;
            model.addAttribute("errorMessage", "ID không hợp lệ");
        }

        // 2. Nạp dữ liệu danh sách chủ đề cho từng chế độ
        List<Topic> topics = topicService.findAllOrdered();
        if (topics == null) {
            topics = Collections.emptyList();
        }

        List<TopicGroupDto> kanjiTopicGroups = kanjiService.getKanjiTopicGroups();
        if (kanjiTopicGroups == null) {
            kanjiTopicGroups = Collections.emptyList();
        }

        // 3. Đếm số câu/từ làm sai trong phiên làm việc cho từng chế độ
        int jaViWrongCount = studyProgressService.getWrongVocabIds(session, "JA_VI", effectiveTopicId).size();
        int viJaWrongCount = studyProgressService.getWrongVocabIds(session, "VI_JA", effectiveTopicId).size();
        int kanjiWrongCount = studyProgressService.getWrongKanjiIds(session, kanjiMode.getStudyProgressMode(), effectiveTopicId).size();

        int currentWrongCount = switch (studioMode) {
            case "vi-ja" -> viJaWrongCount;
            case "kanji" -> kanjiWrongCount;
            default -> jaViWrongCount;
        };

        // 4. Bổ sung các thuộc tính vào Model để các fragment con gọi lại chính xác
        model.addAttribute("studioMode", studioMode);
        model.addAttribute("selectedMode", kanjiMode.name());
        model.addAttribute("selectedTopicId", effectiveTopicId);
        model.addAttribute("topics", topics);
        model.addAttribute("topicGroups", kanjiTopicGroups);
        model.addAttribute("wrongCount", currentWrongCount);
        model.addAttribute("jaViWrongCount", jaViWrongCount);
        model.addAttribute("viJaWrongCount", viJaWrongCount);
        model.addAttribute("kanjiWrongCount", kanjiWrongCount);
        model.addAttribute("activeNav", "practice");

        return "practice/studio";
    }

    /**
     * Chuẩn hóa mã chế độ Studio đầu vào.
     */
    private String normalizeStudioMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "ja-vi";
        }
        String normalized = mode.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "vi-ja", "vi_ja", "vija", "vietnamese", "vi" -> "vi-ja";
            case "kanji", "han" -> "kanji";
            default -> "ja-vi";
        };
    }
}

