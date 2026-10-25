package it.uniroma2.dicii.isw2;

/**
 * Compares the balancing techniques for each of the configured models, to choose the one each of them
 * is best trained with.
 * <p>
 * A third entry point rather than a phase of {@link MlWorkflow}, because it answers a question that
 * workflow has to have answered already: which technique to measure a model under is a choice, and a
 * choice has to be made before the test releases are read, out of releases that are not among them.
 */
public class BalancingStudyMain {

    public static void main(String[] args) {
        BalancingStudyWorkflow workflow = new BalancingStudyWorkflow();
        workflow.execute();
    }

}
