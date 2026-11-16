package it.uniroma2.dicii.isw2.ml.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.function.ToDoubleFunction;

/**
 * The figures the best configuration of each classifier is chosen by, each read off an
 * {@link EvaluationOutcome}.
 * <p>
 * Several of them and not one, because they disagree for reasons worth knowing. Accuracy rewards a model
 * for the 77% of rows that are not buggy and is the figure balancing is least likely to improve;
 * precision and recall are of the buggy class alone and pull in opposite directions, the first saying
 * how many of the classes a model flags are faulty and the second how many of the faulty classes it
 * flags; F1 weighs the two at the threshold the model actually answers with, so it moves with the trade
 * between them a technique buys; AUC is of the buggy class across every threshold, so it says whether
 * the model ranks the faulty classes higher, whatever it answers; kappa is what is left of the accuracy
 * once what guessing at the observed rates would reach is taken out. A configuration that wins on all of
 * them is the answer; one that wins on some is a choice the reader has to make, and the report says
 * which.
 */
@AllArgsConstructor
public enum Measure {

    ACCURACY("Accuracy", EvaluationOutcome::accuracy),
    PRECISION("Precision", EvaluationOutcome::precision),
    RECALL("Recall", EvaluationOutcome::recall),
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
