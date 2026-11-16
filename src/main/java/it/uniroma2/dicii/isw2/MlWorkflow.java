package it.uniroma2.dicii.isw2;

import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import it.uniroma2.dicii.isw2.dataset.impl.WekaCsvDatasetReader;
import it.uniroma2.dicii.isw2.dataset.impl.WekaCsvDatasetSaver;
import it.uniroma2.dicii.isw2.ml.DatasetTrimmer;
import it.uniroma2.dicii.isw2.ml.FeatureSelector;
import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.ModelEvaluator;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.impl.BestOutcome;
import it.uniroma2.dicii.isw2.ml.impl.CrossValidatingEvaluator;
import it.uniroma2.dicii.isw2.ml.impl.CsvEvaluationReportWriter;
import it.uniroma2.dicii.isw2.ml.impl.ReleaseDatasetTrimmer;
import it.uniroma2.dicii.isw2.ml.impl.WekaClassifierFactory;
import it.uniroma2.dicii.isw2.ml.impl.WekaFeatureSelector;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import it.uniroma2.dicii.isw2.ml.model.FeatureSelectionKind;
import it.uniroma2.dicii.isw2.ml.model.Measure;
import it.uniroma2.dicii.isw2.ml.model.ModelConfiguration;
import it.uniroma2.dicii.isw2.properties.PropertiesManager;
import lombok.extern.slf4j.Slf4j;
import weka.core.Instances;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Chooses the classifier: cross-validates every configured model under every configured pairing of a
 * balancing technique and a feature selection, and reports, for each model, the pairing it scored best
 * under by each measure.
 * <p>
 * It reads the <b>whole</b> dataset and trims it itself to the earliest {@code project.ml.trimFraction}
 * of the releases, which is what the models are studied on. The buggy label of a release is only as
 * good as the defects reported against it, and the last releases of a project have had the least time to
 * have any reported. Learning from the tail would be learning that the recent releases are clean, which
 * is a fact about the calendar and not about the code — the snoring effect. The trimmed dataset is
 * written beside the whole one, as {@code <project>-trimmed.csv}, for a reader to inspect.
 * <p>
 * The dataset is read as Weka needs it: the label is the nominal class, its buggy value read off how it
 * is written ({@code 1}) and never off its position, and the columns naming each row are kept only until
 * the trim, which reads the release off them, and dropped before any model sees a row.
 * <p>
 * Every model is then cross-validated over {@code project.ml.folds} folds of the trimmed rows, once per
 * pairing of a configured balancing technique with a configured feature selection, and nothing else
 * decides between them. Both techniques are part of the model rather than something done to the rows
 * before the folds are cut: each fold chooses its attributes out of the nine tenths it trains on,
 * balances those nine tenths reduced to them, and measures the tenth it held out as it is. Balancing
 * the whole dataset before cutting the folds would have SMOTE synthesise rows out of classes a fold then
 * tests on, and choosing the attributes over the whole would have every fold choose with the tenth it is
 * measured on in hand.
 * <p>
 * The report holds one row per model and pairing, and the log ends with the verdict: per model and per
 * {@link Measure}, the pairing that scored best and the figure it won with. The verdict is per measure on
 * purpose — they disagree, and which of them the choice is made by is the reader's call.
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
    private static final String ROW_FORMAT = "%-14s %-14s %-16s %9s %9s %9s %9s %9s %9s%n";

    /**
     * How one line of the logged verdict is laid out: a model and a measure, then the pairing of
     * techniques that measure picks and the figure it won with.
     */
    private static final String VERDICT_FORMAT = "%-14s %-10s %-14s %-16s %9s%n";

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
            // Read with the columns naming each row still on it: the trim is cut on the release a row
            // belongs to, and they are dropped as soon as it is done
            Instances whole = new WekaCsvDatasetReader().readRetainingIdentity(file);
            DatasetTrimmer trimmer = new ReleaseDatasetTrimmer(MlSettings.trimFraction(),
                    settings.positiveClass(), file);
            Instances trimmed = trimmer.trim(whole);
            Path trimmedFile = datasetDirectory.resolve(projectName + TRIMMED_SUFFIX);
            new WekaCsvDatasetSaver().save(trimmed, trimmedFile);
            Instances data = WekaCsvDatasetReader.withoutIdentityColumns(trimmedFile, trimmed);
            log.info("Cross-validating the models over the {} rows of the trimmed dataset",
                    data.numInstances());
            describeSelections(data, settings);

            List<EvaluationOutcome> outcomes;
            try (CsvEvaluationReportWriter report = CsvEvaluationReportWriter.open(
                    datasetDirectory.resolve(projectName + REPORT_SUFFIX))) {
                outcomes = measure(data, settings, report);
            }
            summarise(outcomes, settings);
            log.info("Models evaluated successfully!");
        } catch (DatasetException e) {
            log.error("Error reading the dataset the models are trained on, or writing its trimmed part", e);
        } catch (MlException e) {
            log.error("Error evaluating the models", e);
        }
    }

    /**
     * Logs which attributes each configured feature selection keeps out of the whole of the trimmed
     * rows. They are what a reader can see of a selection, and only that: each fold chooses again out of
     * its own nine tenths, and may keep a slightly different subset. A technique that cannot be run over
     * them is only reported, since the comparison may still measure the others.
     *
     * @param data     the trimmed rows
     * @param settings which techniques to run
     */
    private static void describeSelections(Instances data, MlSettings settings) {
        FeatureSelector selector = new WekaFeatureSelector(settings);
        int attributes = data.numAttributes() - 1;
        for (FeatureSelectionKind kind : settings.featureSelectionTechniques()) {
            try {
                List<String> kept = selector.select(data, kind);
                log.info("{} keeps {} of the {} attributes of the trimmed dataset: {}", kind.getLabel(),
                        kept.size(), attributes, String.join(", ", kept));
            } catch (MlException e) {
                log.warn("Unable to tell which attributes {} keeps of the trimmed dataset", kind.getLabel(),
                        e);
            }
        }
    }

    /**
     * Cross-validates every configured model under every configured pairing of a balancing technique
     * and a feature selection, writing each row as it comes.
     * <p>
     * A pairing that fails costs its own row and no more: the ones already measured are worth reading
     * with a row missing from the table.
     *
     * @param data     the trimmed rows, which the folds are cut from
     * @param settings which models to measure, how to balance their training rows, which of their
     *                 attributes to keep and how many folds to cut
     * @param report   where the scores are written
     * @return how each of them scored under each pairing
     * @throws MlException if a score cannot be written
     */
    private static List<EvaluationOutcome> measure(Instances data, MlSettings settings,
                                                   CsvEvaluationReportWriter report) throws MlException {
        ModelEvaluator evaluator = new CrossValidatingEvaluator(new WekaClassifierFactory(settings), settings);
        List<ModelConfiguration> configurations = settings.configurations();
        log.info("Validating {} models under {} balancing techniques and {} feature selections over {} "
                        + "folds", settings.classifiers().size(), settings.balancingTechniques().size(),
                settings.featureSelectionTechniques().size(), settings.folds());
        List<EvaluationOutcome> outcomes = new ArrayList<>(configurations.size());
        for (ModelConfiguration configuration : configurations) {
            try {
                EvaluationOutcome outcome = evaluator.evaluate(data, configuration);
                report.write(outcome);
                outcomes.add(outcome);
            } catch (MlException e) {
                log.error("Unable to validate {}. Leaving it out of the comparison...",
                        configuration.describe(), e);
            }
        }
        return outcomes;
    }

    /**
     * Logs the comparison as a table, one row per model and pairing of techniques, and then the verdict:
     * for each model and measure, the pairing that measure picks, with the figure that made it win.
     * <p>
     * The four figures beside accuracy and kappa are of the buggy class alone, and are worth a glance
     * before accuracy is believed: some 23% of the rows are buggy, so a model finding none of them still
     * scores .77, and two models can differ by nothing in accuracy while differing entirely in whether
     * they found anything. Kappa is what says the same thing of the whole matrix — it is what is left of
     * the accuracy once the share of it that guessing at the observed rates would have reached anyway is
     * taken out.
     *
     * @param outcomes how each model scored under each pairing
     * @param settings the models, in the order they were measured, and the folds cut
     */
    private static void summarise(List<EvaluationOutcome> outcomes, MlSettings settings) {
        StringBuilder table = new StringBuilder(String.format(Locale.ROOT,
                "How each model predicts the buggy classes, validated over %d folds of the trimmed "
                        + "dataset:%n", settings.folds()));
        table.append(String.format(Locale.ROOT, ROW_FORMAT, "Classifier", "Balancing", "FeatureSelection",
                "Accuracy", "Precision", "Recall", "F1", "AUC", "Kappa"));
        for (EvaluationOutcome outcome : outcomes) {
            table.append(String.format(Locale.ROOT, ROW_FORMAT, outcome.classifier().getLabel(),
                    outcome.balancing().getLabel(), outcome.featureSelection().getLabel(),
                    score(outcome.accuracy()), score(outcome.precision()), score(outcome.recall()),
                    score(outcome.fMeasure()), score(outcome.areaUnderRoc()), score(outcome.kappa())));
        }
        table.append(String.format(Locale.ROOT, "%nThe balancing technique and feature selection each "
                + "measure picks for each model:%n"));
        table.append(String.format(Locale.ROOT, VERDICT_FORMAT, "Classifier", "Measure", "Balancing",
                "FeatureSelection", "Score"));
        for (ClassifierKind classifier : settings.classifiers()) {
            for (Measure measure : Measure.values()) {
                table.append(String.format(Locale.ROOT, VERDICT_FORMAT, verdict(outcomes, classifier, measure)));
            }
        }
        log.info(table.toString());
    }

    /**
     * @param outcomes   how each model scored under each pairing
     * @param classifier the model to read the verdict of
     * @param measure    what the verdict is by
     * @return the cells of its line of the verdict: the model and the measure, then the balancing
     * technique and the feature selection it picks and the figure they won with — blank if the model
     * scored no number under any pairing
     */
    private static Object[] verdict(List<EvaluationOutcome> outcomes, ClassifierKind classifier,
                                    Measure measure) {
        Optional<EvaluationOutcome> best =
                BestOutcome.of(outcomes, outcome -> outcome.classifier() == classifier, measure);
        return new Object[] {
                classifier.getLabel(),
                measure.getLabel(),
                best.map(outcome -> outcome.balancing().getLabel()).orElse(""),
                best.map(outcome -> outcome.featureSelection().getLabel()).orElse(""),
                best.map(outcome -> score(measure.of(outcome))).orElse("")
        };
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
