package com.nihon.repository;

import com.nihon.entity.GrammarQuizChoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GrammarQuizChoiceRepository extends JpaRepository<GrammarQuizChoice, Long> {

    List<GrammarQuizChoice> findByQuestionIdOrderBySortOrderAsc(Long questionId);
}

