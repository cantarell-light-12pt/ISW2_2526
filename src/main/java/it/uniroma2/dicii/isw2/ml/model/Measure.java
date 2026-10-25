package it.uniroma2.dicii.isw2.ml.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.function.ToDoubleFunction;

/**
 * The figures the balancing techniques are compared by, each read off an {@link EvaluationOutcome}.
 * <p>
 * Four of them and not one, because they disagree for reasons worth knowing. Accuracy rewards a model
 * for the 77% of rows that are not buggy and is the figure balancing is least likely to improve; F1 is
 * of the buggy class alone, at the threshold the model actually answers with, so it moves with the
 * trade between precision and recall a technique buys; AUC is of the buggy class across every threshold,
 * so it says whether the model ranks the faulty classes higher, whatever it answers; kappa is what is
 * left of the accuracy once what guessing at the observed rates would reach is taken out. A technique
 * that wins on all four is the answer; one that wins on some is a choice the reader has to make, and
 * the study reports which.
 */
@AllArgsConstructor
public enum Measure {

    ACCURACY("Accuracy", EvaluationOutcome::accuracy),
    F1("F1", EvaluationOutcome::fMeasure),
    AUC("AUC", EvaluationOutcome::areaUnderRoc),
    KAPPA("Kappa", EvaluationOutcome::kappa);

    /**
     * How the measure reads in the reports.
     */
    @Getter
    private final String label;

    private final ToDoubleFunction<EvaluationOutcome> reader;

    /**
     * @param outcome how a model scored
     * @return its figure for this measure, which is {@code NaN} for a per-class figure of a model that
     * never predicted the class
     */
    public double of(EvaluationOutcome outcome) {
        return reader.applyAsDouble(outcome);
    }

}
