package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import it.uniroma2.dicii.isw2.ml.model.Measure;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Picks, out of the outcomes a choice is made among, the one that scored best by one measure — the
 * pairing of a balancing technique and a feature selection a classifier scores best with.
 * <p>
 * A tie goes to the outcome measured first, which is the pairing listed first in the configuration —
 * {@code None} and {@code None} as the project lists both kinds. That is deliberate: a technique that
 * scores no better than leaving the rows as they are has bought nothing, and none of them is free, so it
 * should not win on a coin toss. A figure that is no number — the F1 of a model that never predicted the buggy class —
 * loses to every figure that is one, since a model that found nothing is not the one to choose.
 */
public final class BestOutcome {

    private BestOutcome() {
        // Compares outcomes, and holds none
    }

    /**
     * @param outcomes   how every model scored under every pairing of techniques
     * @param candidates which of them the choice is among, e.g. those of one model
     * @param measure    what to choose by
     * @return the candidate scoring highest, or nothing if none of them scored a number
     */
    public static Optional<EvaluationOutcome> of(List<EvaluationOutcome> outcomes,
                                                 Predicate<EvaluationOutcome> candidates, Measure measure) {
        EvaluationOutcome best = null;
        for (EvaluationOutcome outcome : outcomes) {
            double score = measure.of(outcome);
            if (candidates.test(outcome) && Double.isFinite(score)
                    && (best == null || score > measure.of(best))) {
                best = outcome;
            }
        }
        return Optional.ofNullable(best);
    }

}
