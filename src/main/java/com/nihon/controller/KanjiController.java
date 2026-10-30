package com.nihon.controller;

import com.nihon.dto.KanjiDetailDto;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.service.KanjiService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/kanji")
public class KanjiController {

    private static final int DEFAULT_PAGE_SIZE = 24;
    private static final int MAX_PAGE_SIZE = 100;

    private final KanjiService kanjiService;

    public KanjiController(KanjiService kanjiService) {
        this.kanjiService = kanjiService;
    }

    /**
     * Trang danh sách Kanji riêng biệt: hiển thị thẻ/lưới Kanji, tìm kiếm đa tiêu chí và lọc theo chủ đề bài học.
     */
    @GetMapping
    public String list(@RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "topicId", required = false) Long topicId,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       @RequestParam(value = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
                       Model model) {
        if (page < 0) {
            page = 0;
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            size = DEFAULT_PAGE_SIZE;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("sourceLine").ascending());
        Page<KanjiDetailDto> kanjiPage = kanjiService.search(topicId, keyword, pageable);
        List<Topic> topics = kanjiService.getKanjiTopics();

        model.addAttribute("kanjiPage", kanjiPage);
        model.addAttribute("topics", topics);
        model.addAttribute("selectedTopicId", topicId);
        model.addAttribute("keyword", keyword != null ? keyword.strip() : "");
        model.addAttribute("totalCount", kanjiService.countTotal());
        model.addAttribute("activeNav", "kanji");

        return "kanji/list";
    }

    /**
     * Mở xem chi tiết một ký tự Kanji riêng biệt.
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        Optional<KanjiDetailDto> kanjiOpt = kanjiService.findDetailById(id);
        if (kanjiOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy Kanji với mã ID yêu cầu.");
            return "redirect:/kanji";
        }

        KanjiDetailDto kanji = kanjiOpt.get();
        List<Vocabulary> relatedVocabularies = kanjiService.getRelatedVocabulary(kanji.getCharacter(), kanji.getTopicId());

        model.addAttribute("kanji", kanji);
        model.addAttribute("relatedVocabularies", relatedVocabularies);
        model.addAttribute("activeNav", "kanji");

        return "kanji/detail";
    }
}
