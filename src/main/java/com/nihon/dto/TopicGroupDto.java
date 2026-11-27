package com.nihon.dto;

import com.nihon.entity.Topic;

import java.util.List;

/**
 * DTO nhóm các chủ đề con theo chủ đề cha, phục vụ hiển thị optgroup trên dropdown.
 */
public record TopicGroupDto(String parentName, List<Topic> children) {
}

