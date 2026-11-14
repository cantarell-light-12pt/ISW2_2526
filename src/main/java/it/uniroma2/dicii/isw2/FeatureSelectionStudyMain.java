package it.uniroma2.dicii.isw2;

/**
 * Compares the feature selection techniques for each of the configured models, to choose the attributes
 * each of them is best trained on.
 * <p>
 * An entry point of its own, as {@link BalancingStudyMain} is and for the same reason: which attributes
 * to train a model on is a choice, and a choice has to be made before the test releases are read, out of
 * releases that are not among them.
 */
public class FeatureSelectionStudyMain {

    public static void main(String[] args) {
        FeatureSelectionStudyWorkflow workflow = new FeatureSelectionStudyWorkflow();
        workflow.execute();
    }

}
