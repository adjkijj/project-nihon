package com.nihon.dto;

import com.nihon.entity.Topic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO đại diện cho một hàng từ vựng duy nhất trên bảng danh sách,
 * tổng hợp tất cả các chủ đề mà từ vựng này xuất hiện.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VocabularyRowDto {

    private Long id;
    private String japaneseText;
    private String reading;
    private String romaji;
    private String meaningVi;
    private boolean hasKanji;

    @Builder.Default
    private List<Topic> topics = new ArrayList<>();

    @Builder.Default
    private List<Long> allIds = new ArrayList<>();

    /**
     * Chuỗi danh sách chủ đề cách nhau bởi dấu phẩy (ví dụ: "Chủ đề A, Chủ đề B").
     */
    public String getTopicNamesDisplay() {
        if (topics == null || topics.isEmpty()) {
            return "";
        }
        return topics.stream()
                .map(Topic::getName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Kiểm tra từ có thuộc từ 2 chủ đề trở lên hay không.
     */
    public boolean hasMultipleTopics() {
        return topics != null && topics.size() > 1;
    }

    /**
     * Giữ tương thích nếu template/code cũ truy cập v.topic.name
     */
    public Topic getTopic() {
        return (topics != null && !topics.isEmpty()) ? topics.get(0) : null;
    }
}
