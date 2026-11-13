package com.nihon.entity;

/**
 * Danh mục phân loại cho Ngữ pháp và Kiến thức bổ sung (F30).
 */
public enum GrammarCategory {
    GRAMMAR("Ngữ pháp"),
    SUPPLEMENT("Kiến thức bổ sung");

    private final String displayName;

    GrammarCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static boolean isValid(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        for (GrammarCategory cat : values()) {
            if (cat.name().equalsIgnoreCase(name.trim())) {
                return true;
            }
        }
        return false;
    }

    public static GrammarCategory fromString(String name) {
        if (name == null || name.isBlank()) {
            return GRAMMAR;
        }
        for (GrammarCategory cat : values()) {
            if (cat.name().equalsIgnoreCase(name.trim())) {
                return cat;
            }
        }
        throw new IllegalArgumentException("Loại ngữ pháp không hợp lệ: " + name);
    }
}

