package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The instances the cross-validation is tried on: a handful of rows whose label one of the attributes
 * gives away and the other two say nothing about.
 * <p>
 * Small and synthetic on purpose. What these tests check is that a model is trained, tested and read
 * off the right class, which a set whose answer is known settles in milliseconds; running them over the
 * twenty thousand rows of the real dataset would check the same thing and turn {@code mvn test} into a
 * coffee break.
 */
final class SyntheticInstances {

    /**
     * The attribute the label can be read off.
     */
    static final String SIGNAL = "signal";

    /**
     * The attributes it cannot.
     */
    static final List<String> NOISE = List.of("noise1", "noise2");

    /**
     * How the label reads, as the dataset writes it. The not-buggy rows come first, so that the buggy
     * class is the <i>second</i> value of the attribute — which is what these tests want, since code
     * that assumes it is the first is exactly the mistake worth catching.
     */
    static final String BUGGY = "1";
    static final String NOT_BUGGY = "0";

    private SyntheticInstances() {
        // Builds instances, and holds none
    }

    /**
     * @param rows how many rows to build, half of them buggy
     * @return them, labelled, with the signal attribute separating the two classes cleanly
     */
    static Instances separable(int rows) {
        return build(rows, rows / 2, true);
    }

    /**
     * Builds the shape the real dataset has: far more not-buggy rows than buggy ones, and nothing in
     * the attributes to tell them apart. Anything trained on it can do no better than answer "not
     * buggy" to everything, which is the case where reading a per-class figure off the wrong class
     * shows up — that answer scores a recall of 1 on one class and of 0 on the other.
     *
     * @param rows      how many rows to build
     * @param buggyRows how many of them are buggy, which should be the smaller share
     * @return them, labelled and unlearnable
     */
    static Instances imbalanced(int rows, int buggyRows) {
        return build(rows, buggyRows, false);
    }

    /**
     * @param rows       how many rows to build
     * @param buggyRows  how many of them to label buggy
     * @param separating whether the signal attribute gives the label away
     * @return the rows, labelled
     */
    private static Instances build(int rows, int buggyRows, boolean separating) {
        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute(SIGNAL));
        NOISE.forEach(name -> attributes.add(new Attribute(name)));
        ArrayList<String> labels = new ArrayList<>(List.of(NOT_BUGGY, BUGGY));
        attributes.add(new Attribute("Buggy", labels));
        Instances data = new Instances("synthetic", attributes, rows);
        data.setClassIndex(attributes.size() - 1);
        Random random = new Random(7);
        for (int i = 0; i < rows; i++) {
            // Spreads exactly buggyRows of them evenly through the file, rather than leaving the buggy
            // ones in a block that a fold could hold all of
            boolean buggy = i * buggyRows / rows < (i + 1) * buggyRows / rows;
            double[] values = new double[attributes.size()];
            values[0] = (separating && buggy ? 10 : 0) + random.nextDouble();
            for (int noise = 0; noise < NOISE.size(); noise++) {
                values[1 + noise] = random.nextDouble() * 10;
            }
            values[attributes.size() - 1] = labels.indexOf(buggy ? BUGGY : NOT_BUGGY);
            data.add(new DenseInstance(1.0, values));
        }
        return data;
    }

    /**
     * Builds the dataset as it comes off the file, with the columns naming each row still on it, which
     * is what a release-aware split is cut on.
     *
     * @param releases       the releases to build, in the order the file holds them
     * @param rowsPerRelease how many classes each of them holds
     * @param contiguous     whether the rows of a release sit together, as a file written release by
     *                       release holds them, or are interleaved as no such file would
     * @return the rows, with a Version and a ClassName column in front of the metrics
     */
    static Instances withReleases(List<String> releases, int rowsPerRelease, boolean contiguous) {
        ArrayList<String> classNames = new ArrayList<>();
        for (int i = 0; i < rowsPerRelease; i++) {
            classNames.add("sample.Class" + i);
        }
        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("Version", new ArrayList<>(releases)));
        attributes.add(new Attribute("ClassName", classNames));
        attributes.add(new Attribute(SIGNAL));
        NOISE.forEach(name -> attributes.add(new Attribute(name)));
        ArrayList<String> labels = new ArrayList<>(List.of(NOT_BUGGY, BUGGY));
        attributes.add(new Attribute("Buggy", labels));

        Instances data = new Instances("synthetic", attributes, releases.size() * rowsPerRelease);
        data.setClassIndex(attributes.size() - 1);
        Random random = new Random(7);
        // Interleaved means walking the classes on the outside, so each release's rows end up scattered
        int outer = contiguous ? releases.size() : rowsPerRelease;
        int inner = contiguous ? rowsPerRelease : releases.size();
        for (int i = 0; i < outer; i++) {
            for (int j = 0; j < inner; j++) {
                int release = contiguous ? i : j;
                int className = contiguous ? j : i;
                boolean buggy = (release + className) % 3 == 0;
                double[] values = new double[attributes.size()];
                values[0] = release;
                values[1] = className;
                values[2] = (buggy ? 10 : 0) + random.nextDouble();
                for (int noise = 0; noise < NOISE.size(); noise++) {
                    values[3 + noise] = random.nextDouble() * 10;
                }
                values[attributes.size() - 1] = labels.indexOf(buggy ? BUGGY : NOT_BUGGY);
                data.add(new DenseInstance(1.0, values));
            }
        }
        return data;
    }

    /**
     * @return settings a test can run under: the smallest validation there is, since forty rows split
     * ten ways would decide nothing, and a single thread, since a test has no machine to itself
     */
    static MlSettings settings() {
        return settings(ClassifierKind.NAIVE_BAYES, BUGGY);
    }

    /**
     * @param classifier    the model to measure
     * @param positiveClass the class the per-class figures are read of
     * @return settings a test can run under
     */
    static MlSettings settings(ClassifierKind classifier, String positiveClass) {
        return new MlSettings(List.of(classifier), 0.67, 2, 1, positiveClass, 1, 10, 1);
    }

}
