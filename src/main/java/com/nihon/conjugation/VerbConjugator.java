package com.nihon.conjugation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure Java Japanese verb conjugator supporting Groups 1, 2, and 3,
 * irregular exceptions, okurigana / kanji preservation, consistency validation,
 * and masu-form group/dictionary inference.
 *
 * Zero Spring dependencies.
 */
public final class VerbConjugator {

    // E-dan vowels: え, け, げ, せ, ぜ, て, で, ね, へ, べ, ぺ, め, れ
    private static final Set<Character> E_DAN = Set.of(
            'え', 'け', 'げ', 'せ', 'ぜ', 'て', 'で', 'ね', 'へ', 'べ', 'ぺ', 'め', 'れ'
    );

    // I-dan vowels: い, き, ぎ, し, じ, ち, ぢ, に, ひ, び, ぴ, み, り
    private static final Set<Character> I_DAN = Set.of(
            'い', 'き', 'ぎ', 'し', 'じ', 'ち', 'ぢ', 'に', 'ひ', 'び', 'ぴ', 'み', 'り'
    );

    // A-dan vowels
    private static final Set<Character> A_DAN = Set.of(
            'あ', 'か', 'が', 'さ', 'ざ', 'た', 'だ', 'な', 'は', 'ば', 'ぱ', 'ま', 'や', 'ら', 'わ'
    );

    // U-dan vowels
    private static final Set<Character> U_DAN = Set.of(
            'う', 'く', 'ぐ', 'す', 'ず', 'つ', 'づ', 'ぬ', 'ふ', 'ぶ', 'ぷ', 'む', 'ゆ', 'る'
    );

    // O-dan vowels
    private static final Set<Character> O_DAN = Set.of(
            'お', 'こ', 'ご', 'そ', 'ぞ', 'と', 'ど', 'の', 'ほ', 'ぼ', 'ぽ', 'も', 'よ', 'ろ', 'を'
    );

    // Mapping from i-dan to u-dan for Godan masu restoration
    private static final Map<Character, Character> I_TO_U = Map.ofEntries(
            Map.entry('い', 'う'),
            Map.entry('き', 'く'),
            Map.entry('ぎ', 'ぐ'),
            Map.entry('し', 'す'),
            Map.entry('じ', 'ず'),
            Map.entry('ち', 'つ'),
            Map.entry('ぢ', 'づ'),
            Map.entry('に', 'ぬ'),
            Map.entry('ひ', 'ふ'),
            Map.entry('び', 'ぶ'),
            Map.entry('ぴ', 'ぷ'),
            Map.entry('み', 'む'),
            Map.entry('り', 'る')
    );

    // Common kanji to kana reading mapping for direct kanji input convenience
    private static final Map<String, String> COMMON_KANJI_READINGS = Map.ofEntries(
            Map.entry("飲む", "のむ"),
            Map.entry("食べる", "たべる"),
            Map.entry("行く", "いく"),
            Map.entry("来る", "くる"),
            Map.entry("話す", "はなす"),
            Map.entry("待つ", "まつ"),
            Map.entry("泳ぐ", "およぐ"),
            Map.entry("死ぬ", "しぬ"),
            Map.entry("遊ぶ", "あそぶ"),
            Map.entry("買う", "かう"),
            Map.entry("取る", "とる"),
            Map.entry("読む", "よむ"),
            Map.entry("書く", "かく"),
            Map.entry("見る", "みる"),
            Map.entry("起きる", "おきる"),
            Map.entry("寝る", "ねる"),
            Map.entry("帰る", "かえる"),
            Map.entry("入る", "はいる"),
            Map.entry("知る", "しる"),
            Map.entry("走る", "はしる"),
            Map.entry("切る", "きる"),
            Map.entry("喋る", "しゃべる"),
            Map.entry("有る", "ある"),
            Map.entry("言う", "いう"),
            Map.entry("思う", "おもう"),
            Map.entry("立つ", "たつ"),
            Map.entry("持つ", "もつ"),
            Map.entry("作る", "つくる"),
            Map.entry("分かる", "わかる"),
            Map.entry("休む", "やすむ"),
            Map.entry("呼ぶ", "よぶ"),
            Map.entry("選ぶ", "えらぶ"),
            Map.entry("聞く", "きく"),
            Map.entry("歩く", "あるく"),
            Map.entry("急ぐ", "いそぐ"),
            Map.entry("貸す", "かす"),
            Map.entry("教える", "おしえる"),
            Map.entry("開ける", "あける"),
            Map.entry("閉める", "しめる"),
            Map.entry("忘れる", "わすれる"),
            Map.entry("借りる", "かりる"),
            Map.entry("勉強する", "べんきょうする"),
            Map.entry("練習する", "れんしゅうする"),
            Map.entry("散歩する", "さんぽする"),
            Map.entry("持って来る", "もってくる"),
            Map.entry("持ってくる", "もってくる"),
            Map.entry("連れて来る", "つれてくる"),
            Map.entry("連れてくる", "つれてくる")
    );

    private VerbConjugator() {
        // utility class
    }

    // -------------------------------------------------------------------------
    // Core Conjugation
    // -------------------------------------------------------------------------

    /**
     * Conjugates a Japanese verb into its 5 core forms (dictionary, masu, te, ta, nai).
     *
     * @param dictionaryForm Japanese dictionary form (hiragana or kanji)
     * @param verbGroup      1 (Godan), 2 (Ichidan), 3 (Irregular)
     * @param kanjiText      Optional kanji representation (e.g. "飲む")
     * @return ConjugatedForms containing kana forms and kanji forms (if kanjiText is provided)
     */
    public static ConjugatedForms conjugate(String dictionaryForm, Integer verbGroup, String kanjiText) {
        if (dictionaryForm == null || dictionaryForm.isBlank()) {
            throw new IllegalArgumentException("Dictionary form must not be blank");
        }
        String dict = dictionaryForm.trim();
        String kanji = (kanjiText != null && !kanjiText.isBlank()) ? kanjiText.trim() : null;

        // If dictionaryForm contains kanji and kanji is not specified, assign kanjiText = dict
        if (kanji == null && containsKanji(dict)) {
            kanji = dict;
            String knownReading = COMMON_KANJI_READINGS.get(dict);
            if (knownReading != null) {
                dict = knownReading;
            }
        }

        // If verbGroup is null, attempt to infer
        if (verbGroup == null) {
            verbGroup = inferGroup(dict);
            if (verbGroup == null) {
                throw new IllegalArgumentException("Verb group could not be inferred for: " + dict);
            }
        }

        if (verbGroup < 1 || verbGroup > 3) {
            throw new IllegalArgumentException("Verb group must be 1, 2, or 3");
        }

        // Check custom overrides first
        var custom = VerbExceptions.getCustomOverride(dict);
        if (custom.isPresent()) {
            return custom.get();
        }

        switch (verbGroup) {
            case 3:
                return conjugateGroup3(dict, kanji);
            case 2:
                return conjugateGroup2(dict, kanji);
            case 1:
                return conjugateGroup1(dict, kanji);
            default:
                throw new IllegalArgumentException("Unsupported verb group: " + verbGroup);
        }
    }

    public static ConjugatedForms conjugate(String dictionaryForm, Integer verbGroup) {
        return conjugate(dictionaryForm, verbGroup, null);
    }

    public static ConjugatedForms conjugate(String dictionaryForm, String kanjiText) {
        Integer group = inferGroup(dictionaryForm);
        return conjugate(dictionaryForm, group, kanjiText);
    }

    // -------------------------------------------------------------------------
    // Group 3 (Irregular)
    // -------------------------------------------------------------------------

    private static ConjugatedForms conjugateGroup3(String dict, String kanji) {
        // Pure する
        if (VerbExceptions.isSuru(dict)) {
            String masu = "します";
            String te = "して";
            String ta = "した";
            String nai = "しない";

            String kMasu = kanji != null ? "します" : null;
            String kTe = kanji != null ? "して" : null;
            String kTa = kanji != null ? "した" : null;
            String kNai = kanji != null ? "しない" : null;

            return new ConjugatedForms(dict, masu, te, ta, nai, kanji, kMasu, kTe, kTa, kNai);
        }

        // Compound ending with する (e.g. べんきょうする, 勉強する)
        if (dict.endsWith("する") && dict.length() > 2) {
            String kanaPrefix = dict.substring(0, dict.length() - 2);
            String masu = kanaPrefix + "します";
            String te = kanaPrefix + "して";
            String ta = kanaPrefix + "した";
            String nai = kanaPrefix + "しない";

            String kMasu = null, kTe = null, kTa = null, kNai = null;
            if (kanji != null) {
                String kPrefix = kanji.endsWith("する") ? kanji.substring(0, kanji.length() - 2) : kanji;
                kMasu = kPrefix + "します";
                kTe = kPrefix + "して";
                kTa = kPrefix + "した";
                kNai = kPrefix + "しない";
            }

            return new ConjugatedForms(dict, masu, te, ta, nai, kanji, kMasu, kTe, kTa, kNai);
        }

        // Pure くる / 来る
        if (VerbExceptions.isKuru(dict, kanji)) {
            String masu = "きます";
            String te = "きて";
            String ta = "きた";
            String nai = "こない";

            String kMasu = null, kTe = null, kTa = null, kNai = null;
            if (kanji != null) {
                if (kanji.contains("来")) {
                    kMasu = "来ます";
                    kTe = "来て";
                    kTa = "来た";
                    kNai = "来ない";
                } else {
                    kMasu = "きます";
                    kTe = "きて";
                    kTa = "きた";
                    kNai = "こない";
                }
            }

            return new ConjugatedForms(dict, masu, te, ta, nai, kanji, kMasu, kTe, kTa, kNai);
        }

        // Compound ending with くる / 来る (e.g. もってくる, つれてくる, 持って来る)
        if (VerbExceptions.isCompoundKuru(dict, kanji)) {
            String kanaPrefix;
            if (dict.endsWith("くる") || dict.endsWith("来る")) {
                kanaPrefix = dict.substring(0, dict.length() - 2);
            } else {
                kanaPrefix = dict;
            }

            String masu = kanaPrefix + "きます";
            String te = kanaPrefix + "きて";
            String ta = kanaPrefix + "きた";
            String nai = kanaPrefix + "こない";

            String kMasu = null, kTe = null, kTa = null, kNai = null;
            if (kanji != null) {
                if (kanji.endsWith("来る")) {
                    String kPrefix = kanji.substring(0, kanji.length() - 2);
                    kMasu = kPrefix + "来ます";
                    kTe = kPrefix + "来て";
                    kTa = kPrefix + "来た";
                    kNai = kPrefix + "来ない";
                } else if (kanji.endsWith("くる")) {
                    String kPrefix = kanji.substring(0, kanji.length() - 2);
                    kMasu = kPrefix + "きます";
                    kTe = kPrefix + "きて";
                    kTa = kPrefix + "きた";
                    kNai = kPrefix + "こない";
                } else {
                    kMasu = kanji + "来ます";
                    kTe = kanji + "来て";
                    kTa = kanji + "来た";
                    kNai = kanji + "来ない";
                }
            }

            return new ConjugatedForms(dict, masu, te, ta, nai, kanji, kMasu, kTe, kTa, kNai);
        }

        throw new IllegalArgumentException("Unknown Group 3 verb: " + dict);
    }

    // -------------------------------------------------------------------------
    // Group 2 (Ichidan)
    // -------------------------------------------------------------------------

    private static ConjugatedForms conjugateGroup2(String dict, String kanji) {
        if (!dict.endsWith("る")) {
            throw new IllegalArgumentException("Group 2 verbs must end with 'る': " + dict);
        }

        String kanaStem = dict.substring(0, dict.length() - 1);
        String masu = kanaStem + "ます";
        String te = kanaStem + "て";
        String ta = kanaStem + "た";
        String nai = kanaStem + "ない";

        String kMasu = null, kTe = null, kTa = null, kNai = null;
        if (kanji != null) {
            String kanjiStem = kanji.endsWith("る") ? kanji.substring(0, kanji.length() - 1) : kanji;
            kMasu = kanjiStem + "ます";
            kTe = kanjiStem + "て";
            kTa = kanjiStem + "た";
            kNai = kanjiStem + "ない";
        }

        return new ConjugatedForms(dict, masu, te, ta, nai, kanji, kMasu, kTe, kTa, kNai);
    }

    // -------------------------------------------------------------------------
    // Group 1 (Godan)
    // -------------------------------------------------------------------------

    private static ConjugatedForms conjugateGroup1(String dict, String kanji) {
        if (dict.length() < 1) {
            throw new IllegalArgumentException("Dictionary form is too short");
        }

        // Exception 1: ある (nai -> ない)
        if (VerbExceptions.isAru(dict)) {
            String masu = "あります";
            String te = "あって";
            String ta = "あった";
            String nai = "ない";

            String kMasu = null, kTe = null, kTa = null, kNai = null;
            if (kanji != null) {
                String kStem = kanji.endsWith("る") ? kanji.substring(0, kanji.length() - 1) : kanji;
                kMasu = kStem + "ります";
                kTe = kStem + "って";
                kTa = kStem + "った";
                kNai = "ない";
            }
            return new ConjugatedForms(dict, masu, te, ta, nai, kanji, kMasu, kTe, kTa, kNai);
        }

        // Exception 2: いく / 行く (te -> いって/行って, ta -> いった/行った)
        if (VerbExceptions.isIku(dict, kanji)) {
            String masu = "いきます";
            String te = "いって";
            String ta = "いった";
            String nai = "いかない";

            String kMasu = null, kTe = null, kTa = null, kNai = null;
            if (kanji != null) {
                String kStem = kanji.endsWith("く") ? kanji.substring(0, kanji.length() - 1) : kanji;
                kMasu = kStem + "きます";
                kTe = kStem + "って";
                kTa = kStem + "った";
                kNai = kStem + "かない";
            }
            return new ConjugatedForms(dict, masu, te, ta, nai, kanji, kMasu, kTe, kTa, kNai);
        }

        char tail = dict.charAt(dict.length() - 1);
        String kanaStem = dict.substring(0, dict.length() - 1);

        String masuSuffix;
        String teSuffix;
        String taSuffix;
        String naiSuffix;

        switch (tail) {
            case 'う':
                masuSuffix = "います";
                teSuffix = "って";
                taSuffix = "った";
                naiSuffix = "わない";
                break;
            case 'つ':
                masuSuffix = "ちます";
                teSuffix = "って";
                taSuffix = "った";
                naiSuffix = "たない";
                break;
            case 'る':
                masuSuffix = "ります";
                teSuffix = "って";
                taSuffix = "った";
                naiSuffix = "らない";
                break;
            case 'む':
                masuSuffix = "みます";
                teSuffix = "んで";
                taSuffix = "んだ";
                naiSuffix = "まない";
                break;
            case 'ぶ':
                masuSuffix = "びます";
                teSuffix = "んで";
                taSuffix = "んだ";
                naiSuffix = "ばない";
                break;
            case 'ぬ':
                masuSuffix = "にます";
                teSuffix = "んで";
                taSuffix = "んだ";
                naiSuffix = "なない";
                break;
            case 'く':
                masuSuffix = "きます";
                teSuffix = "いて";
                taSuffix = "いた";
                naiSuffix = "かない";
                break;
            case 'ぐ':
                masuSuffix = "ぎます";
                teSuffix = "いで";
                taSuffix = "いだ";
                naiSuffix = "がない";
                break;
            case 'す':
                masuSuffix = "します";
                teSuffix = "して";
                taSuffix = "した";
                naiSuffix = "さない";
                break;
            default:
                throw new IllegalArgumentException("Unsupported godan ending: '" + tail + "' in verb: " + dict);
        }

        String masu = kanaStem + masuSuffix;
        String te = kanaStem + teSuffix;
        String ta = kanaStem + taSuffix;
        String nai = kanaStem + naiSuffix;

        String kMasu = null, kTe = null, kTa = null, kNai = null;
        if (kanji != null) {
            String kStem = kanji.endsWith(String.valueOf(tail))
                    ? kanji.substring(0, kanji.length() - 1)
                    : kanji;
            kMasu = kStem + masuSuffix;
            kTe = kStem + teSuffix;
            kTa = kStem + taSuffix;
            kNai = kStem + naiSuffix;
        }

        return new ConjugatedForms(dict, masu, te, ta, nai, kanji, kMasu, kTe, kTa, kNai);
    }

    // -------------------------------------------------------------------------
    // Consistency Validation
    // -------------------------------------------------------------------------

    /**
     * Checks if dictionaryForm and masuForm match for the specified verb group.
     */
    public static boolean isConsistent(String dictionaryForm, String masuForm, Integer verbGroup) {
        return isConsistent(dictionaryForm, masuForm, verbGroup, null);
    }

    /**
     * Checks if dictionaryForm and masuForm match for the specified verb group and optional kanji.
     */
    public static boolean isConsistent(String dictionaryForm, String masuForm, Integer verbGroup, String kanjiText) {
        if (dictionaryForm == null || masuForm == null || verbGroup == null) {
            return false;
        }
        String dict = dictionaryForm.trim();
        String masu = masuForm.trim();
        if (dict.isEmpty() || masu.isEmpty()) {
            return false;
        }
        if (verbGroup < 1 || verbGroup > 3) {
            return false;
        }
        if (!masu.endsWith("ます")) {
            return false;
        }

        // Structural sanity checks per group
        if (verbGroup == 2 && !dict.endsWith("る")) {
            return false;
        }
        if (verbGroup == 3) {
            boolean isSuru = VerbExceptions.isSuru(dict) || VerbExceptions.isCompoundSuru(dict);
            boolean isKuru = VerbExceptions.isKuru(dict, kanjiText) || VerbExceptions.isCompoundKuru(dict, kanjiText);
            if (!isSuru && !isKuru) {
                return false;
            }
        }

        try {
            ConjugatedForms forms = conjugate(dict, verbGroup, kanjiText);
            if (masu.equals(forms.getMasuForm())) {
                return true;
            }
            if (forms.getKanjiMasuForm() != null && masu.equals(forms.getKanjiMasuForm())) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Masu Form Inference
    // -------------------------------------------------------------------------

    /**
     * Infers the verb group and dictionary form from a polite masu-form.
     * - Returns clear result for e-dan (Group 2), exact します/きます (Group 3),
     *   and recognized compound suru/kuru verbs.
     * - Returns ambiguous result when preceding syllable is i-dan (cannot distinguish Group 1 vs Group 2).
     * - Returns invalid result when not ending in ます or invalid syllable.
     */
    public static MasuInferenceResult inferFromMasu(String masuForm) {
        return inferFromMasu(masuForm, null);
    }

    public static MasuInferenceResult inferFromMasu(String masuForm, String kanjiMasu) {
        if (masuForm == null || masuForm.isBlank()) {
            return MasuInferenceResult.invalid("Thể masu không được để trống");
        }
        String masu = masuForm.trim();
        if (!masu.endsWith("ます")) {
            return MasuInferenceResult.invalid("Thể masu phải kết thúc bằng 'ます': " + masu);
        }
        if (masu.length() < 3) {
            return MasuInferenceResult.invalid("Thể masu quá ngắn: " + masu);
        }

        // Exact Group 3 checks
        if (masu.equals("します")) {
            return MasuInferenceResult.clear(3, "する");
        }
        if (masu.equals("きます") || masu.equals("来ます")) {
            return MasuInferenceResult.clear(3, "くる");
        }

        // Compound suru check (e.g. べんきょうします -> べんきょうする)
        if (masu.endsWith("します") && masu.length() > 3) {
            String prefix = masu.substring(0, masu.length() - 3);
            String potentialDict = prefix + "する";
            if (VerbExceptions.getCompoundSuruVerbs().contains(potentialDict)
                    || (kanjiMasu != null && VerbExceptions.getCompoundSuruVerbs().contains(kanjiMasu.replace("します", "する")))) {
                return MasuInferenceResult.clear(3, potentialDict);
            }
        }

        // Compound kuru check (e.g. もってきます -> もってくる)
        if ((masu.endsWith("きます") || masu.endsWith("来ます")) && masu.length() > 3) {
            String prefix = masu.substring(0, masu.length() - 3);
            String potentialDict = prefix + "くる";
            if (VerbExceptions.getCompoundKuruVerbs().contains(potentialDict)
                    || (kanjiMasu != null && VerbExceptions.getCompoundKuruVerbs().contains(kanjiMasu.replace("きます", "くる")))) {
                return MasuInferenceResult.clear(3, potentialDict);
            }
        }

        // Preceding syllable analysis
        char c = masu.charAt(masu.length() - 3);
        String stem = masu.substring(0, masu.length() - 2);

        // E-dan: 100% Group 2 (Ichidan)
        if (E_DAN.contains(c)) {
            String dictionaryForm = stem + "る";
            return MasuInferenceResult.clear(2, dictionaryForm);
        }

        // I-dan: Ambiguous between Group 1 and Group 2
        if (I_DAN.contains(c)) {
            List<MasuInferenceResult.Candidate> candidates = new ArrayList<>();

            // Group 2 candidate: stem + る
            String g2Dict = stem + "る";
            candidates.add(new MasuInferenceResult.Candidate(
                    2, g2Dict, "Động từ nhóm 2 (Ichidan): bỏ ます thêm る"
            ));

            // Group 1 candidate: change i-dan to u-dan
            Character uDan = I_TO_U.get(c);
            if (uDan != null) {
                String prefix = masu.substring(0, masu.length() - 3);
                String g1Dict = prefix + uDan;
                candidates.add(new MasuInferenceResult.Candidate(
                        1, g1Dict, "Động từ nhóm 1 (Godan): đổi âm '" + c + "' sang hàng u ('" + uDan + "')"
                ));
            }

            // Group 3 candidate if ending in します (potential suru compound)
            if (c == 'し' && masu.length() > 3) {
                String g3Dict = stem.substring(0, stem.length() - 1) + "する";
                candidates.add(new MasuInferenceResult.Candidate(
                        3, g3Dict, "Động từ nhóm 3: dạng ghép する"
                ));
            }

            return MasuInferenceResult.ambiguous(
                    candidates,
                    "Âm '" + c + "' trước 'ます' thuộc hàng i: không thể xác định duy nhất giữa Nhóm 1 và Nhóm 2"
            );
        }

        return MasuInferenceResult.invalid(
                "Âm '" + c + "' trước 'ます' không thuộc hàng i hoặc hàng e, không phải thể ます chuẩn"
        );
    }

    // -------------------------------------------------------------------------
    // Group Inference from Dictionary Form
    // -------------------------------------------------------------------------

    /**
     * Infers verb group (1, 2, or 3) from the dictionary form.
     */
    public static Integer inferGroup(String dictionaryForm) {
        if (dictionaryForm == null || dictionaryForm.isBlank()) {
            return null;
        }
        String dict = dictionaryForm.trim();

        // Group 3 irregulars
        if (VerbExceptions.isSuru(dict) || VerbExceptions.isCompoundSuru(dict)) {
            return 3;
        }
        if (VerbExceptions.isKuru(dict, null) || VerbExceptions.isCompoundKuru(dict, null)) {
            return 3;
        }

        // Known Group 1 lookalike exceptions
        if (VerbExceptions.isGroup1Exception(dict)) {
            return 1;
        }

        // Verbs not ending in る MUST be Group 1
        if (!dict.endsWith("る")) {
            return 1;
        }

        // Ends in る: check vowel preceding る
        if (dict.length() >= 2) {
            char beforeRu = dict.charAt(dict.length() - 2);
            if (A_DAN.contains(beforeRu) || U_DAN.contains(beforeRu) || O_DAN.contains(beforeRu)) {
                // Verbs ending in -aru, -uru, -oru are ALWAYS Group 1
                return 1;
            }
            if (I_DAN.contains(beforeRu) || E_DAN.contains(beforeRu)) {
                // Verbs ending in -iru or -eru: default to Group 2 unless registered lookalike
                return 2;
            }
        }

        return 1;
    }

    private static boolean containsKanji(String text) {
        if (text == null) return false;
        return text.codePoints().anyMatch(cp ->
                Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }
}
