package com.nihon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO chứa thông tin thống kê số lượng từ vựng của từng chủ đề.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicStatDto {

    private Long id;
    private String name;
    private String parentName;
    private long vocabCount;
    private boolean kanjiSection;
}
