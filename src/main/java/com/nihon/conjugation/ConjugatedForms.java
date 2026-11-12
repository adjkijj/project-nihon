package com.nihon.conjugation;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable model holding the 5 core conjugated forms of a Japanese verb,
 * both in kana reading and optionally in kanji representation.
 * Zero Spring dependencies (pure Java).
 */
public class ConjugatedForms implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String dictionaryForm;
    private final String masuForm;
    private final String teForm;
    private final String taForm;
    private final String naiForm;

    private final String kanjiDictionaryForm;
    private final String kanjiMasuForm;
    private final String kanjiTeForm;
    private final String kanjiTaForm;
    private final String kanjiNaiForm;

    public ConjugatedForms(String dictionaryForm,
                           String masuForm,
                           String teForm,
                           String taForm,
                           String naiForm) {
        this(dictionaryForm, masuForm, teForm, taForm, naiForm, null, null, null, null, null);
    }

    public ConjugatedForms(String dictionaryForm,
                           String masuForm,
                           String teForm,
                           String taForm,
                           String naiForm,
                           String kanjiDictionaryForm,
                           String kanjiMasuForm,
                           String kanjiTeForm,
                           String kanjiTaForm,
                           String kanjiNaiForm) {
        this.dictionaryForm = dictionaryForm;
        this.masuForm = masuForm;
        this.teForm = teForm;
        this.taForm = taForm;
        this.naiForm = naiForm;
        this.kanjiDictionaryForm = kanjiDictionaryForm;
        this.kanjiMasuForm = kanjiMasuForm;
        this.kanjiTeForm = kanjiTeForm;
        this.kanjiTaForm = kanjiTaForm;
        this.kanjiNaiForm = kanjiNaiForm;
    }

    public String getDictionaryForm() {
        return dictionaryForm;
    }

    public String getMasuForm() {
        return masuForm;
    }

    public String getTeForm() {
        return teForm;
    }

    public String getTaForm() {
        return taForm;
    }

    public String getNaiForm() {
        return naiForm;
    }

    public String getKanjiDictionaryForm() {
        return kanjiDictionaryForm;
    }

    public String getKanjiMasuForm() {
        return kanjiMasuForm;
    }

    public String getKanjiTeForm() {
        return kanjiTeForm;
    }

    public String getKanjiTaForm() {
        return kanjiTaForm;
    }

    public String getKanjiNaiForm() {
        return kanjiNaiForm;
    }

    public boolean hasKanji() {
        return kanjiDictionaryForm != null && !kanjiDictionaryForm.isBlank();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConjugatedForms that = (ConjugatedForms) o;
        return Objects.equals(dictionaryForm, that.dictionaryForm) &&
                Objects.equals(masuForm, that.masuForm) &&
                Objects.equals(teForm, that.teForm) &&
                Objects.equals(taForm, that.taForm) &&
                Objects.equals(naiForm, that.naiForm) &&
                Objects.equals(kanjiDictionaryForm, that.kanjiDictionaryForm) &&
                Objects.equals(kanjiMasuForm, that.kanjiMasuForm) &&
                Objects.equals(kanjiTeForm, that.kanjiTeForm) &&
                Objects.equals(kanjiTaForm, that.kanjiTaForm) &&
                Objects.equals(kanjiNaiForm, that.kanjiNaiForm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dictionaryForm, masuForm, teForm, taForm, naiForm,
                kanjiDictionaryForm, kanjiMasuForm, kanjiTeForm, kanjiTaForm, kanjiNaiForm);
    }

    @Override
    public String toString() {
        return "ConjugatedForms{" +
                "dict='" + dictionaryForm + '\'' +
                ", masu='" + masuForm + '\'' +
                ", te='" + teForm + '\'' +
                ", ta='" + taForm + '\'' +
                ", nai='" + naiForm + '\'' +
                (hasKanji() ? ", kanjiDict='" + kanjiDictionaryForm + '\'' +
                        ", kanjiMasu='" + kanjiMasuForm + '\'' +
                        ", kanjiTe='" + kanjiTeForm + '\'' +
                        ", kanjiTa='" + kanjiTaForm + '\'' +
                        ", kanjiNai='" + kanjiNaiForm + '\'' : "") +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String dictionaryForm;
        private String masuForm;
        private String teForm;
        private String taForm;
        private String naiForm;
        private String kanjiDictionaryForm;
        private String kanjiMasuForm;
        private String kanjiTeForm;
        private String kanjiTaForm;
        private String kanjiNaiForm;

        public Builder dictionaryForm(String dictionaryForm) {
            this.dictionaryForm = dictionaryForm;
            return this;
        }

        public Builder masuForm(String masuForm) {
            this.masuForm = masuForm;
            return this;
        }

        public Builder teForm(String teForm) {
            this.teForm = teForm;
            return this;
        }

        public Builder taForm(String taForm) {
            this.taForm = taForm;
            return this;
        }

        public Builder naiForm(String naiForm) {
            this.naiForm = naiForm;
            return this;
        }

        public Builder kanjiDictionaryForm(String kanjiDictionaryForm) {
            this.kanjiDictionaryForm = kanjiDictionaryForm;
            return this;
        }

        public Builder kanjiMasuForm(String kanjiMasuForm) {
            this.kanjiMasuForm = kanjiMasuForm;
            return this;
        }

        public Builder kanjiTeForm(String kanjiTeForm) {
            this.kanjiTeForm = kanjiTeForm;
            return this;
        }

        public Builder kanjiTaForm(String kanjiTaForm) {
            this.kanjiTaForm = kanjiTaForm;
            return this;
        }

        public Builder kanjiNaiForm(String kanjiNaiForm) {
            this.kanjiNaiForm = kanjiNaiForm;
            return this;
        }

        public ConjugatedForms build() {
            return new ConjugatedForms(
                    dictionaryForm, masuForm, teForm, taForm, naiForm,
                    kanjiDictionaryForm, kanjiMasuForm, kanjiTeForm, kanjiTaForm, kanjiNaiForm
            );
        }
    }
}
