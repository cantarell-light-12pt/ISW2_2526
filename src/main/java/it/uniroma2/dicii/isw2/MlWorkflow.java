package it.uniroma2.dicii.isw2;

import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import it.uniroma2.dicii.isw2.dataset.impl.WekaCsvDatasetReader;
import it.uniroma2.dicii.isw2.ml.DatasetSplitter;
import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.ModelEvaluator;
import it.uniroma2.dicii.isw2.ml.ModelTester;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.impl.CrossValidatingEvaluator;
import it.uniroma2.dicii.isw2.ml.impl.CsvEvaluationReportWriter;
import it.uniroma2.dicii.isw2.ml.impl.HoldOutTester;
import it.uniroma2.dicii.isw2.ml.impl.ReleaseDatasetSplitter;
import it.uniroma2.dicii.isw2.ml.impl.WekaClassifierFactory;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.DatasetSplit;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import it.uniroma2.dicii.isw2.properties.PropertiesManager;
import lombok.extern.slf4j.Slf4j;
import weka.core.Instances;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Trains a model on the dataset the extraction workflow builds and reports how well it predicts which
 * classes are faulty.
 * <p>
 * It reads the <b>trimmed</b> dataset rather than the whole one. The buggy label of a release is only
 * as good as the defects reported against it, and the last releases of a project have had the least
 * time to have any reported. Learning from the tail would be learning that the recent releases are
 * clean, which is a fact about the calendar and not about the code — the snoring effect.
 * <p>
 * The run has three phases, and the order they are in is the point of the design:
 * <ol>
 * <li><b>split</b> — the releases are cut into the earliest {@code project.ml.trainingFraction} of them
 * and the rest. Cut at a <i>release</i>, not at a row, so that every training row is older than every
 * test row. A long-lived class still appears in both halves, which is no fault — its earlier releases
 * are the history a predictor legitimately has — but a random row split would additionally let the
 * model read a class's <i>future</i>, and a near-identical row from a release that had not happened yet
 * is information nobody could have had;</li>
 * <li><b>validation</b> — each model is cross-validated over the folds of the <i>training</i> half
 * alone. This is what one model is chosen over another by, and confining it to the training half is
 * what keeps choosing from costing anything: a model picked because it scored best on the test rows has
 * been fitted to them by hand;</li>
 * <li><b>inference</b> — each model is then trained on the whole training half and asked about the test
 * releases, which nothing has read until this point. This is the figure the project is reported on.</li>
 * </ol>
 * Both phases report the same six measures, and the report tells them apart by a {@code Phase} column
 * rather than by two files, so that the two figures of a model sit next to each other. Expect the test
 * figures to be the <b>lower</b> pair: they are of releases the model has never seen, and the gap
 * between the two is itself the finding — it is how much of the validation score was the model
 * recognising classes rather than recognising faults.
 * <p>
 * Nothing here names a classifier or a Weka class. Which models are measured is the configured list the
 * loop below runs over, and the rows arrive as {@code Instances}, so measuring three models instead of
 * one is an edit to the properties file, and measuring them on a dataset a feature selection has
 * reduced is a different {@code Instances} handed to the same evaluator.
 */
@Slf4j
public class MlWorkflow {

    private static final String DATASET_SUFFIX = "-trimmed.csv";
    private static final String REPORT_SUFFIX = "-evaluation.csv";

    /**
     * How one line of the logged comparison is laid out. Every figure is handed over as a string rather
     * than as the number it is, so that a cell no model could fill can be left blank instead of reading
     * {@code NaN}.
     */
    private static final String ROW_FORMAT = "%-14s %-11s %9s %9s %9s %9s %9s %9s%n";

    private final String projectName;
    private final Path datasetDirectory;

    public MlWorkflow() {
        this.projectName = PropertiesManager.getInstance().getProperty("project.name");
        this.datasetDirectory = Path.of(
                PropertiesManager.getInstance().getProperty("project.dataset.outputDirectory"));
    }

    public void execute() {
        try {
            MlSettings settings = MlSettings.load();
            Path file = datasetDirectory.resolve(projectName + DATASET_SUFFIX);
            // Read with the columns naming each row still on it: the split is cut on the release a row
            // belongs to, and the splitter drops them from each half on its way out
            Instances data = new WekaCsvDatasetReader().readRetainingIdentity(file);
            DatasetSplitter splitter = new ReleaseDatasetSplitter(settings, file);
            DatasetSplit split = splitter.split(data);
            log.info("Training on {} rows of {} releases, testing on {} rows of {}",
                    split.training().numInstances(), split.trainingReleases().size(),
                    split.test().numInstances(), split.testReleases().size());

            List<EvaluationOutcome> outcomes;
            try (CsvEvaluationReportWriter report = CsvEvaluationReportWriter.open(
                    datasetDirectory.resolve(projectName + REPORT_SUFFIX))) {
                outcomes = measure(split, settings, report);
            }
            summarise(outcomes, settings.folds());
            log.info("Models evaluated successfully!");
        } catch (DatasetException e) {
            log.error("Error reading the dataset the models are trained on", e);
        } catch (MlException e) {
            log.error("Error evaluating the models", e);
        }
    }

    /**
     * Validates and then tests every configured model, writing each row as it comes.
     * <p>
     * A model that fails costs its own rows and no more: the ones already measured are worth reading
     * with a row missing from the table.
     *
     * @param split    the training rows and the held-out test rows
     * @param settings which models to measure
     * @param report   where the scores are written
     * @return how each of them scored in each phase, validation first
     * @throws MlException if a score cannot be written
     */
    private static List<EvaluationOutcome> measure(DatasetSplit split, MlSettings settings,
                                                   CsvEvaluationReportWriter report) throws MlException {
        WekaClassifierFactory factory = new WekaClassifierFactory(settings);
        ModelEvaluator evaluator = new CrossValidatingEvaluator(factory, settings);
        ModelTester tester = new HoldOutTester(factory, settings);
        List<ClassifierKind> classifiers = settings.classifiers();
        log.info("Measuring {} models, each validated over {} folds of the training releases and then "
                + "tested on the held-out ones", classifiers.size(), settings.folds());
        List<EvaluationOutcome> outcomes = new ArrayList<>(classifiers.size() * 2);
        for (ClassifierKind classifier : classifiers) {
            for (EvaluationOutcome outcome : measureOne(split, evaluator, tester, classifier)) {
                report.write(outcome);
                outcomes.add(outcome);
            }
        }
        return outcomes;
    }

    /**
     * Runs both phases of one model.
     *
     * @param split      the training rows and the held-out test rows
     * @param evaluator  what cross-validates it over the training rows
     * @param tester     what trains it and asks it about the test rows
     * @param classifier which model to measure
     * @return what it scored in each phase, or fewer rows if a phase failed
     */
    private static List<EvaluationOutcome> measureOne(DatasetSplit split, ModelEvaluator evaluator,
                                                      ModelTester tester, ClassifierKind classifier) {
        List<EvaluationOutcome> outcomes = new ArrayList<>(2);
        try {
            outcomes.add(evaluator.evaluate(split.training(), classifier));
        } catch (MlException e) {
            log.error("Unable to validate {}. Leaving it out of the comparison...",
                    classifier.getLabel(), e);
        }
        try {
            outcomes.add(tester.test(split.training(), split.test(), classifier));
        } catch (MlException e) {
            log.error("Unable to test {} on the held-out releases. Leaving it out of the comparison...",
                    classifier.getLabel(), e);
        }
        return outcomes;
    }

    /**
     * Logs the comparison as a table, two rows per model: what it scored validating over the training
     * releases, and what it scored on the releases it had never seen.
     * <p>
     * The four figures beside accuracy and kappa are of the buggy class alone, and are worth a glance
     * before accuracy is believed: some 23% of the rows are buggy, so a model finding none of them still
     * scores .77, and two models can differ by nothing in accuracy while differing entirely in whether
     * they found anything. Kappa is what says the same thing of the whole matrix — it is what is left of
     * the accuracy once the share of it that guessing at the observed rates would have reached anyway is
     * taken out.
     *
     * @param outcomes how each model scored in each phase
     * @param folds    how many folds the validation cut, named in the heading
     */
    private static void summarise(List<EvaluationOutcome> outcomes, int folds) {
        StringBuilder table = new StringBuilder(String.format(Locale.ROOT,
                "How each model predicts the buggy classes, validated over %d folds of the training "
                        + "releases and tested on the held-out ones:%n", folds));
        table.append(String.format(Locale.ROOT, ROW_FORMAT, "Classifier", "Phase", "Accuracy",
                "Precision", "Recall", "F1", "AUC", "Kappa"));
        for (EvaluationOutcome outcome : outcomes) {
            table.append(String.format(Locale.ROOT, ROW_FORMAT, outcome.classifier().getLabel(),
                    outcome.phase().getLabel(), score(outcome.accuracy()), score(outcome.precision()),
                    score(outcome.recall()), score(outcome.fMeasure()), score(outcome.areaUnderRoc()),
                    score(outcome.kappa())));
        }
        log.info(table.toString());
    }

    /**
     * @param value a score
     * @return how it reads in the table, blank if it is not a finite number — which is what a measure of
     * the buggy class comes to when a model never predicted it
     */
    private static String score(double value) {
        if (!Double.isFinite(value)) {
            return "";
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

}
