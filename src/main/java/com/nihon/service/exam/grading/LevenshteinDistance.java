package com.nihon.service.exam.grading;

/**
 * Tiện ích tính khoảng cách Levenshtein và độ tương đồng giữa hai chuỗi văn bản (F44).
 * Cài đặt thuần Java, không phụ thuộc thư viện bên ngoài.
 * Độ phức tạp thời gian: O(M * N), không gian bộ nhớ: O(N).
 */
public final class LevenshteinDistance {

    private LevenshteinDistance() {
    }

    /**
     * Tính khoảng cách Levenshtein giữa hai chuỗi ký tự.
     * Trả về số lượng thao tác tối thiểu (chèn, xóa, thay thế ký tự) để biến chuỗi s1 thành s2.
     *
     * @param s1 Chuỗi thứ nhất
     * @param s2 Chuỗi thứ hai
     * @return Khoảng cách Levenshtein (không âm)
     */
    public static int computeDistance(String s1, String s2) {
        if (s1 == null) {
            s1 = "";
        }
        if (s2 == null) {
            s2 = "";
        }

        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0) {
            return len2;
        }
        if (len2 == 0) {
            return len1;
        }

        // Tối ưu bộ nhớ O(N) bằng cách chỉ lưu trữ 2 hàng
        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];

        for (int j = 0; j <= len2; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= len2; j++) {
                char c2 = s2.charAt(j - 1);
                int cost = (c1 == c2) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            System.arraycopy(curr, 0, prev, 0, len2 + 1);
        }

        return prev[len2];
    }

    /**
     * Tính độ tương đồng chuẩn hóa giữa hai chuỗi trong khoảng từ 0.0 đến 1.0 (100%).
     * Công thức: Similarity = 1.0 - (LevenshteinDistance / max(len1, len2)).
     *
     * @param s1 Chuỗi thứ nhất
     * @param s2 Chuỗi thứ hai
     * @return Tỷ lệ tương đồng từ 0.0 đến 1.0
     */
    public static double computeSimilarity(String s1, String s2) {
        if (s1 == null) {
            s1 = "";
        }
        if (s2 == null) {
            s2 = "";
        }

        if (s1.equals(s2)) {
            return 1.0;
        }

        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 1.0;
        }

        int distance = computeDistance(s1, s2);
        return Math.max(0.0, 1.0 - ((double) distance / maxLen));
    }
}

