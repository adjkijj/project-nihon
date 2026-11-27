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

import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
        String safeMode = normalizeMode(mode);

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

        // 2. Kiểm tra bộ lọc topicId và thống kê tiến độ học tập cá nhân trong phiên làm việc
        Long effectiveTopicId = topicId;
        if (topicId != null && topicId <= 0) {
            effectiveTopicId = null;
            model.addAttribute("errorMessage", "ID không hợp lệ");
        } else if (topicId != null) {
            boolean topicExists = allTopics.stream().anyMatch(t -> topicId.equals(t.getId()))
                    || topicRepository.existsById(topicId);
            if (!topicExists) {
                model.addAttribute("errorMessage", "Chủ đề không tồn tại");
                StudyProgressSummaryDto emptyProgress = StudyProgressSummaryDto.builder()
                        .totalAnswered(0)
                        .correctCount(0)
                        .incorrectCount(0)
                        .accuracyPercentage(0)
                        .records(Collections.emptyList())
                        .incorrectRecords(Collections.emptyList())
                        .currentModeFilter(safeMode)
                        .currentTopicFilter(topicId)
                        .build();
                model.addAttribute("progress", emptyProgress);
                model.addAttribute("selectedMode", safeMode);
                model.addAttribute("selectedTopicId", topicId);
                model.addAttribute("wrongCount", 0);
                model.addAttribute("activeNav", "stats");
                return "stats";
            }
        }

        StudyProgressSummaryDto progress = studyProgressService.getSummary(session, safeMode, effectiveTopicId);
        if (progress == null) {
            progress = StudyProgressSummaryDto.builder()
                    .totalAnswered(0)
                    .correctCount(0)
                    .incorrectCount(0)
                    .accuracyPercentage(0)
                    .records(Collections.emptyList())
                    .incorrectRecords(Collections.emptyList())
                    .currentModeFilter(safeMode)
                    .currentTopicFilter(effectiveTopicId)
                    .build();
        }
        int wrongCount;
        if (safeMode.startsWith("KANJI_") || "TIMED_KANJI_READING".equals(safeMode)) {
            wrongCount = studyProgressService.getWrongKanjiIds(session, safeMode, effectiveTopicId).size();
        } else if (safeMode.startsWith("VERB_") || "TIMED_VERB_TE".equals(safeMode)) {
            wrongCount = studyProgressService.getWrongVerbIds(session, safeMode, effectiveTopicId).size();
        } else {
            wrongCount = studyProgressService.getWrongVocabIds(session, safeMode, effectiveTopicId).size();
        }

        model.addAttribute("progress", progress);
        model.addAttribute("selectedMode", safeMode);
        model.addAttribute("selectedTopicId", effectiveTopicId);
        model.addAttribute("wrongCount", wrongCount);
        model.addAttribute("activeNav", "stats");

        return "stats";
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "ALL";
        }
        String trimmedUpper = mode.strip().toUpperCase(Locale.ROOT);
        if ("JA_VI".equals(trimmedUpper) || "VI_JA".equals(trimmedUpper)
                || "MC_JA_VI".equals(trimmedUpper) || "MC_VI_JA".equals(trimmedUpper)
                || "KANJI_READING".equals(trimmedUpper) || "KANJI_WRITE".equals(trimmedUpper)
                || "KANJI_CHOICE".equals(trimmedUpper)
                || "TIMED_JA_VI".equals(trimmedUpper) || "TIMED_VI_JA".equals(trimmedUpper)
                || "TIMED_KANJI_READING".equals(trimmedUpper) || "TIMED_CHOICE".equals(trimmedUpper)
                || "TIMED_VERB_TE".equals(trimmedUpper)
                || "VERB_TE".equals(trimmedUpper) || "VERB_RU".equals(trimmedUpper)
                || "VERB_TA".equals(trimmedUpper) || "VERB_NAI".equals(trimmedUpper)
                || "VERB_MASU".equals(trimmedUpper)
                || "ALL".equals(trimmedUpper)) {
            return trimmedUpper;
        }
        return "ALL";
    }

    @PostMapping("/reset")
    public String resetProgress(HttpSession session, RedirectAttributes redirectAttributes) {
        studyProgressService.clearProgress(session);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa toàn bộ lịch sử luyện tập của phiên hiện tại. Bạn có thể bắt đầu lại từ đầu!");
        return "redirect:/stats";
    }
}
