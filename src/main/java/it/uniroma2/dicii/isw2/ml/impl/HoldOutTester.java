package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.ModelFactory;
import it.uniroma2.dicii.isw2.ml.ModelTester;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import it.uniroma2.dicii.isw2.ml.model.EvaluationPhase;
import lombok.extern.slf4j.Slf4j;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;

import java.time.Duration;
import java.time.Instant;

/**
 * Trains a model on the whole training set and predicts the held-out test releases.
 * <p>
 * The model is trained on <b>all</b> of the training set, not on nine tenths of it as each fold of the
 * validation was: the folds exist to leave rows to be tested on, and here the rows to be tested on are
 * a different set of releases entirely. There is no reason left to hold any of the training data back,
 * and a model trained on more of it is the one that would actually be shipped.
 * <p>
 * Note that this is the only place the test rows are read, and that they are read exactly once. Every
 * choice — which models to run, how large a forest, how many neighbours — was made before this point
 * and out of the validation figures alone. Re-running this phase after changing one of them on the
 * strength of what it reported would fit the model to the test set by hand, and the figure would stop
 * being an estimate of anything.
 */
@Slf4j
public class HoldOutTester implements ModelTester {

    private final ModelFactory factory;
    private final MlSettings settings;

    /**
     * How many folds the inference phase cuts, which is none: it trains once and predicts once.
     */
    private static final int NO_FOLDS = 0;

    public HoldOutTester(ModelFactory factory, MlSettings settings) {
        this.factory = factory;
        this.settings = settings;
    }

    @Override
    public EvaluationOutcome test(Instances training, Instances test, ClassifierKind classifier)
            throws MlException {
        if (!training.equalHeaders(test)) {
            throw new MlException("The training and test rows do not describe the same columns: "
                    + training.equalHeadersMsg(test));
        }
        int positiveClassIndex = EvaluationOutcomes.positiveClassIndex(test, settings.positiveClass());
        Instant start = Instant.now();
        log.info("Training {} on {} rows and predicting {} held-out ones", classifier.getLabel(),
                training.numInstances(), test.numInstances());
        Evaluation evaluation = trainAndPredict(training, test, classifier);
        EvaluationOutcome outcome = EvaluationOutcomes.from(evaluation, classifier,
                EvaluationPhase.TEST, NO_FOLDS, positiveClassIndex,
                Duration.between(start, Instant.now()));
        log.info("{} predicted the held-out releases at accuracy {} and AUC {} on the buggy class in {}s",
                classifier.getLabel(), EvaluationOutcomes.rounded(outcome.accuracy()),
                EvaluationOutcomes.rounded(outcome.areaUnderRoc()), outcome.elapsed().toSeconds());
        return outcome;
    }

    /**
     * Trains the model and asks it about every test row.
     *
     * @param training   the rows to train on
     * @param test       the rows to predict
     * @param classifier which model to train, named in the report of a failure
     * @return its predictions of the test rows
     * @throws MlException if the model cannot be trained or asked
     */
    private Evaluation trainAndPredict(Instances training, Instances test, ClassifierKind classifier)
            throws MlException {
        try {
            Classifier model = factory.build(classifier);
            model.buildClassifier(training);
            Evaluation evaluation = new Evaluation(training);
            evaluation.evaluateModel(model, test);
            return evaluation;
        } catch (Exception e) {
            // Weka reports everything from an unbuildable classifier to an empty test set as a bare
            // Exception, which leaves nothing more specific to catch
            throw new MlException("Unable to test " + classifier.getLabel()
                    + " on the held-out releases", e);
        }
    }

}
