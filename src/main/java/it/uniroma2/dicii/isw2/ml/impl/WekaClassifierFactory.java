package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.BalancerFactory;
import it.uniroma2.dicii.isw2.ml.FeatureSelector;
import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.ModelFactory;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.ModelConfiguration;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.RandomForest;
import weka.core.neighboursearch.KDTree;

/**
 * Builds the Weka classifiers the comparison is run over.
 * <p>
 * Two of the three are configured, and the third is not: a forest is grown to a configured size and a
 * nearest-neighbour classifier votes among a configured number of neighbours, while naive Bayes has
 * nothing to set that would not change what it is.
 * <p>
 * Every one of them comes wrapped in a {@link BalancedClassifier}, the rows left as they are included:
 * a model is then trained through the same path whatever technique it was configured with, and the
 * figures of two techniques differ by the technique and by nothing else.
 * <p>
 * The feature selection goes around the balancing, not inside it: the attributes are chosen out of the
 * training rows as they are, and it is the rows reduced to them that are balanced. Choosing them out of
 * balanced rows instead would have SMOTE's synthetic rows, interpolated over every attribute, vote on
 * which attributes the real ones are told apart by. A model keeping every attribute is not wrapped at
 * all, so that it is, to the last digit, the model measured before selection existed.
 */
public class WekaClassifierFactory implements ModelFactory {

    private final MlSettings settings;
    private final BalancerFactory balancers;
    private final FeatureSelector selector;

    public WekaClassifierFactory(MlSettings settings) {
        this(settings, new WekaBalancerFactory(settings), new WekaFeatureSelector(settings));
    }

    public WekaClassifierFactory(MlSettings settings, BalancerFactory balancers, FeatureSelector selector) {
        this.settings = settings;
        this.balancers = balancers;
        this.selector = selector;
    }

    @Override
    public Classifier build(ModelConfiguration configuration) {
        Classifier balanced = new BalancedClassifier(model(configuration.classifier()),
                balancers.build(configuration.balancing()));
        return selector.selecting(balanced, configuration.featureSelection());
    }

    /**
     * @param kind which model to build
     * @return it, configured and untrained, before anything is wrapped around it
     */
    private Classifier model(ClassifierKind kind) {
        return switch (kind) {
            case RANDOM_FOREST -> randomForest();
            case NAIVE_BAYES -> new NaiveBayes();
            case IBK -> nearestNeighbours();
        };
    }

    /**
     * @return a forest of the configured size, seeded so that two runs grow the same one and free to
     * grow its trees on every thread the settings allow — the models are measured one after another, so
     * nothing is competing with it for them
     */
    private Classifier randomForest() {
        RandomForest forest = new RandomForest();
        forest.setNumIterations(settings.randomForestIterations());
        forest.setNumExecutionSlots(settings.threads());
        forest.setSeed(settings.seed());
        return forest;
    }

    /**
     * @return a nearest-neighbour classifier searching through a k-d tree rather than through the linear
     * scan it defaults to. Every prediction it makes is a search through the training set, of which a
     * training fold of the trimmed dataset holds some four thousand rows, and a validation asks it for
     * one prediction per row of the dataset
     */
    private Classifier nearestNeighbours() {
        IBk classifier = new IBk();
        classifier.setKNN(settings.ibkNeighbours());
        classifier.setNearestNeighbourSearchAlgorithm(new KDTree());
        return classifier;
    }

}
