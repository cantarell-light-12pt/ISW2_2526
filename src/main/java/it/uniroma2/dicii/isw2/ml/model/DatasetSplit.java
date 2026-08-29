package it.uniroma2.dicii.isw2.ml.model;

import weka.core.Instances;

import java.util.List;

/**
 * The dataset cut in two: the releases a model is trained on, and the later ones it is finally asked
 * about.
 *
 * @param training         the rows of the earlier releases, which the cross-validation folds are cut
 *                         from and the final model is trained on
 * @param test             the rows of the later releases, which nothing reads until the inference phase
 * @param trainingReleases which releases went into the training set, in the order they were published
 * @param testReleases     which releases went into the test set
 */
public record DatasetSplit(Instances training, Instances test, List<String> trainingReleases,
                           List<String> testReleases) {

    public DatasetSplit {
        // Copied, so that the lists the split was described with cannot be written into behind it
        trainingReleases = List.copyOf(trainingReleases);
        testReleases = List.copyOf(testReleases);
    }

}
