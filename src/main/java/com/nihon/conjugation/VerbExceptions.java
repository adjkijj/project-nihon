package com.nihon.conjugation;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extensible registry and detector for Japanese verb exceptions:
 * 1. いく / 行く (special te/ta forms: いって/行って, いった/行った).
 * 2. ある (special negative: ない).
 * 3. くる / 来る (irregular Group 3: きます/来ます, きて/来て, きた/来た, こない/来ない).
 * 4. する (irregular Group 3: します, して, した, しない).
 * 5. Compound verbs ending with する or くる (e.g. べんきょうする, もってくる, つれてくる).
 * 6. Group 1 verbs that look like Group 2 (end in -iru/-eru but conjugate as godan:
 *    かえる/帰る, はいる/入る, しる/知る, はしる/走る, きる/切る, しゃべる/喋る, etc.).
 *
 * Thread-safe and pure Java (zero Spring dependencies).
 */
public final class VerbExceptions {

    // Group 1 verbs that look like Group 2 (ichidan lookalikes)
    private static final Set<String> GROUP_1_LOOKALIKES = ConcurrentHashMap.newKeySet();

    // Registry of known compound suru verbs (kana/kanji dictionary forms)
    private static final Set<String> COMPOUND_SURU_VERBS = ConcurrentHashMap.newKeySet();

    // Registry of known compound kuru verbs (kana/kanji dictionary forms)
    private static final Set<String> COMPOUND_KURU_VERBS = ConcurrentHashMap.newKeySet();

    // Extensible custom overrides map: key -> ConjugatedForms
    private static final Map<String, ConjugatedForms> CUSTOM_OVERRIDES = new ConcurrentHashMap<>();

    static {
        // Pre-populate Group 1 verbs that end in -iru or -eru
        registerGroup1Lookalike("かえる");
        registerGroup1Lookalike("帰る");
        registerGroup1Lookalike("はいる");
        registerGroup1Lookalike("入る");
        registerGroup1Lookalike("しる");
        registerGroup1Lookalike("知る");
        registerGroup1Lookalike("はしる");
        registerGroup1Lookalike("走る");
        registerGroup1Lookalike("きる");
        registerGroup1Lookalike("切る");
        registerGroup1Lookalike("しゃべる");
        registerGroup1Lookalike("喋る");
        registerGroup1Lookalike("へる");
        registerGroup1Lookalike("減る");
        registerGroup1Lookalike("ける");
        registerGroup1Lookalike("蹴る");
        registerGroup1Lookalike("すべる");
        registerGroup1Lookalike("滑る");
        registerGroup1Lookalike("ちる");
        registerGroup1Lookalike("散る");
        registerGroup1Lookalike("にぎる");
        registerGroup1Lookalike("握る");
        registerGroup1Lookalike("てる");
        registerGroup1Lookalike("照る");
        registerGroup1Lookalike("ねじる");
        registerGroup1Lookalike("捩る");
        registerGroup1Lookalike("まじる");
        registerGroup1Lookalike("混じる");
        registerGroup1Lookalike("いじる");
        registerGroup1Lookalike("弄る");
        registerGroup1Lookalike("かぎる");
        registerGroup1Lookalike("限る");
        registerGroup1Lookalike("うらぎる");
        registerGroup1Lookalike("裏切る");
        registerGroup1Lookalike("あせる");
        registerGroup1Lookalike("焦る");
        registerGroup1Lookalike("いる");
        registerGroup1Lookalike("要る");

        // Pre-populate common compound suru verbs
        registerCompoundSuru("べんきょうする");
        registerCompoundSuru("勉強する");
        registerCompoundSuru("れんしゅうする");
        registerCompoundSuru("練習する");
        registerCompoundSuru("さんぽする");
        registerCompoundSuru("散歩する");
        registerCompoundSuru("りょこうする");
        registerCompoundSuru("旅行する");
        registerCompoundSuru("そうじする");
        registerCompoundSuru("掃除する");
        registerCompoundSuru("うんどうする");
        registerCompoundSuru("運動する");
        registerCompoundSuru("しつもんする");
        registerCompoundSuru("質問する");
        registerCompoundSuru("でんわする");
        registerCompoundSuru("電話する");
        registerCompoundSuru("しごとする");
        registerCompoundSuru("仕事する");
        registerCompoundSuru("かいものする");
        registerCompoundSuru("買い物する");
        registerCompoundSuru("しょくじする");
        registerCompoundSuru("食事する");
        registerCompoundSuru("あんないする");
        registerCompoundSuru("案内する");
        registerCompoundSuru("せつめいする");
        registerCompoundSuru("説明する");
        registerCompoundSuru("よやくする");
        registerCompoundSuru("予約する");
        registerCompoundSuru("ちゅうもんする");
        registerCompoundSuru("注文する");
        registerCompoundSuru("うんてんする");
        registerCompoundSuru("運転する");
        registerCompoundSuru("けんきゅうする");
        registerCompoundSuru("研究する");
        registerCompoundSuru("そつぎょうする");
        registerCompoundSuru("卒業する");
        registerCompoundSuru("にゅうがくする");
        registerCompoundSuru("入学する");
        registerCompoundSuru("しっぱいする");
        registerCompoundSuru("失敗する");
        registerCompoundSuru("せいこうする");
        registerCompoundSuru("成功する");
        registerCompoundSuru("しんぱいする");
        registerCompoundSuru("心配する");
        registerCompoundSuru("あんしんする");
        registerCompoundSuru("安心する");
        registerCompoundSuru("じゅんびする");
        registerCompoundSuru("準備する");
        registerCompoundSuru("そうだんする");
        registerCompoundSuru("相談する");
        registerCompoundSuru("けっこんする");
        registerCompoundSuru("結婚する");

        // Pre-populate common compound kuru verbs
        registerCompoundKuru("もってくる");
        registerCompoundKuru("持ってくる");
        registerCompoundKuru("持って来る");
        registerCompoundKuru("つれてくる");
        registerCompoundKuru("連れてくる");
        registerCompoundKuru("連れて来る");
        registerCompoundKuru("もどってくる");
        registerCompoundKuru("戻ってくる");
        registerCompoundKuru("戻って来る");
        registerCompoundKuru("いってくる");
        registerCompoundKuru("行ってくる");
        registerCompoundKuru("行って来る");
        registerCompoundKuru("かえってくる");
        registerCompoundKuru("帰ってくる");
        registerCompoundKuru("帰って来る");
        registerCompoundKuru("でてくる");
        registerCompoundKuru("出てくる");
        registerCompoundKuru("出て来る");
        registerCompoundKuru("はいってくる");
        registerCompoundKuru("入ってくる");
        registerCompoundKuru("入って来る");
        registerCompoundKuru("よんでくる");
        registerCompoundKuru("呼んでくる");
        registerCompoundKuru("呼んで来る");
        registerCompoundKuru("かってくる");
        registerCompoundKuru("買ってくる");
        registerCompoundKuru("買って来る");
    }

    private VerbExceptions() {
        // utility class
    }

    // -------------------------------------------------------------------------
    // Registration methods (extensible API)
    // -------------------------------------------------------------------------

    public static void registerGroup1Lookalike(String verb) {
        if (verb != null && !verb.isBlank()) {
            GROUP_1_LOOKALIKES.add(verb.trim());
        }
    }

    public static void registerCompoundSuru(String verb) {
        if (verb != null && !verb.isBlank()) {
            COMPOUND_SURU_VERBS.add(verb.trim());
        }
    }

    public static void registerCompoundKuru(String verb) {
        if (verb != null && !verb.isBlank()) {
            COMPOUND_KURU_VERBS.add(verb.trim());
        }
    }

    public static void registerCustomOverride(String dictionaryForm, ConjugatedForms forms) {
        if (dictionaryForm != null && forms != null) {
            CUSTOM_OVERRIDES.put(dictionaryForm.trim(), forms);
        }
    }

    public static Optional<ConjugatedForms> getCustomOverride(String dictionaryForm) {
        if (dictionaryForm == null) return Optional.empty();
        return Optional.ofNullable(CUSTOM_OVERRIDES.get(dictionaryForm.trim()));
    }

    public static Set<String> getGroup1Lookalikes() {
        return Collections.unmodifiableSet(GROUP_1_LOOKALIKES);
    }

    public static Set<String> getCompoundSuruVerbs() {
        return Collections.unmodifiableSet(COMPOUND_SURU_VERBS);
    }

    public static Set<String> getCompoundKuruVerbs() {
        return Collections.unmodifiableSet(COMPOUND_KURU_VERBS);
    }

    // -------------------------------------------------------------------------
    // Query / Detection methods
    // -------------------------------------------------------------------------

    /**
     * Checks if a verb is a known Group 1 exception that looks like Group 2 (ends in -iru/-eru).
     */
    public static boolean isGroup1Exception(String dictionaryForm) {
        if (dictionaryForm == null) return false;
        String trimmed = dictionaryForm.trim();
        return GROUP_1_LOOKALIKES.contains(trimmed);
    }

    /**
     * Checks if the verb is いく / 行く (or compounds ending with いく / 行く).
     */
    public static boolean isIku(String dictionaryForm, String kanjiText) {
        if (dictionaryForm != null) {
            String dict = dictionaryForm.trim();
            if (dict.equals("いく") || dict.equals("ゆく") || dict.equals("行く")) {
                return true;
            }
        }
        if (kanjiText != null) {
            String kanji = kanjiText.trim();
            if (kanji.equals("行く") || kanji.equals("いく")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the verb is the existential / possession verb ある (nai -> ない).
     */
    public static boolean isAru(String dictionaryForm) {
        if (dictionaryForm == null) return false;
        String dict = dictionaryForm.trim();
        return dict.equals("ある") || dict.equals("有る");
    }

    /**
     * Checks if the verb is standard する.
     */
    public static boolean isSuru(String dictionaryForm) {
        if (dictionaryForm == null) return false;
        return dictionaryForm.trim().equals("する");
    }

    /**
     * Checks if the verb is standard くる / 来る.
     */
    public static boolean isKuru(String dictionaryForm, String kanjiText) {
        if (dictionaryForm != null) {
            String dict = dictionaryForm.trim();
            if (dict.equals("くる") || dict.equals("来る")) {
                return true;
            }
        }
        if (kanjiText != null) {
            String kanji = kanjiText.trim();
            if (kanji.equals("来る") || kanji.equals("くる")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the verb is a compound ending with する (e.g. べんきょうする, 勉強する).
     */
    public static boolean isCompoundSuru(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        if (trimmed.equals("する")) return false;
        if (COMPOUND_SURU_VERBS.contains(trimmed)) return true;
        // Generic compound detection: ends with する and has length > 2
        return trimmed.length() > 2 && trimmed.endsWith("する");
    }

    /**
     * Checks if the verb is a compound ending with くる or 来る (e.g. もってくる, つれてくる, 持って来る).
     */
    public static boolean isCompoundKuru(String dictionaryForm, String kanjiText) {
        if (dictionaryForm != null) {
            String dict = dictionaryForm.trim();
            if (!dict.equals("くる") && !dict.equals("来る")) {
                if (COMPOUND_KURU_VERBS.contains(dict)) return true;
                if (isKuruCompoundEnding(dict)) {
                    return true;
                }
            }
        }
        if (kanjiText != null) {
            String kanji = kanjiText.trim();
            if (!kanji.equals("来る") && !kanji.equals("くる")) {
                if (COMPOUND_KURU_VERBS.contains(kanji)) return true;
                if (isKuruCompoundEnding(kanji)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isKuruCompoundEnding(String text) {
        if (text == null || text.length() < 3) return false;
        return text.endsWith("てくる") || text.endsWith("でくる")
                || text.endsWith("て来る") || text.endsWith("で来る")
                || text.endsWith("っくる") || text.endsWith("っ来る");
    }
}
