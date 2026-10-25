package com.nihon.service;

import com.nihon.dto.KanjiDetailDto;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KanjiServiceTest {

    @Mock
    private KanjiEntryRepository kanjiEntryRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private VocabularyRepository vocabularyRepository;

    @InjectMocks
    private KanjiService kanjiService;

    private KanjiEntry sampleKanji;
    private Topic sampleTopic;

    @BeforeEach
    void setUp() {
        sampleTopic = new Topic();
        sampleTopic.setId(10L);
        sampleTopic.setName("Kanji Lesson 5");

        sampleKanji = new KanjiEntry();
        sampleKanji.setId(1L);
        sampleKanji.setCharacter("水");
        sampleKanji.setHanViet("THỦY");
        sampleKanji.setMeaningVi("Nước");
        sampleKanji.setOnyomi("スイ");
        sampleKanji.setKunyomi("みず");
        sampleKanji.setTopic(sampleTopic);
        sampleKanji.setSourceLine(1247);
    }

    @Test
    @DisplayName("Tìm kiếm với từ khóa hợp lệ được cắt tỉa khoảng trắng")
    void testSearchWithKeyword() {
        Pageable pageable = PageRequest.of(0, 10);
        when(kanjiEntryRepository.search(eq(10L), eq("THỦY"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(sampleKanji)));

        Page<KanjiDetailDto> result = kanjiService.search(10L, "  THỦY  ", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCharacter()).isEqualTo("水");
        verify(kanjiEntryRepository).search(eq(10L), eq("THỦY"), eq(pageable));
    }

    @Test
    @DisplayName("Từ khóa rỗng hoặc chỉ có khoảng trắng được chuẩn hóa thành null")
    void testSearchWithBlankKeyword() {
        Pageable pageable = PageRequest.of(0, 10);
        when(kanjiEntryRepository.search(isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(sampleKanji)));

        Page<KanjiDetailDto> result = kanjiService.search(null, "   ", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(kanjiEntryRepository).search(isNull(), isNull(), eq(pageable));
    }

    @Test
    @DisplayName("Từ khóa quá dài (> 50 ký tự) tự động bị cắt ngắn an toàn")
    void testSearchWithLongKeywordTruncated() {
        Pageable pageable = PageRequest.of(0, 10);
        String veryLongKeyword = "a".repeat(80);
        String expectedTruncated = "a".repeat(50);

        when(kanjiEntryRepository.search(isNull(), eq(expectedTruncated), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        kanjiService.search(null, veryLongKeyword, pageable);

        verify(kanjiEntryRepository).search(isNull(), eq(expectedTruncated), eq(pageable));
    }

    @Test
    @DisplayName("Tìm kiếm chi tiết theo ID không hợp lệ trả về Optional.empty")
    void testFindDetailByIdInvalid() {
        assertThat(kanjiService.findDetailById(null)).isEmpty();
        assertThat(kanjiService.findDetailById(0L)).isEmpty();
        assertThat(kanjiService.findDetailById(-5L)).isEmpty();
    }

    @Test
    @DisplayName("Tìm kiếm chi tiết theo ID hợp lệ trả về DTO")
    void testFindDetailByIdSuccess() {
        when(kanjiEntryRepository.findById(1L)).thenReturn(Optional.of(sampleKanji));

        Optional<KanjiDetailDto> result = kanjiService.findDetailById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getCharacter()).isEqualTo("水");
        assertThat(result.get().getMeaningVi()).isEqualTo("Nước");
    }

    @Test
    @DisplayName("Lấy từ vựng liên quan trả về danh sách từ vựng chứa Kanji")
    void testGetRelatedVocabulary() {
        Vocabulary vocab = new Vocabulary();
        vocab.setJapaneseText("水よう日（すいようび）");
        when(vocabularyRepository.findTop10ByJapaneseTextContainingOrderBySourceLineAsc("水"))
                .thenReturn(List.of(vocab));

        List<Vocabulary> list = kanjiService.getRelatedVocabulary(" 水 ");

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getJapaneseText()).isEqualTo("水よう日（すいようび）");
    }

    @Test
    @DisplayName("Lấy từ vựng liên quan với chuỗi rỗng trả về danh sách rỗng")
    void testGetRelatedVocabularyBlank() {
        assertThat(kanjiService.getRelatedVocabulary("")).isEmpty();
        assertThat(kanjiService.getRelatedVocabulary(null)).isEmpty();
        assertThat(kanjiService.getRelatedVocabulary("   ")).isEmpty();
    }
}
