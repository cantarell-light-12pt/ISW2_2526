package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import it.uniroma2.dicii.isw2.ml.model.Measure;

import java.util.List;
import java.util.Optional;

/**
 * Picks, for one model and one measure, the balancing technique it scored best under.
 * <p>
 * A tie goes to the technique measured first, which is the one listed first in the configuration —
 * {@code None} as the project lists them. That is deliberate: a technique that scores no better than the
 * rows as they are has bought nothing, and resampling is not free, so it should not win on a coin toss.
 * A figure that is no number — the F1 of a model that never predicted the buggy class — loses to every
 * figure that is one, since a model that found nothing is not the one to choose.
 */
public final class BestBalancing {

    private BestBalancing() {
        // Compares outcomes, and holds none
    }

    /**
     * @param outcomes   how every model scored under every technique
     * @param classifier which model to choose a technique for
     * @param measure    what to choose it by
     * @return the outcome of the technique it scored highest under, or nothing if it scored no number
     * under any of them
     */
    public static Optional<EvaluationOutcome> of(List<EvaluationOutcome> outcomes, ClassifierKind classifier,
                                                 Measure measure) {
        EvaluationOutcome best = null;
        for (EvaluationOutcome outcome : outcomes) {
            double score = measure.of(outcome);
            if (outcome.classifier() == classifier && Double.isFinite(score)
                    && (best == null || score > measure.of(best))) {
                best = outcome;
            }
        }
        return Optional.ofNullable(best);
    }

}
