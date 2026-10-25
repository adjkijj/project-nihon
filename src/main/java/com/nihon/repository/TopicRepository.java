package com.nihon.repository;

import com.nihon.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findByParentIsNullOrderBySortOrderAsc();

    List<Topic> findByParentIdOrderBySortOrderAsc(Long parentId);

    List<Topic> findByKanjiSectionTrueOrderBySortOrderAsc();

    Optional<Topic> findBySourceLine(Integer sourceLine);

    List<Topic> findAllByOrderBySortOrderAsc();

    boolean existsByParentId(Long parentId);
}
