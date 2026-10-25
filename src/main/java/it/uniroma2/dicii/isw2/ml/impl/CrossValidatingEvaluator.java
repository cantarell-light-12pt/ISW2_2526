package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.ModelEvaluator;
import it.uniroma2.dicii.isw2.ml.ModelFactory;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.BalancingKind;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import it.uniroma2.dicii.isw2.ml.model.EvaluationPhase;
import lombok.extern.slf4j.Slf4j;
import weka.classifiers.Evaluation;
import weka.core.Instances;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

/**
 * Measures a model by cross-validating it over the rows it is given, which are the <b>training</b> half
 * of the split and never the test half.
 * <p>
 * The folds are cut by {@code Evaluation.crossValidateModel}, which randomises the rows, stratifies
 * them so that every fold holds the buggy share of the whole, and then trains on nine tenths and tests
 * on the tenth, accumulating the predictions of all ten into a single confusion matrix. That is the
 * same routine the Weka Explorer runs, so a figure reported here and one read off the Explorer are the
 * answers of the same engine rather than two implementations that ought to agree.
 * <p>
 * What this phase is for is choosing between models, and the reason it is confined to the training set
 * is that choosing is itself a way of reading the data: a model picked because it scored best on the
 * test set has been fitted to it by hand, and the figure that made it win is no longer an estimate of
 * anything. The test set is read once, by {@link HoldOutTester}, after everything has been decided.
 * <p>
 * Balancing happens inside every fold and of its training part alone, without anything here asking for
 * it: the model the factory builds balances whatever it is trained on, and each fold trains a copy of it
 * on its nine tenths. The tenth it is measured on is the rows as they are, as a test set has to be.
 */
@Slf4j
public class CrossValidatingEvaluator implements ModelEvaluator {

    private final ModelFactory factory;
    private final MlSettings settings;

    public CrossValidatingEvaluator(ModelFactory factory, MlSettings settings) {
        this.factory = factory;
        this.settings = settings;
    }

    @Override
    public EvaluationOutcome evaluate(Instances data, ClassifierKind classifier, BalancingKind balancing)
            throws MlException {
        int folds = settings.folds();
        int positiveClassIndex = EvaluationOutcomes.positiveClassIndex(data, settings.positiveClass());
        // Its own copy: the validation shuffles what it is given, and the rows belong to the caller
        Instances working = new Instances(data);
        Instant start = Instant.now();
        log.info("Validating {} balanced by {} over {} folds of {} training rows", classifier.getLabel(),
                balancing.getLabel(), folds, working.numInstances());
        Evaluation evaluation = crossValidate(working, classifier, balancing, folds);
        EvaluationOutcome outcome = EvaluationOutcomes.from(evaluation, classifier, balancing,
                EvaluationPhase.VALIDATION, folds, positiveClassIndex,
                Duration.between(start, Instant.now()));
        log.info("{} balanced by {} validated at accuracy {} and AUC {} on the buggy class in {}s",
                classifier.getLabel(), balancing.getLabel(), EvaluationOutcomes.rounded(outcome.accuracy()),
                EvaluationOutcomes.rounded(outcome.areaUnderRoc()), outcome.elapsed().toSeconds());
        return outcome;
    }

    /**
     * Runs the validation itself.
     *
     * @param working    the rows to validate over, which this shuffles
     * @param classifier which model to train, named in the report of a failure
     * @param balancing  how to balance the training part of each fold
     * @param folds      how many folds to cut
     * @return the predictions of every fold, accumulated
     * @throws MlException if the model cannot be trained or tested
     */
    private Evaluation crossValidate(Instances working, ClassifierKind classifier,
                                     BalancingKind balancing, int folds) throws MlException {
        try {
            Evaluation evaluation = new Evaluation(working);
            evaluation.crossValidateModel(factory.build(classifier, balancing), working, folds,
                    new Random(settings.seed()));
            return evaluation;
        } catch (Exception e) {
            // Weka reports everything from an unbuildable classifier to a fold holding a single class
            // as a bare Exception, which leaves nothing more specific to catch
            throw new MlException("Unable to validate " + classifier.getLabel() + " balanced by "
                    + balancing.getLabel() + " over " + folds + " folds", e);
        }
    }

}
