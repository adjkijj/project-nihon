package com.nihon;

import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProjectNihonApplicationTests {

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private KanjiEntryRepository kanjiEntryRepository;

    @Test
    void contextLoadsAndDataIsInitialized() {
        assertThat(topicRepository.count()).isGreaterThanOrEqualTo(77);
        assertThat(vocabularyRepository.count()).isGreaterThan(0);
        assertThat(kanjiEntryRepository.count()).isEqualTo(126);
    }
}
