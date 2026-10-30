package com.nihon.service;

import com.nihon.entity.KanjiEntry;
import com.nihon.entity.KanjiWord;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.KanjiWordRepository;
import com.nihon.repository.VocabularyRepository;
import com.nihon.service.excel.ImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Dịch vụ di chuyển dữ liệu một lần từ bảng cũ kanji_word sang bảng từ vựng chung vocabulary.
 * Hoạt động hoàn toàn idempotent (an toàn khi chạy lại nhiều lần, không sinh bản ghi trùng).
 */
@Service
public class KanjiWordMigrationService {

    private static final Logger log = LoggerFactory.getLogger(KanjiWordMigrationService.class);

    private final KanjiWordRepository kanjiWordRepository;
    private final VocabularyRepository vocabularyRepository;

    public KanjiWordMigrationService(KanjiWordRepository kanjiWordRepository,
                                     VocabularyRepository vocabularyRepository) {
        this.kanjiWordRepository = kanjiWordRepository;
        this.vocabularyRepository = vocabularyRepository;
    }

    /**
     * Chuyển toàn bộ các từ trong kanji_word chưa tồn tại trong vocabulary sang bảng vocabulary.
     *
     * @return số lượng bản ghi từ vựng mới được chuyển đổi thành công
     */
    @Transactional
    public int migrateAll() {
        List<KanjiWord> words = kanjiWordRepository.findAll();
        if (words.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (KanjiWord kw : words) {
            KanjiEntry kanji = kw.getKanji();
            if (kanji == null || kanji.getTopic() == null) {
                continue;
            }
            Long topicId = kanji.getTopic().getId();
            String japanese = kw.getJapanese() != null ? kw.getJapanese().trim() : "";
            if (japanese.isEmpty()) {
                continue;
            }

            if (!vocabularyRepository.existsByTopicIdAndJapaneseText(topicId, japanese)) {
                Vocabulary v = new Vocabulary();
                v.setTopic(kanji.getTopic());
                v.setJapaneseText(japanese);
                v.setReading(ImportService.extractReading(japanese));
                v.setRomaji(kw.getRomaji() != null && !kw.getRomaji().isBlank() ? kw.getRomaji().trim() : null);
                v.setMeaningVi(kw.getMeaningVi() != null && !kw.getMeaningVi().isBlank() ? kw.getMeaningVi().trim() : null);
                v.setHasKanji(ImportService.containsKanji(japanese));
                v.setSourceLine(null);
                vocabularyRepository.save(v);
                count++;
            }
        }

        if (count > 0) {
            log.info("Migrated {} kanji_word records to common vocabulary table.", count);
        }
        return count;
    }
}
