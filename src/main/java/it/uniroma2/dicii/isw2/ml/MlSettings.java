package it.uniroma2.dicii.isw2.ml;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.properties.PropertiesManager;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * The knobs the evaluation is run with, read once out of {@code project.properties}.
 * <p>
 * Which models are measured is one of them rather than something the code names, because the comparison
 * is meant to grow: the workflow loops over this list, so running the three classifiers instead of one
 * is an edit to the properties file and no edit at all here.
 *
 * @param classifiers            the models to train and measure, in the order they are reported
 * @param trainingFraction       which share of the releases a model is trained on, the rest being held
 *                               back to test it on
 * @param folds                  how many folds each of them is cross-validated over
 * @param seed                   what the folds are drawn with, so that two runs agree
 * @param positiveClass          the label a per-class measure is read of, as it reads in the dataset
 * @param threads                how many threads a model that can spread itself may use
 * @param randomForestIterations how many trees a forest is grown with
 * @param ibkNeighbours          how many neighbours a nearest-neighbour classifier votes among
 */
@Slf4j
public record MlSettings(List<ClassifierKind> classifiers, double trainingFraction, int folds, int seed,
                         String positiveClass, int threads, int randomForestIterations,
                         int ibkNeighbours) {

    private static final String PREFIX = "project.ml.";

    public MlSettings {
        if (classifiers == null || classifiers.isEmpty()) {
            throw new IllegalArgumentException("There is no model to measure: at least one classifier "
                    + "has to be configured through '" + PREFIX + "classifiers'");
        }
        // Copied, so that the list the settings were built from cannot be written into behind them
        classifiers = List.copyOf(classifiers);
        if (!(trainingFraction >= 0 && trainingFraction <= 1)) {
            throw new IllegalArgumentException("A model is trained on some of the releases and tested "
                    + "on the rest, so the training fraction lies strictly between 0 and 1, not "
                    + trainingFraction);
        }
        if (folds < 2) {
            throw new IllegalArgumentException("A cross-validation needs at least two folds, not " + folds);
        }
        if (positiveClass == null || positiveClass.isBlank()) {
            throw new IllegalArgumentException("The buggy class has to be named, since precision, "
                    + "recall, F1 and AUC are read of it alone");
        }
        if (threads < 1) {
            throw new IllegalArgumentException("A model is trained on at least one thread, not " + threads);
        }
        if (randomForestIterations < 1 || ibkNeighbours < 1) {
            throw new IllegalArgumentException("A forest is grown with at least one tree and a vote is "
                    + "taken among at least one neighbour, not " + randomForestIterations + " and "
                    + ibkNeighbours);
        }
    }

    /**
     * Reads the settings out of the properties of the project, falling back on the values below for any
     * of them the file does not carry.
     *
     * @return the knobs the evaluation is run with
     * @throws MlException if the configured list of classifiers names one that does not exist
     */
    public static MlSettings load() throws MlException {
        return new MlSettings(
                configuredClassifiers(),
                number("trainingFraction", 0.67),
                integer("folds", 10),
                integer("seed", 1),
                stringify("positiveClass", "1"),
                configuredThreads(),
                integer("randomForest.iterations", 100),
                integer("ibk.neighbours", 3));
    }

    /**
     * @return the models to measure, as the comma-separated list of names the property holds
     * @throws MlException if one of those names belongs to no classifier
     */
    private static List<ClassifierKind> configuredClassifiers() throws MlException {
        String configured = stringify("classifiers", ClassifierKind.RANDOM_FOREST.getLabel());
        List<ClassifierKind> classifiers = new ArrayList<>();
        for (String label : configured.split(",")) {
            if (!label.isBlank()) {
                classifiers.add(ClassifierKind.from(label.trim()));
            }
        }
        return classifiers;
    }

    /**
     * @return how many threads a model may spread itself over, one per available processor when the
     * property leaves it open. Nothing else runs beside it — the models are measured one after another
     * — so the whole machine is its to use
     */
    private static int configuredThreads() {
        int configured = integer("threads", 0);
        return configured > 0 ? configured : Runtime.getRuntime().availableProcessors();
    }

    private static String stringify(String key, String fallback) {
        String value = PropertiesManager.getInstance().getProperty(PREFIX + key);
        if (value == null || value.isBlank()) {
            log.debug("No '{}{}' configured: falling back on {}", PREFIX, key, fallback);
            return fallback;
        }
        return value.trim();
    }

    private static int integer(String key, int fallback) {
        String value = stringify(key, String.valueOf(fallback));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("'{}{}' reads '{}', which is no whole number: falling back on {}",
                    PREFIX, key, value, fallback);
            return fallback;
        }
    }

    private static double number(String key, double fallback) {
        String value = stringify(key, String.valueOf(fallback));
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("'{}{}' reads '{}', which is no number: falling back on {}",
                    PREFIX, key, value, fallback);
            return fallback;
        }
    }

}
