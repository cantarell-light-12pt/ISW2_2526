package it.uniroma2.dicii.isw2;

import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import it.uniroma2.dicii.isw2.dataset.impl.WekaCsvDatasetReader;
import it.uniroma2.dicii.isw2.ml.FeatureSelector;
import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.ModelEvaluator;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.impl.*;
import it.uniroma2.dicii.isw2.ml.model.*;
import it.uniroma2.dicii.isw2.properties.PropertiesManager;
import lombok.extern.slf4j.Slf4j;
import weka.core.Instances;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Cross-validates every configured model under every configured feature selection, over the releases a
 * model is trained on, and reports which selection each model scored best under.
 * <p>
 * The releases are those {@link MlWorkflow} <b>trains</b> on, and only those: the test releases are set
 * aside unread, for the reason {@link BalancingStudyWorkflow} sets them aside — a selection chosen
 * because it scored best on them has been fitted to them by hand. Unlike that study, this one does not
 * cut the training releases again, but cross-validates over {@code project.ml.folds} folds of them, as
 * the validation phase of {@link MlWorkflow} does. The selection is part of the model, so every fold
 * chooses its attributes out of the nine tenths it trains on and the tenth it is measured on is no part
 * of the choice.
 * <p>
 * The selection is not chosen on its own, but for every configured balancing technique: the attributes
 * worth keeping are those the model is told apart by once its rows have been balanced, and SMOTE in
 * particular synthesises its rows over whatever attributes are left. So the verdict names a selection
 * per model <i>and</i> technique, and narrowing {@code project.ml.balancing} to the technique the
 * balancing study chose for each model narrows the comparison to the pairings that matter.
 * <p>
 * Before the models are measured, the attributes each technique keeps over the whole of the training
 * releases are logged. They are what a reader can see of a selection, and only that: each fold chooses
 * again out of its own rows, and may keep a slightly different subset.
 */
@Slf4j
public class FeatureSelectionStudyWorkflow {

    /**
     * The trimmed dataset {@link MlWorkflow} writes, which has to have run first.
     */
    private static final String DATASET_SUFFIX = "-trimmed.csv";
    private static final String REPORT_SUFFIX = "-feature-selection-study.csv";

    /**
     * How one line of the logged comparison is laid out, every figure handed over as a string so that a
     * cell no model could fill can be left blank.
     */
    private static final String ROW_FORMAT = "%-14s %-14s %-16s %9s %9s %9s %9s %9s %9s%n";

    /**
     * How one line of the logged verdict is laid out: a model and a balancing technique, then the
     * selection each measure picks.
     */
    private static final String VERDICT_FORMAT = "%-14s %-14s %-24s %-24s %-24s %-24s%n";

    /**
     * How many cells of a line of the verdict come before the measures: the model and the technique.
     */
    private static final int VERDICT_KEYS = 2;

    private final String projectName;
    private final Path datasetDirectory;

    public FeatureSelectionStudyWorkflow() {
        this.projectName = PropertiesManager.getInstance().getProperty("project.name");
        this.datasetDirectory = Path.of(PropertiesManager.getInstance().getProperty("project.dataset.outputDirectory"));
    }

    /**
     * Logs which attributes each configured technique keeps out of the whole of the training releases.
     * A technique that cannot be run over them is only reported, since the comparison below may still
     * measure the others.
     *
     * @param training the rows of the training releases
     * @param settings which techniques to run
     */
    private static void describeSelections(Instances training, MlSettings settings) {
        FeatureSelector selector = new WekaFeatureSelector(settings);
        int attributes = training.numAttributes() - 1;
        for (FeatureSelectionKind kind : settings.featureSelectionTechniques()) {
            try {
                List<String> kept = selector.select(training, kind);
                log.info("{} keeps {} of the {} attributes of the training releases: {}", kind.getLabel(), kept.size(), attributes, String.join(", ", kept));
            } catch (MlException e) {
                log.warn("Unable to tell which attributes {} keeps of the training releases", kind.getLabel(), e);
            }
        }
    }

    /**
     * Cross-validates every configured model under every pairing of a configured balancing technique
     * and a configured selection, writing each row as it comes. A pairing that fails costs its own row
     * and no more.
     *
     * @param training the rows of the training releases, which the folds are cut from
     * @param settings which models and techniques to measure, and how many folds to cut
     * @param report   where the scores are written
     * @return how each model scored under each pairing
     * @throws MlException if a score cannot be written
     */
    private static List<EvaluationOutcome> compare(Instances training, MlSettings settings, CsvEvaluationReportWriter report) throws MlException {
        ModelEvaluator evaluator = new CrossValidatingEvaluator(new WekaClassifierFactory(settings), settings);
        List<ModelConfiguration> configurations = settings.configurations();
        log.info("Validating {} models under {} balancing techniques and {} feature selections over {} " + "folds", settings.classifiers().size(), settings.balancingTechniques().size(), settings.featureSelectionTechniques().size(), settings.folds());
        List<EvaluationOutcome> outcomes = new ArrayList<>(configurations.size());
        for (ModelConfiguration configuration : configurations) {
            try {
                EvaluationOutcome outcome = evaluator.evaluate(training, configuration);
                report.write(outcome);
                outcomes.add(outcome);
            } catch (MlException e) {
                log.error("Unable to validate {}. Leaving it out of the comparison...", configuration.describe(), e);
            }
        }
        return outcomes;
    }

    /**
     * Logs the comparison as a table, one row per model, balancing technique and selection, and then the
     * verdict: for each model and technique, the selection each measure picks, with the figure that made
     * it win.
     *
     * @param outcomes how each model scored under each pairing
     * @param settings the models and techniques, in the order they were measured, and the folds cut
     */
    private static void summarise(List<EvaluationOutcome> outcomes, MlSettings settings) {
        StringBuilder table = new StringBuilder(String.format(Locale.ROOT, "How each model predicts the buggy classes, validated over %d folds of the training " + "releases:%n", settings.folds()));
        table.append(String.format(Locale.ROOT, ROW_FORMAT, "Classifier", "Balancing", "FeatureSelection", "Accuracy", "Precision", "Recall", "F1", "AUC", "Kappa"));
        for (EvaluationOutcome outcome : outcomes) {
            table.append(String.format(Locale.ROOT, ROW_FORMAT, outcome.classifier().getLabel(), outcome.balancing().getLabel(), outcome.featureSelection().getLabel(), score(outcome.accuracy()), score(outcome.precision()), score(outcome.recall()), score(outcome.fMeasure()), score(outcome.areaUnderRoc()), score(outcome.kappa())));
        }
        table.append(String.format(Locale.ROOT, "%nThe feature selection each measure picks for each model " + "and balancing technique:%n"));
        Object[] heading = new Object[Measure.values().length + VERDICT_KEYS];
        heading[0] = "Classifier";
        heading[1] = "Balancing";
        for (Measure measure : Measure.values()) {
            heading[measure.ordinal() + VERDICT_KEYS] = measure.getLabel();
        }
        table.append(String.format(Locale.ROOT, VERDICT_FORMAT, heading));
        for (ClassifierKind classifier : settings.classifiers()) {
            for (BalancingKind balancing : settings.balancingTechniques()) {
                table.append(String.format(Locale.ROOT, VERDICT_FORMAT, verdict(outcomes, classifier, balancing)));
            }
        }
        log.info(table.toString());
    }

    /**
     * @param outcomes   how each model scored under each pairing
     * @param classifier the model to read the verdict of
     * @param balancing  the technique its rows were balanced by
     * @return the cells of its line of the verdict: the model and the technique, then per measure the
     * selection it picks and the figure it won with — blank if the model scored no number under any of
     * them
     */
    private static Object[] verdict(List<EvaluationOutcome> outcomes, ClassifierKind classifier, BalancingKind balancing) {
        Object[] cells = new Object[Measure.values().length + VERDICT_KEYS];
        cells[0] = classifier.getLabel();
        cells[1] = balancing.getLabel();
        for (Measure measure : Measure.values()) {
            Optional<EvaluationOutcome> best = BestOutcome.of(outcomes, outcome -> outcome.classifier() == classifier && outcome.balancing() == balancing, measure);
            cells[measure.ordinal() + VERDICT_KEYS] = best.map(outcome -> outcome.featureSelection().getLabel() + " (" + score(measure.of(outcome)) + ")").orElse("");
        }
        return cells;
    }

    /**
     * @param value a score
     * @return how it reads in the table, blank if it is not a finite number
     */
    private static String score(double value) {
        if (!Double.isFinite(value)) {
            return "";
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    public void execute() {
        try {
            MlSettings settings = MlSettings.load();
            Path file = datasetDirectory.resolve(projectName + DATASET_SUFFIX);
            Instances data = new WekaCsvDatasetReader().readRetainingIdentity(file);
            DatasetSplit split = new ReleaseDatasetSplitter(settings, file).split(data);
            log.info("Leaving the {} test releases, {} to {}, unread: the feature selections are compared " + "over {} rows of the {} training releases, {} to {}", split.testReleases().size(), split.testReleases().getFirst(), split.testReleases().getLast(), split.training().numInstances(), split.trainingReleases().size(), split.trainingReleases().getFirst(), split.trainingReleases().getLast());
            describeSelections(split.training(), settings);

            List<EvaluationOutcome> outcomes;
            try (CsvEvaluationReportWriter report = CsvEvaluationReportWriter.open(datasetDirectory.resolve(projectName + REPORT_SUFFIX))) {
                outcomes = compare(split.training(), settings, report);
            }
            summarise(outcomes, settings);
            log.info("Feature selections compared successfully!");
        } catch (DatasetException e) {
            log.error("Error reading the dataset the feature selections are compared on, which MlMain " + "writes and has to have run first", e);
        } catch (MlException e) {
            log.error("Error comparing the feature selections", e);
        }
    }

}
