package com.nihon.dto;

import com.nihon.entity.Topic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * DTO chứa thông tin chi tiết của một chủ đề để hiển thị trên trang GET /topics/{id}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicDetailDto {

    private Long id;
    private String name;
    private Long parentId;
    private String parentName;
    private boolean kanjiSection;
    private Integer sortOrder;
    private long vocabularyCount;
    private long kanjiCount;
    private long verbCount;

    @Builder.Default
    private List<com.nihon.dto.verb.VerbDetailDto> verbs = Collections.emptyList();

    @Builder.Default
    private List<Topic> children = Collections.emptyList();

    private boolean canDelete;
    private String cannotDeleteReason;
}

