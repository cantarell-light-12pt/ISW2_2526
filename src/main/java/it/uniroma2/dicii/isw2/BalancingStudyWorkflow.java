package it.uniroma2.dicii.isw2;

import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import it.uniroma2.dicii.isw2.dataset.impl.WekaCsvDatasetReader;
import it.uniroma2.dicii.isw2.ml.DatasetSplitter;
import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.ModelTester;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.impl.BestBalancing;
import it.uniroma2.dicii.isw2.ml.impl.CsvEvaluationReportWriter;
import it.uniroma2.dicii.isw2.ml.impl.HoldOutTester;
import it.uniroma2.dicii.isw2.ml.impl.ReleaseDatasetSplitter;
import it.uniroma2.dicii.isw2.ml.impl.WekaClassifierFactory;
import it.uniroma2.dicii.isw2.ml.model.BalancingKind;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.DatasetSplit;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import it.uniroma2.dicii.isw2.ml.model.EvaluationPhase;
import it.uniroma2.dicii.isw2.ml.model.Measure;
import it.uniroma2.dicii.isw2.properties.PropertiesManager;
import lombok.extern.slf4j.Slf4j;
import weka.core.Instances;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Trains every configured model under every configured balancing technique, measures each of them on
 * releases it was not trained on, and reports which technique each model scored best under.
 * <p>
 * The releases are those {@link MlWorkflow} <b>trains</b> on, and only those. They are cut in two again,
 * in time order like the first cut: the earliest {@code project.ml.balancingStudy.trainingFraction} of
 * them are trained on and the rest are what the techniques are compared over. The test releases are
 * never read here, and that is the point of the design rather than a restriction on it — a technique
 * chosen because it scored best on the test releases has been fitted to them by hand, and the test
 * figure measured under it would stop estimating anything. Chosen here, it costs them nothing.
 * <p>
 * The comparison is held out rather than cross-validated, for the reason the final test is: the folds
 * of a cross-validation are cut at random, so a class of one release is trained on while the same class
 * of the next is tested on, and a technique that helps a model recognise a class it has seen is not the
 * same as one that helps it predict a release it has not. The two halves are as lopsided as the whole —
 * the balancing is of the training half alone, inside the model, and the half it is compared over is
 * the rows as they are.
 * <p>
 * Every measure is reported, and the best technique is named per measure rather than collapsed into a
 * single verdict. The measures disagree for reasons that matter — see {@link Measure} — and which of them
 * the choice is made by is a decision the report leaves to its reader.
 */
@Slf4j
public class BalancingStudyWorkflow {

    private static final String DATASET_SUFFIX = "-trimmed.csv";
    private static final String REPORT_SUFFIX = "-balancing-study.csv";

    /**
     * How one line of the logged comparison is laid out, every figure handed over as a string so that a
     * cell no model could fill can be left blank.
     */
    private static final String ROW_FORMAT = "%-14s %-14s %9s %9s %9s %9s %9s %9s%n";

    /**
     * How one line of the logged verdict is laid out: a model, then the technique each measure picks.
     */
    private static final String VERDICT_FORMAT = "%-14s %-22s %-22s %-22s %-22s%n";

    private final String projectName;
    private final Path datasetDirectory;

    public BalancingStudyWorkflow() {
        this.projectName = PropertiesManager.getInstance().getProperty("project.name");
        this.datasetDirectory = Path.of(
                PropertiesManager.getInstance().getProperty("project.dataset.outputDirectory"));
    }

    public void execute() {
        try {
            MlSettings settings = MlSettings.load();
            Path file = datasetDirectory.resolve(projectName + DATASET_SUFFIX);
            Instances data = new WekaCsvDatasetReader().readRetainingIdentity(file);
            DatasetSplit study = studySplit(data, settings, file);

            List<EvaluationOutcome> outcomes;
            try (CsvEvaluationReportWriter report = CsvEvaluationReportWriter.open(
                    datasetDirectory.resolve(projectName + REPORT_SUFFIX))) {
                outcomes = compare(study, settings, report);
            }
            summarise(outcomes, settings.classifiers(), study);
            log.info("Balancing techniques compared successfully!");
        } catch (DatasetException e) {
            log.error("Error reading the dataset the balancing techniques are compared on", e);
        } catch (MlException e) {
            log.error("Error comparing the balancing techniques", e);
        }
    }

    /**
     * Cuts the releases the way {@link MlWorkflow} does, sets the test releases aside unread, and cuts
     * the training releases in two again.
     *
     * @param data     the whole dataset, with the columns naming each row still on it
     * @param settings where the first cut falls
     * @param file     the dataset the rows came from, named in the report of a failure
     * @return the earlier training releases to train on, and the later ones to compare the techniques
     * over
     * @throws MlException if either cut cannot be made
     */
    private static DatasetSplit studySplit(Instances data, MlSettings settings, Path file)
            throws MlException {
        DatasetSplitter outer = new ReleaseDatasetSplitter(settings, file);
        DatasetSplit whole = outer.split(data);
        log.info("Leaving the {} test releases, {} to {}, unread: the techniques are compared over the {} "
                        + "training releases alone", whole.testReleases().size(),
                whole.testReleases().getFirst(), whole.testReleases().getLast(),
                whole.trainingReleases().size());
        Instances history = outer.rowsOfReleases(data, whole.trainingReleases());
        MlSettings studySettings = settings.withTrainingFraction(MlSettings.balancingStudyTrainingFraction());
        DatasetSplit study = new ReleaseDatasetSplitter(studySettings, file).split(history);
        log.info("Training on {} rows of {} releases, comparing over {} rows of {}",
                study.training().numInstances(), study.trainingReleases().size(),
                study.test().numInstances(), study.testReleases().size());
        return study;
    }

    /**
     * Measures every configured model under every configured technique, writing each row as it comes.
     * A pairing that fails costs its own row and no more.
     *
     * @param study    the rows to train on and the rows to compare over
     * @param settings which models and techniques to measure
     * @param report   where the scores are written
     * @return how each model scored under each technique
     * @throws MlException if a score cannot be written
     */
    private static List<EvaluationOutcome> compare(DatasetSplit study, MlSettings settings,
                                                   CsvEvaluationReportWriter report) throws MlException {
        ModelTester tester = new HoldOutTester(new WekaClassifierFactory(settings), settings,
                EvaluationPhase.SELECTION);
        List<ClassifierKind> classifiers = settings.classifiers();
        List<BalancingKind> techniques = settings.balancingTechniques();
        List<EvaluationOutcome> outcomes = new ArrayList<>(classifiers.size() * techniques.size());
        for (ClassifierKind classifier : classifiers) {
            for (BalancingKind balancing : techniques) {
                try {
                    EvaluationOutcome outcome =
                            tester.test(study.training(), study.test(), classifier, balancing);
                    report.write(outcome);
                    outcomes.add(outcome);
                } catch (MlException e) {
                    log.error("Unable to measure {} balanced by {}. Leaving it out of the comparison...",
                            classifier.getLabel(), balancing.getLabel(), e);
                }
            }
        }
        return outcomes;
    }

    /**
     * Logs the comparison as a table, one row per model and technique, and then the verdict: for each
     * model, the technique each measure picks, with the figure that made it win.
     *
     * @param outcomes    how each model scored under each technique
     * @param classifiers the models, in the order they were measured
     * @param study       which releases were trained on and compared over, named in the heading
     */
    private static void summarise(List<EvaluationOutcome> outcomes, List<ClassifierKind> classifiers,
                                  DatasetSplit study) {
        StringBuilder table = new StringBuilder(String.format(Locale.ROOT,
                "How each model predicts the buggy classes of releases %s to %s, trained on %s to %s:%n",
                study.testReleases().getFirst(), study.testReleases().getLast(),
                study.trainingReleases().getFirst(), study.trainingReleases().getLast()));
        table.append(String.format(Locale.ROOT, ROW_FORMAT, "Classifier", "Balancing", "Accuracy",
                "Precision", "Recall", "F1", "AUC", "Kappa"));
        for (EvaluationOutcome outcome : outcomes) {
            table.append(String.format(Locale.ROOT, ROW_FORMAT, outcome.classifier().getLabel(),
                    outcome.balancing().getLabel(), score(outcome.accuracy()), score(outcome.precision()),
                    score(outcome.recall()), score(outcome.fMeasure()), score(outcome.areaUnderRoc()),
                    score(outcome.kappa())));
        }
        table.append(String.format(Locale.ROOT, "%nThe technique each measure picks for each model:%n"));
        Object[] heading = new Object[Measure.values().length + 1];
        heading[0] = "Classifier";
        for (Measure measure : Measure.values()) {
            heading[measure.ordinal() + 1] = measure.getLabel();
        }
        table.append(String.format(Locale.ROOT, VERDICT_FORMAT, heading));
        for (ClassifierKind classifier : classifiers) {
            table.append(String.format(Locale.ROOT, VERDICT_FORMAT, verdict(outcomes, classifier)));
        }
        log.info(table.toString());
    }

    /**
     * @param outcomes   how each model scored under each technique
     * @param classifier the model to read the verdict of
     * @return the cells of its line of the verdict: the model, then per measure the technique it picks
     * and the figure it won with — blank if the model scored no number under any technique
     */
    private static Object[] verdict(List<EvaluationOutcome> outcomes, ClassifierKind classifier) {
        Object[] cells = new Object[Measure.values().length + 1];
        cells[0] = classifier.getLabel();
        for (Measure measure : Measure.values()) {
            Optional<EvaluationOutcome> best = BestBalancing.of(outcomes, classifier, measure);
            cells[measure.ordinal() + 1] = best
                    .map(outcome -> outcome.balancing().getLabel() + " (" + score(measure.of(outcome)) + ")")
                    .orElse("");
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

}
