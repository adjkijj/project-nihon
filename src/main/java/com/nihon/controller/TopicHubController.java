package com.nihon.controller;

import com.nihon.dto.topic.TopicHubDto;
import com.nihon.service.TopicHubService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controller cho Trang Cẩm nang Bài học & Chuyên đề All-in-One Hub (F12).
 * Gom trên một trang: từ vựng, Kanji, ngữ pháp, động từ, đề thi và các liên kết flashcard / quiz.
 */
@Controller
public class TopicHubController {

    private final TopicHubService topicHubService;

    public TopicHubController(TopicHubService topicHubService) {
        this.topicHubService = topicHubService;
    }

    /**
     * Hiển thị trang cẩm nang tổng hợp của một chủ đề bài học.
     * Hỗ trợ 2 alias đường dẫn: /topics/{topicId}/hub và /lessons/{topicId}.
     *
     * @param topicId ID chủ đề bài học
     * @param model   Spring UI Model
     * @return Tên view template topics/hub
     */
    @GetMapping({"/topics/{topicId}/hub", "/lessons/{topicId}"})
    public String topicHub(@PathVariable("topicId") Long topicId, Model model) {
        if (topicId == null || topicId <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "topicId không hợp lệ: " + topicId);
        }

        TopicHubDto hub = topicHubService.getTopicHubData(topicId);

        model.addAttribute("hub", hub);
        model.addAttribute("topic", hub.getTopic());
        model.addAttribute("vocabularyList", hub.getVocabularyList());
        model.addAttribute("kanjiList", hub.getKanjiList());
        model.addAttribute("grammarList", hub.getGrammarList());
        model.addAttribute("verbList", hub.getVerbList());
        model.addAttribute("examList", hub.getExamList());

        return "topics/hub";
    }
}

