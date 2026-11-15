package it.uniroma2.dicii.isw2.ml;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.BalancingKind;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.FeatureSelectionKind;
import it.uniroma2.dicii.isw2.ml.model.ModelConfiguration;
import it.uniroma2.dicii.isw2.properties.PropertiesManager;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * The knobs the evaluation is run with, read once out of {@code project.properties}.
 * <p>
 * Which models are measured is one of them rather than something the code names, because the comparison
 * is meant to grow: the workflow loops over this list, so running the three classifiers instead of one
 * is an edit to the properties file and no edit at all here. How the training rows are balanced, and
 * which of their attributes a model is trained on, are lists for the same reason, and every model is
 * measured under every pairing of a technique of the one with a technique of the other.
 *
 * @param classifiers                the models to train and measure, in the order they are reported
 * @param balancingTechniques        how the two classes of the training rows are brought to the same
 *                                   size, one technique per measurement of each model, in the order they
 *                                   are reported
 * @param featureSelectionTechniques which of the attributes of the training rows a model is trained on,
 *                                   one technique per measurement of each model and balancing, in the
 *                                   order they are reported
 * @param trainingFraction           which share of the releases a model is trained on, the rest being
 *                                   held back to test it on
 * @param folds                      how many folds each of them is cross-validated over
 * @param seed                       what the folds and the samples are drawn with, so that two runs agree
 * @param positiveClass              the label a per-class measure is read of, as it reads in the dataset
 * @param threads                    how many threads a model that can spread itself may use
 * @param randomForestIterations     how many trees a forest is grown with
 * @param ibkNeighbours              how many neighbours a nearest-neighbour classifier votes among
 * @param infoGainAttributes         how many attributes a selection by information gain keeps
 */
@Slf4j
public record MlSettings(List<ClassifierKind> classifiers, List<BalancingKind> balancingTechniques,
                         List<FeatureSelectionKind> featureSelectionTechniques, double trainingFraction,
                         int folds, int seed, String positiveClass, int threads, int randomForestIterations,
                         int ibkNeighbours, int infoGainAttributes) {

    private static final String PREFIX = "project.ml.";

    public MlSettings {
        if (classifiers == null || classifiers.isEmpty()) {
            throw new IllegalArgumentException("There is no model to measure: at least one classifier "
                    + "has to be configured through '" + PREFIX + "classifiers'");
        }
        // Copied, so that the list the settings were built from cannot be written into behind them
        classifiers = List.copyOf(classifiers);
        // Named rather than defaulted to NONE, so that "the rows were not balanced" is something the
        // settings were told and never something they fell back on
        if (balancingTechniques == null || balancingTechniques.isEmpty()) {
            throw new IllegalArgumentException("How the training rows are balanced has to be named "
                    + "through '" + PREFIX + "balancing', even when the answer is "
                    + BalancingKind.NONE.getLabel());
        }
        balancingTechniques = List.copyOf(balancingTechniques);
        // Named for the same reason: "every attribute" is a technique, and has to be chosen as one
        if (featureSelectionTechniques == null || featureSelectionTechniques.isEmpty()) {
            throw new IllegalArgumentException("Which attributes a model is trained on has to be named "
                    + "through '" + PREFIX + "featureSelection', even when the answer is "
                    + FeatureSelectionKind.NONE.getLabel());
        }
        featureSelectionTechniques = List.copyOf(featureSelectionTechniques);
        // Written as what is rejected rather than as the negation of what is accepted, so that it reads
        // as the sentence below it. NaN fails every comparison, so it falls through neither test and is
        // rejected here as well
        if (!(trainingFraction > 0 && trainingFraction < 1)) {
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
        if (infoGainAttributes < 1) {
            throw new IllegalArgumentException("A selection by information gain keeps at least one "
                    + "attribute, not " + infoGainAttributes);
        }
    }

    /**
     * @return every model to measure, under every pairing of a balancing technique and a feature
     * selection — the models in the order they are listed, and for each of them the techniques in the
     * order they are, the feature selection varying fastest. That is the order they are reported in, so
     * the rows a choice is made among sit next to each other, and a tie goes to the one listed first
     */
    public List<ModelConfiguration> configurations() {
        List<ModelConfiguration> configurations = new ArrayList<>(
                classifiers.size() * balancingTechniques.size() * featureSelectionTechniques.size());
        for (ClassifierKind classifier : classifiers) {
            for (BalancingKind balancing : balancingTechniques) {
                for (FeatureSelectionKind selection : featureSelectionTechniques) {
                    configurations.add(new ModelConfiguration(classifier, balancing, selection));
                }
            }
        }
        return configurations;
    }

    /**
     * @param fraction which share of the releases to train on instead
     * @return these settings, cutting the releases there
     * @throws IllegalArgumentException if the share leaves no release on one of the two sides
     */
    public MlSettings withTrainingFraction(double fraction) {
        return new MlSettings(classifiers, balancingTechniques, featureSelectionTechniques, fraction, folds,
                seed, positiveClass, threads, randomForestIterations, ibkNeighbours, infoGainAttributes);
    }

    /**
     * @return which share of the training releases the balancing study trains on, the rest of them being
     * what each technique is compared over — two thirds when the property leaves it open
     */
    public static double balancingStudyTrainingFraction() {
        return number("balancingStudy.trainingFraction", 0.66);
    }

    /**
     * @return which share of the releases of the whole dataset the trimmed one keeps, the earliest
     * first — a third when the property leaves it open
     */
    public static double trimFraction() {
        return number("trimFraction", 0.33);
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
                configuredBalancing(),
                configuredFeatureSelection(),
                number("trainingFraction", 0.67),
                integer("folds", 10),
                integer("seed", 1),
                stringify("positiveClass", "1"),
                configuredThreads(),
                integer("randomForest.iterations", 100),
                integer("ibk.neighbours", 3),
                integer("featureSelection.infoGainAttributes", 10));
    }

    /**
     * @return the models to measure, as the comma-separated list of names the property holds
     * @throws MlException if one of those names belongs to no classifier
     */
    private static List<ClassifierKind> configuredClassifiers() throws MlException {
        return labels("classifiers", ClassifierKind.RANDOM_FOREST.getLabel(), ClassifierKind::from);
    }

    /**
     * @return the techniques the two classes of the training rows are brought to the same size with, as
     * the comma-separated list of names the property holds — the rows as they are when it leaves it
     * open, which is what the other two are read against
     * @throws MlException if one of those names belongs to no technique
     */
    private static List<BalancingKind> configuredBalancing() throws MlException {
        return labels("balancing", BalancingKind.NONE.getLabel(), BalancingKind::from);
    }

    /**
     * @return which of the attributes of the training rows a model is trained on, as the comma-separated
     * list of names the property holds — every one of them when it leaves it open, which is what the
     * selections are read against
     * @throws MlException if one of those names belongs to no technique
     */
    private static List<FeatureSelectionKind> configuredFeatureSelection() throws MlException {
        return labels("featureSelection", FeatureSelectionKind.NONE.getLabel(), FeatureSelectionKind::from);
    }

    /**
     * Reads a comma-separated list of names out of the properties, in the order it is written, skipping
     * the blank entries a trailing comma leaves.
     *
     * @param key      the property, without its prefix
     * @param fallback the list to read when the property leaves it open
     * @param resolver what turns one name into what it names
     * @param <T>      what the names name
     * @return what they name
     * @throws MlException if one of the names names nothing
     */
    private static <T> List<T> labels(String key, String fallback, LabelResolver<T> resolver)
            throws MlException {
        List<T> resolved = new ArrayList<>();
        for (String label : stringify(key, fallback).split(",")) {
            if (!label.isBlank()) {
                resolved.add(resolver.resolve(label.trim()));
            }
        }
        return resolved;
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

    /**
     * Turns the name an option reads as in the configuration into the option, as the {@code from} of
     * each of the enums the settings list does.
     *
     * @param <T> the option
     */
    @FunctionalInterface
    private interface LabelResolver<T> {

        /**
         * @param label the name to resolve
         * @return the option it names
         * @throws MlException if it names none
         */
        T resolve(String label) throws MlException;

    }

}
