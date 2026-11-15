package it.uniroma2.dicii.isw2;

import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import it.uniroma2.dicii.isw2.dataset.impl.WekaCsvDatasetReader;
import it.uniroma2.dicii.isw2.dataset.impl.WekaCsvDatasetSaver;
import it.uniroma2.dicii.isw2.ml.DatasetSplitter;
import it.uniroma2.dicii.isw2.ml.DatasetTrimmer;
import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.ModelEvaluator;
import it.uniroma2.dicii.isw2.ml.ModelTester;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.impl.CrossValidatingEvaluator;
import it.uniroma2.dicii.isw2.ml.impl.CsvEvaluationReportWriter;
import it.uniroma2.dicii.isw2.ml.impl.HoldOutTester;
import it.uniroma2.dicii.isw2.ml.impl.ReleaseDatasetSplitter;
import it.uniroma2.dicii.isw2.ml.impl.ReleaseDatasetTrimmer;
import it.uniroma2.dicii.isw2.ml.impl.WekaClassifierFactory;
import it.uniroma2.dicii.isw2.ml.model.DatasetSplit;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import it.uniroma2.dicii.isw2.ml.model.ModelConfiguration;
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
 * It reads the <b>whole</b> dataset and trims it itself to the earliest {@code project.ml.trimFraction}
 * of the releases, which is what the models are studied on. The buggy label of a release is only as
 * good as the defects reported against it, and the last releases of a project have had the least time to
 * have any reported. Learning from the tail would be learning that the recent releases are clean, which
 * is a fact about the calendar and not about the code — the snoring effect. The trimmed dataset is
 * written beside the whole one, as {@code <project>-trimmed.csv}, for the studies choosing a balancing
 * technique and a feature selection to read, and for a reader to inspect.
 * <p>
 * The dataset is read as Weka needs it: the label is the nominal class, its buggy value read off how it
 * is written ({@code 1}) and never off its position, and the columns naming each row are kept only until
 * the split, which reads the release off them and drops them from both halves.
 * <p>
 * The run has three phases after the trim, in this (necessary) order:
 * <ol>
 * <li><b>split</b> — the releases are cut into the earliest {@code project.ml.trainingFraction} of them
 * and the rest. Cut at a <i>release</i>, not at a row, so that every training row is older than every
 * test row. A long-lived class still appears in both halves, which is no fault — its earlier releases
 * are the history a predictor legitimately has — but a random row split would additionally let the
 * model read a class's <i>future</i>, and a near-identical row from a release that had not happened yet
 * is information nobody could have had;</li>
 * <li><b>validation</b> — each model is cross-validated over the folds of the <i>training</i> portion
 * alone. This is what one model is chosen over another by, and confining it to the training portion is
 * what keeps choosing from costing anything: a model picked because it scored best on the test rows has
 * been fitted to them by hand;</li>
 * <li><b>inference</b> — each model is then trained on the whole training portion and asked about the test
 * releases, which nothing has read until this point. This is the figure the project is reported on.</li>
 * </ol>
 * Both phases report the same six measures, and the report tells them apart by a {@code Phase} column
 * rather than by two files, so that the two figures of a model sit next to each other. Expect the test
 * figures to be the <b>lower</b> pair: they are of releases the model has never seen, and the gap
 * between the two is itself the finding — it is how much of the validation score was the model
 * recognising classes rather than recognising faults.
 * <p>
 * Every model is measured once per configured balancing technique, both phases each time. The technique
 * is applied to the rows a model is <i>trained</i> on and to no others — the training part of each fold,
 * then the whole training portion — so the held-out tenth of a fold and the test releases are always the
 * rows as they are, and the figures of two techniques are estimates of the same population. The same
 * holds of every configured feature selection, which is crossed with every balancing technique: the
 * attributes are chosen out of the rows a model is trained on, and the rows it is measured on are only
 * reduced to what was chosen.
 * <p>
 * Nothing here names a classifier, a technique or a Weka class. Which models are measured, how their
 * training rows are balanced and which of their attributes they are trained on are the configured lists
 * the loop below runs over, and the rows arrive as {@code Instances}, so measuring three models under
 * twelve pairings of techniques instead of one is an edit to the properties file.
 */
@Slf4j
public class MlWorkflow {

    private static final String DATASET_SUFFIX = ".csv";
    private static final String TRIMMED_SUFFIX = "-trimmed.csv";
    private static final String REPORT_SUFFIX = "-evaluation.csv";

    /**
     * How one line of the logged comparison is laid out. Every figure is handed over as a string rather
     * than as the number it is, so that a cell no model could fill can be left blank instead of reading
     * {@code NaN}.
     */
    private static final String ROW_FORMAT = "%-14s %-14s %-16s %-11s %9s %9s %9s %9s %9s %9s%n";

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
            // Read with the columns naming each row still on it: both the trim and the split are cut on
            // the release a row belongs to, and the splitter drops them from each half on its way out
            Instances whole = new WekaCsvDatasetReader().readRetainingIdentity(file);
            DatasetTrimmer trimmer = new ReleaseDatasetTrimmer(MlSettings.trimFraction(),
                    settings.positiveClass(), file);
            Instances data = trimmer.trim(whole);
            Path trimmedFile = datasetDirectory.resolve(projectName + TRIMMED_SUFFIX);
            new WekaCsvDatasetSaver().save(data, trimmedFile);
            DatasetSplitter splitter = new ReleaseDatasetSplitter(settings, trimmedFile);
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
            log.error("Error reading the dataset the models are trained on, or writing its trimmed part", e);
        } catch (MlException e) {
            log.error("Error evaluating the models", e);
        }
    }

    /**
     * Validates and then tests every configured model under every configured pairing of a balancing
     * technique and a feature selection, writing each row as it comes.
     * <p>
     * A model that fails costs its own rows and no more: the ones already measured are worth reading
     * with a row missing from the table.
     *
     * @param split    the training rows and the held-out test rows
     * @param settings which models to measure, how to balance their training rows and which of their
     *                 attributes to keep
     * @param report   where the scores are written
     * @return how each of them scored under each pairing in each phase, validation first
     * @throws MlException if a score cannot be written
     */
    private static List<EvaluationOutcome> measure(DatasetSplit split, MlSettings settings,
                                                   CsvEvaluationReportWriter report) throws MlException {
        WekaClassifierFactory factory = new WekaClassifierFactory(settings);
        ModelEvaluator evaluator = new CrossValidatingEvaluator(factory, settings);
        ModelTester tester = new HoldOutTester(factory, settings);
        List<ModelConfiguration> configurations = settings.configurations();
        log.info("Measuring {} models under {} balancing techniques and {} feature selections, each "
                        + "validated over {} folds of the training releases and then tested on the held-out "
                        + "ones", settings.classifiers().size(), settings.balancingTechniques().size(),
                settings.featureSelectionTechniques().size(), settings.folds());
        List<EvaluationOutcome> outcomes = new ArrayList<>(configurations.size() * 2);
        for (ModelConfiguration configuration : configurations) {
            for (EvaluationOutcome outcome : measureOne(split, evaluator, tester, configuration)) {
                report.write(outcome);
                outcomes.add(outcome);
            }
        }
        return outcomes;
    }

    /**
     * Runs both phases of one model under one pairing of a balancing technique and a feature selection.
     *
     * @param split         the training rows and the held-out test rows
     * @param evaluator     what cross-validates it over the training rows
     * @param tester        what trains it and asks it about the test rows
     * @param configuration which model to measure, how to balance the rows it is trained on and which
     *                      of their attributes to keep
     * @return what it scored in each phase, or fewer rows if a phase failed
     */
    private static List<EvaluationOutcome> measureOne(DatasetSplit split, ModelEvaluator evaluator,
                                                      ModelTester tester, ModelConfiguration configuration) {
        List<EvaluationOutcome> outcomes = new ArrayList<>(2);
        try {
            outcomes.add(evaluator.evaluate(split.training(), configuration));
        } catch (MlException e) {
            log.error("Unable to validate {}. Leaving it out of the comparison...",
                    configuration.describe(), e);
        }
        try {
            outcomes.add(tester.test(split.training(), split.test(), configuration));
        } catch (MlException e) {
            log.error("Unable to test {} on the held-out releases. Leaving it out of the comparison...",
                    configuration.describe(), e);
        }
        return outcomes;
    }

    /**
     * Logs the comparison as a table, two rows per model and pairing of techniques: what it scored
     * validating over the training releases, and what it scored on the releases it had never seen.
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
        table.append(String.format(Locale.ROOT, ROW_FORMAT, "Classifier", "Balancing", "FeatureSelection",
                "Phase", "Accuracy", "Precision", "Recall", "F1", "AUC", "Kappa"));
        for (EvaluationOutcome outcome : outcomes) {
            table.append(String.format(Locale.ROOT, ROW_FORMAT, outcome.classifier().getLabel(),
                    outcome.balancing().getLabel(), outcome.featureSelection().getLabel(),
                    outcome.phase().getLabel(),
                    score(outcome.accuracy()), score(outcome.precision()), score(outcome.recall()),
                    score(outcome.fMeasure()), score(outcome.areaUnderRoc()), score(outcome.kappa())));
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
