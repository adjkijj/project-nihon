package com.nihon.conjugation;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of inferring verb group and dictionary form from a polite masu-form.
 * Zero Spring dependencies (pure Java).
 */
public class MasuInferenceResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean valid;
    private final boolean ambiguous;
    private final Integer verbGroup;
    private final String dictionaryForm;
    private final List<Candidate> candidates;
    private final String errorMessage;

    public MasuInferenceResult(boolean valid,
                                boolean ambiguous,
                                Integer verbGroup,
                                String dictionaryForm,
                                List<Candidate> candidates,
                                String errorMessage) {
        this.valid = valid;
        this.ambiguous = ambiguous;
        this.verbGroup = verbGroup;
        this.dictionaryForm = dictionaryForm;
        this.candidates = candidates != null ? Collections.unmodifiableList(candidates) : Collections.emptyList();
        this.errorMessage = errorMessage;
    }

    public static MasuInferenceResult clear(int verbGroup, String dictionaryForm) {
        return new MasuInferenceResult(true, false, verbGroup, dictionaryForm, Collections.emptyList(), null);
    }

    public static MasuInferenceResult ambiguous(List<Candidate> candidates, String message) {
        return new MasuInferenceResult(true, true, null, null, candidates, message);
    }

    public static MasuInferenceResult invalid(String errorMessage) {
        return new MasuInferenceResult(false, false, null, null, Collections.emptyList(), errorMessage);
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isAmbiguous() {
        return ambiguous;
    }

    public boolean isClear() {
        return valid && !ambiguous;
    }

    public Integer getVerbGroup() {
        return verbGroup;
    }

    public String getDictionaryForm() {
        return dictionaryForm;
    }

    public List<Candidate> getCandidates() {
        return candidates;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        if (!valid) {
            return "MasuInferenceResult{invalid: '" + errorMessage + "'}";
        }
        if (ambiguous) {
            return "MasuInferenceResult{ambiguous: candidates=" + candidates + ", note='" + errorMessage + "'}";
        }
        return "MasuInferenceResult{group=" + verbGroup + ", dict='" + dictionaryForm + "'}";
    }

    public static class Candidate implements Serializable {
        private static final long serialVersionUID = 1L;

        private final int verbGroup;
        private final String dictionaryForm;
        private final String explanation;

        public Candidate(int verbGroup, String dictionaryForm, String explanation) {
            this.verbGroup = verbGroup;
            this.dictionaryForm = dictionaryForm;
            this.explanation = explanation;
        }

        public int getVerbGroup() {
            return verbGroup;
        }

        public String getDictionaryForm() {
            return dictionaryForm;
        }

        public String getExplanation() {
            return explanation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Candidate candidate = (Candidate) o;
            return verbGroup == candidate.verbGroup &&
                    Objects.equals(dictionaryForm, candidate.dictionaryForm) &&
                    Objects.equals(explanation, candidate.explanation);
        }

        @Override
        public int hashCode() {
            return Objects.hash(verbGroup, dictionaryForm, explanation);
        }

        @Override
        public String toString() {
            return "Candidate{group=" + verbGroup + ", dict='" + dictionaryForm + "', note='" + explanation + "'}";
        }
    }
}
