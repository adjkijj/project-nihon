package com.nihon.repository;

import com.nihon.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findByParentIsNullOrderBySortOrderAsc();

    List<Topic> findByParentIdOrderBySortOrderAsc(Long parentId);

    List<Topic> findByKanjiSectionTrueOrderBySortOrderAsc();

    Optional<Topic> findBySourceLine(Integer sourceLine);

    List<Topic> findAllByOrderBySortOrderAsc();

    boolean existsByParentId(Long parentId);

    long countByParentId(Long parentId);

    @Query("SELECT t.parent.id, COUNT(t) FROM Topic t WHERE t.parent.id IN :parentIds GROUP BY t.parent.id")
    List<Object[]> countByParentIdInGrouped(@Param("parentIds") java.util.Collection<Long> parentIds);

    Optional<Topic> findByNameIgnoreCase(String name);

    @Query("SELECT COUNT(t) > 0 FROM Topic t WHERE LOWER(t.name) = LOWER(:name) " +
           "AND ((:parentId IS NULL AND t.parent IS NULL) OR t.parent.id = :parentId) " +
           "AND (:excludeId IS NULL OR t.id <> :excludeId)")
    boolean existsDuplicateNameInParent(@Param("name") String name,
                                        @Param("parentId") Long parentId,
                                        @Param("excludeId") Long excludeId);

    @Query("SELECT COALESCE(MAX(t.sortOrder), 0) FROM Topic t")
    Integer findMaxSortOrder();

    List<Topic> findByImportBatchIdOrderBySortOrderDescIdDesc(Long importBatchId);

    long countByImportBatchId(Long importBatchId);

    @Query("SELECT COUNT(t) > 0 FROM Topic t WHERE t.parent.id = :parentId AND (t.importBatchId IS NULL OR t.importBatchId <> :batchId)")
    boolean existsChildOutsideBatch(@Param("parentId") Long parentId, @Param("batchId") Long batchId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Topic t SET t.importBatchId = :batchId WHERE t.id IN :ids")
    void assignImportBatchId(@Param("ids") List<Long> ids, @Param("batchId") Long batchId);

    @Query("SELECT t FROM Topic t LEFT JOIN FETCH t.parent WHERE t.id = :id")
    Optional<Topic> findByIdWithParent(@Param("id") Long id);
}
