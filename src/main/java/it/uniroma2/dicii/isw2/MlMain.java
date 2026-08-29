package it.uniroma2.dicii.isw2;

/**
 * Trains and measures the models over the dataset {@link Main} builds.
 * <p>
 * A second entry point rather than a ninth step of {@link Workflow}: building the dataset clones a
 * repository, checks out every release and lets four tools loose on each of them, which takes hours,
 * while this reads the file that came out. Tying the two together would mean re-mining the project
 * every time a classifier is added to the comparison.
 */
public class MlMain {

    public static void main(String[] args) {
        MlWorkflow workflow = new MlWorkflow();
        workflow.execute();
    }

}
