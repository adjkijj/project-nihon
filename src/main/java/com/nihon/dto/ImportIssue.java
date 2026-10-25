package com.nihon.dto;

/**
 * Một lỗi/xung đột khi nhập dữ liệu. Chỉ chứa số dòng nguồn và mô tả,
 * không chứa nội dung bản ghi để tránh ghi dữ liệu ra log/giao diện ngoài ý muốn.
 */
public record ImportIssue(int line, String message) {
}
