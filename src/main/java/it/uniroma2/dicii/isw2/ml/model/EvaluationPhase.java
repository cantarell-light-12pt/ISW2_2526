package it.uniroma2.dicii.isw2.ml.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The occasions a model is measured on, which the report tells apart because they answer different
 * questions.
 */
@Getter
@AllArgsConstructor
public enum EvaluationPhase {

    /**
     * Cross-validated over the folds of the training set. It says how well a model fits the releases it
     * was given, and is what one model is chosen over another by — every fold of it is drawn from the
     * training set alone, so choosing on it costs the test set nothing.
     */
    VALIDATION("validation"),

    /**
     * Trained on the whole training set and asked about the test releases, which nothing has read until
     * this point. It says how well the model predicts releases it has never seen, which is the figure
     * the project is finally reported on.
     */
    TEST("test"),

    /**
     * Trained on the earlier of the training releases and asked about the later ones, to choose how the
     * training rows are balanced. Held out like the test phase and drawn from the training releases like
     * the validation, so that the choice is made in time order and costs the test releases nothing.
     */
    SELECTION("selection");

    /**
     * How the phase reads in the report.
     */
    private final String label;

}
