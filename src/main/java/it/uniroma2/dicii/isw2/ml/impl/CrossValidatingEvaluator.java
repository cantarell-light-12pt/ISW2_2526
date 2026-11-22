package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.ModelEvaluator;
import it.uniroma2.dicii.isw2.ml.ModelFactory;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import it.uniroma2.dicii.isw2.ml.model.ModelConfiguration;
import lombok.extern.slf4j.Slf4j;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.evaluation.NominalPrediction;
import weka.classifiers.evaluation.Prediction;
import weka.core.Instance;
import weka.core.Instances;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Measures a model by cross-validating it over the rows it is given, which are those of the trimmed
 * dataset.
 * <p>
 * The folds are cut by {@code Evaluation.crossValidateModel}, which randomises the rows, stratifies
 * them so that every fold holds the buggy share of the whole, and then trains on nine tenths and tests
 * on the tenth, accumulating the predictions of all ten into a single confusion matrix. That is the
 * same routine the Weka Explorer runs, so a figure reported here and one read off the Explorer are the
 * answers of the same engine rather than two implementations that ought to agree.
 * <p>
 * Balancing happens inside every fold and of its training part alone, without anything here asking for
 * it: the model the factory builds balances whatever it is trained on, and each fold trains a copy of it
 * on its nine tenths. The tenth it is measured on is the rows as they are, as a test set has to be.
 * The feature selection is placed the same way and for the same reason: each fold chooses the
 * attributes out of its nine tenths, so the ten folds may well keep ten different subsets, and none of
 * them was chosen with the tenth it is measured on in hand.
 * <p>
 * The folds are cut here rather than by {@code Evaluation.crossValidateModel}, and cut exactly as it cuts
 * them: the same shuffle and stratification, the same draws from the same {@code Random}, the same copy
 * of the model per fold and the same {@code evaluateModel} on its tenth, so every figure Weka computes
 * comes out to the last digit as it would. What that buys is knowing <i>which</i> row each prediction
 * was of: Weka records them fold after fold in the order of the tenth held out, and this pairs each with
 * the size of its class, which is what NPofB20 is read off. Its ranking is pooled over the ten tenths,
 * as Weka pools the predictions an AUC is read off: every row is ranked by the one model that never
 * saw it.
 */
@Slf4j
public class CrossValidatingEvaluator implements ModelEvaluator {

    /**
     * Which share of the total size a reviewer is taken to afford to inspect, the 20 NPofB20 is named
     * after — fixed rather than configured for that reason.
     */
    private static final double NPOFB_EFFORT_SHARE = 0.20;

    private final ModelFactory factory;
    private final MlSettings settings;

    public CrossValidatingEvaluator(ModelFactory factory, MlSettings settings) {
        this.factory = factory;
        this.settings = settings;
    }

    @Override
    public EvaluationOutcome evaluate(Instances data, ModelConfiguration configuration)
            throws MlException {
        int folds = settings.folds();
        int positiveClassIndex = EvaluationOutcomes.positiveClassIndex(data, settings.positiveClass());
        int sizeIndex = sizedRowsIndex(data);
        // Its own copy: the validation shuffles what it is given, and the rows belong to the caller
        Instances working = new Instances(data);
        Instant start = Instant.now();
        log.info("Validating {} over {} folds of {} rows", configuration.describe(), folds,
                working.numInstances());
        List<RankedRow> heldOut = new ArrayList<>(working.numInstances());
        Ranking ranking = new Ranking(positiveClassIndex, sizeIndex, heldOut);
        Evaluation evaluation = crossValidate(working, configuration, folds, ranking);
        double npofb20 = EffortAwareRanking.buggyShareFound(heldOut, NPOFB_EFFORT_SHARE);
        EvaluationOutcome outcome = EvaluationOutcomes.from(evaluation, configuration, folds,
                positiveClassIndex, npofb20, Duration.between(start, Instant.now()));
        log.info("{} validated at accuracy {}, AUC {} and NPofB20 {} on the buggy class in {}s",
                configuration.describe(), EvaluationOutcomes.rounded(outcome.accuracy()),
                EvaluationOutcomes.rounded(outcome.areaUnderRoc()), EvaluationOutcomes.rounded(npofb20),
                outcome.elapsed().toSeconds());
        return outcome;
    }

    /**
     * Resolves the attribute the size of a class is read off, and checks every row has one: a class of
     * unknown size cannot be placed in a ranking by density, nor charged to the budget of an inspection.
     *
     * @param data the rows to validate over
     * @return the index of the size attribute
     * @throws MlException if there is no such attribute, or a row leaves it blank
     */
    private int sizedRowsIndex(Instances data) throws MlException {
        int sizeIndex = EvaluationOutcomes.sizeIndex(data, settings.sizeAttribute());
        for (Instance row : data) {
            if (row.isMissing(sizeIndex)) {
                throw new MlException("A row leaves '" + settings.sizeAttribute() + "' blank, so it cannot "
                        + "be ranked by bug density for NPofB20");
            }
        }
        return sizeIndex;
    }

    /**
     * Runs the validation itself, cutting the folds as {@code Evaluation.crossValidateModel} does.
     *
     * @param working       the rows to validate over, which this shuffles
     * @param configuration which model to train, how to balance the training part of each fold and
     *                      which of its attributes to keep
     * @param folds         how many folds to cut
     * @param ranking       where each row held out is collected, with what the model answered of it
     * @return the predictions of every fold, accumulated
     * @throws MlException if the model cannot be trained or tested
     */
    private Evaluation crossValidate(Instances working, ModelConfiguration configuration, int folds,
                                     Ranking ranking) throws MlException {
        try {
            Evaluation evaluation = new Evaluation(working);
            Classifier model = factory.build(configuration);
            Random random = new Random(settings.seed());
            working.randomize(random);
            working.stratify(folds);
            for (int fold = 0; fold < folds; fold++) {
                Instances train = working.trainCV(folds, fold, random);
                evaluation.setPriors(train);
                Classifier copy = AbstractClassifier.makeCopy(model);
                copy.buildClassifier(train);
                Instances test = working.testCV(folds, fold);
                int recorded = recordedPredictions(evaluation);
                evaluation.evaluateModel(copy, test);
                ranking.collect(evaluation.predictions(), recorded, test);
            }
            return evaluation;
        } catch (Exception e) {
            // Weka reports everything from an unbuildable classifier to a fold holding a single class
            // as a bare Exception, which leaves nothing more specific to catch
            throw new MlException("Unable to validate " + configuration.describe() + " over " + folds
                    + " folds", e);
        }
    }

    /**
     * @param evaluation the predictions accumulated so far
     * @return how many there are, none before the first fold has been tested
     */
    private static int recordedPredictions(Evaluation evaluation) {
        List<Prediction> predictions = evaluation.predictions();
        return predictions == null ? 0 : predictions.size();
    }

    /**
     * Collects, fold after fold, every row held out with the probability the model trained without it
     * gave the buggy class, its size and whether it was buggy.
     *
     * @param positiveClassIndex which value of the label is the buggy class
     * @param sizeIndex          which attribute the size of a class is read off
     * @param rows               where the rows are collected
     */
    private record Ranking(int positiveClassIndex, int sizeIndex, List<RankedRow> rows) {

        /**
         * @param predictions every prediction recorded so far, those of the fold just tested last
         * @param from        how many had been recorded before it was
         * @param test        the tenth it held out, in the order its predictions were recorded
         * @throws IllegalStateException if the fold recorded a prediction for other than each of its rows,
         *                               which would pair the rows with the answers about others, or
         *                               one without a distribution to rank them by
         */
        void collect(List<Prediction> predictions, int from, Instances test) {
            int recorded = predictions == null ? 0 : predictions.size() - from;
            if (recorded != test.numInstances()) {
                throw new IllegalStateException("A fold of " + test.numInstances() + " rows recorded "
                        + recorded + " predictions, so they cannot be paired with the rows they are of");
            }
            for (int i = 0; i < recorded; i++) {
                if (!(predictions.get(from + i) instanceof NominalPrediction prediction)) {
                    throw new IllegalStateException("A prediction of a nominal label carries no "
                            + "distribution over its values to rank the rows by");
                }
                Instance row = test.instance(i);
                rows.add(new RankedRow(prediction.distribution()[positiveClassIndex], row.value(sizeIndex),
                        (int) row.classValue() == positiveClassIndex));
            }
        }

    }

}
