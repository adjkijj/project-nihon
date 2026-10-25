package com.nihon.controller;

import com.nihon.dto.StudyProgressSummaryDto;
import com.nihon.dto.TopicStatDto;
import com.nihon.entity.Topic;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import com.nihon.service.StudyProgressService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/stats")
public class StatsController {

    private final VocabularyRepository vocabularyRepository;
    private final TopicRepository topicRepository;
    private final KanjiEntryRepository kanjiEntryRepository;
    private final StudyProgressService studyProgressService;

    public StatsController(VocabularyRepository vocabularyRepository,
                           TopicRepository topicRepository,
                           KanjiEntryRepository kanjiEntryRepository,
                           StudyProgressService studyProgressService) {
        this.vocabularyRepository = vocabularyRepository;
        this.topicRepository = topicRepository;
        this.kanjiEntryRepository = kanjiEntryRepository;
        this.studyProgressService = studyProgressService;
    }

    @GetMapping
    public String index(@RequestParam(value = "mode", required = false, defaultValue = "ALL") String mode,
                        @RequestParam(value = "topicId", required = false) Long topicId,
                        HttpSession session,
                        Model model) {
        // 1. Thống kê kho dữ liệu học liệu chung
        long totalVocab = vocabularyRepository.count();
        long totalTopics = topicRepository.count();
        long totalKanji = kanjiEntryRepository.count();
        long vocabWithKanjiCount = vocabularyRepository.countByHasKanjiTrue();
        long vocabKanaOnlyCount = vocabularyRepository.countByHasKanjiFalse();

        int kanjiPercentage = totalVocab > 0 ? (int) Math.round((double) vocabWithKanjiCount * 100.0 / totalVocab) : 0;
        int kanaPercentage = totalVocab > 0 ? 100 - kanjiPercentage : 0;

        List<Topic> allTopics = topicRepository.findAllByOrderBySortOrderAsc();
        List<TopicStatDto> topicStats = allTopics.stream()
                .map(t -> TopicStatDto.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .parentName(t.getParent() != null ? t.getParent().getName() : null)
                        .vocabCount(vocabularyRepository.countByTopicId(t.getId()))
                        .kanjiSection(t.isKanjiSection())
                        .build())
                .toList();

        model.addAttribute("totalVocab", totalVocab);
        model.addAttribute("totalTopics", totalTopics);
        model.addAttribute("totalKanji", totalKanji);
        model.addAttribute("vocabWithKanjiCount", vocabWithKanjiCount);
        model.addAttribute("vocabKanaOnlyCount", vocabKanaOnlyCount);
        model.addAttribute("kanjiPercentage", kanjiPercentage);
        model.addAttribute("kanaPercentage", kanaPercentage);
        model.addAttribute("topicStats", topicStats);
        model.addAttribute("topics", allTopics);

        // 2. Thống kê tiến độ học tập cá nhân trong phiên làm việc
        StudyProgressSummaryDto progress = studyProgressService.getSummary(session, mode, topicId);
        model.addAttribute("progress", progress);
        model.addAttribute("selectedMode", mode);
        model.addAttribute("selectedTopicId", topicId);
        model.addAttribute("activeNav", "stats");

        return "stats";
    }

    @PostMapping("/reset")
    public String resetProgress(HttpSession session, RedirectAttributes redirectAttributes) {
        studyProgressService.clearProgress(session);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa toàn bộ lịch sử luyện tập của phiên hiện tại. Bạn có thể bắt đầu lại từ đầu!");
        return "redirect:/stats";
    }
}
