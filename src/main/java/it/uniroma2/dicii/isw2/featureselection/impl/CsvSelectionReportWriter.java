package it.uniroma2.dicii.isw2.featureselection.impl;

import it.uniroma2.dicii.isw2.featureselection.exception.FeatureSelectionException;
import it.uniroma2.dicii.isw2.featureselection.model.EvaluationOutcome;
import it.uniroma2.dicii.isw2.featureselection.model.SelectedSubset;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.StringJoiner;

/**
 * Writes what the analysis found, as two comma-separated files: which metrics each search kept, and
 * what keeping them was worth to each classifier.
 * <p>
 * A row is flushed as soon as its configuration is done, for the same reason the dataset's own writer
 * flushes a release as soon as it has been measured: the wrappers turn a run into an hour or more, and
 * the configurations already measured are worth keeping when a later one fails.
 * <p>
 * The numbers go through {@link BigDecimal} rather than {@code String.format}, which would write the
 * decimal separator of the default locale — on a machine whose separator is a comma, every score would
 * quietly split its row in two.
 */
@Slf4j
public class CsvSelectionReportWriter implements AutoCloseable {

    private static final String SEPARATOR = ",";
    private static final String RECORD_SEPARATOR = "\n";
    private static final char QUOTE = '"';

    /**
     * How many decimals a score is written with. Four separates two classifiers that differ, and does
     * not pretend the fifth means anything on twenty thousand rows.
     */
    private static final int SCALE = 4;

    private static final List<String> SUBSET_COLUMNS =
            List.of("Classifier", "Search", "SelectedCount", "OutOf", "Merit", "ElapsedSeconds", "Features");

    private static final List<String> OUTCOME_COLUMNS =
            List.of("Classifier", "Selection", "MeanSelectedCount", "Accuracy", "Kappa",
                    "Precision", "Recall", "F1", "AUC", "ElapsedSeconds");

    private final Path subsetsFile;
    private final Path outcomesFile;
    private final BufferedWriter subsets;
    private final BufferedWriter outcomes;

    private CsvSelectionReportWriter(Path subsetsFile, Path outcomesFile, BufferedWriter subsets,
                                     BufferedWriter outcomes) {
        this.subsetsFile = subsetsFile;
        this.outcomesFile = outcomesFile;
        this.subsets = subsets;
        this.outcomes = outcomes;
    }

    /**
     * Creates both report files, along with the directory meant to hold them, and writes their headers.
     * Anything already at those paths is overwritten: a report is the product of one run, and the rows
     * of two runs interleaved would describe an analysis nobody performed.
     *
     * @param subsetsFile  where the metrics each search kept are written
     * @param outcomesFile where the scores of each configuration are written
     * @return a writer appending to both
     * @throws FeatureSelectionException if either file cannot be created
     */
    public static CsvSelectionReportWriter open(Path subsetsFile, Path outcomesFile)
            throws FeatureSelectionException {
        BufferedWriter subsets = create(subsetsFile, SUBSET_COLUMNS);
        BufferedWriter outcomes;
        try {
            outcomes = create(outcomesFile, OUTCOME_COLUMNS);
        } catch (FeatureSelectionException e) {
            close(subsets, subsetsFile);
            throw e;
        }
        log.info("Writing the metrics each search kept to {}, and what they scored to {}",
                subsetsFile, outcomesFile);
        return new CsvSelectionReportWriter(subsetsFile, outcomesFile, subsets, outcomes);
    }

    /**
     * Appends the metrics one search kept.
     *
     * @param subset what it kept, and what the evaluator made of it
     * @throws FeatureSelectionException if the row cannot be written
     */
    public void write(SelectedSubset subset) throws FeatureSelectionException {
        StringJoiner row = recordLine();
        row.add(escape(subset.configuration().classifier().getLabel()));
        row.add(subset.configuration().selectionLabel());
        row.add(String.valueOf(subset.size()));
        row.add(String.valueOf(subset.outOf()));
        row.add(format(subset.merit()));
        row.add(String.valueOf(subset.elapsed().toSeconds()));
        // The metrics go in one field, separated by spaces rather than by commas, so that the file
        // keeps one row per search however many of them a search happens to keep
        row.add(escape(String.join(" ", subset.features())));
        append(subsets, subsetsFile, row.toString());
    }

    /**
     * Appends what one configuration scored.
     *
     * @param outcome how it did
     * @throws FeatureSelectionException if the row cannot be written
     */
    public void write(EvaluationOutcome outcome) throws FeatureSelectionException {
        StringJoiner row = recordLine();
        row.add(escape(outcome.configuration().classifier().getLabel()));
        row.add(outcome.configuration().selectionLabel());
        row.add(format(outcome.meanSelectedCount()));
        row.add(format(outcome.accuracy()));
        row.add(format(outcome.kappa()));
        row.add(format(outcome.precision()));
        row.add(format(outcome.recall()));
        row.add(format(outcome.fMeasure()));
        row.add(format(outcome.areaUnderRoc()));
        row.add(String.valueOf(outcome.elapsed().toSeconds()));
        append(outcomes, outcomesFile, row.toString());
    }

    @Override
    public void close() throws FeatureSelectionException {
        FeatureSelectionException failure = close(subsets, subsetsFile);
        FeatureSelectionException second = close(outcomes, outcomesFile);
        // The second file is closed whether or not the first could be: leaving it open would lose rows
        // already computed over a failure that has nothing to do with them
        if (failure == null) {
            failure = second;
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Creates one of the report files and writes its header.
     * <p>
     * The header is written on its own, before the stream the rows are appended through is opened.
     * Writing it through that stream would leave it open, with nobody able to close it, whenever the
     * header is the write that fails.
     *
     * @param file    the file to create
     * @param columns the columns every row of it holds
     * @return a writer appending to it
     * @throws FeatureSelectionException if it cannot be created
     */
    private static BufferedWriter create(Path file, List<String> columns)
            throws FeatureSelectionException {
        try {
            Path directory = file.getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            StringJoiner header = recordLine();
            columns.forEach(header::add);
            Files.writeString(file, header.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            return Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new FeatureSelectionException("Unable to create the report file '" + file + "'", e);
        }
    }

    /**
     * Appends one row and makes it durable, since the run it came from may not survive to be closed.
     * <p>
     * Two configurations finish whenever they finish, so the write is synchronised: without it their
     * rows would interleave mid-field.
     *
     * @param writer where to append it
     * @param file   the file behind it, named in the report
     * @param row    the row to append
     * @throws FeatureSelectionException if it cannot be written
     */
    private static synchronized void append(BufferedWriter writer, Path file, String row)
            throws FeatureSelectionException {
        try {
            writer.write(row);
            writer.flush();
        } catch (IOException e) {
            throw new FeatureSelectionException("Unable to append a row to the report '" + file + "'", e);
        }
    }

    /**
     * @param writer the stream to close
     * @param file   the file behind it, named in the report
     * @return why it could not be closed, or {@code null} if it was
     */
    private static FeatureSelectionException close(BufferedWriter writer, Path file) {
        try {
            writer.close();
            return null;
        } catch (IOException e) {
            return new FeatureSelectionException("Unable to close the report '" + file + "'", e);
        }
    }

    /**
     * @return an empty row, i.e. a joiner separating its fields and terminating it
     */
    private static StringJoiner recordLine() {
        return new StringJoiner(SEPARATOR, "", RECORD_SEPARATOR);
    }

    /**
     * @param value a score
     * @return how it reads in the report, empty if it is not a finite number — which is what a measure
     * of the buggy class comes to when a classifier never predicted it
     */
    private static String format(double value) {
        if (!Double.isFinite(value)) {
            return "";
        }
        return BigDecimal.valueOf(value)
                .setScale(SCALE, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    /**
     * Quotes a field the way RFC 4180 prescribes whenever it holds a character that would otherwise
     * break the row. No label holds one today, but a field splitting its row in two would corrupt the
     * report without failing anything.
     *
     * @param field the value of one of the columns naming a row
     * @return it, quoted if it has to be
     */
    private static String escape(String field) {
        if (field == null) {
            return "";
        }
        if (!field.contains(SEPARATOR) && field.indexOf(QUOTE) < 0
                && field.indexOf('\n') < 0 && field.indexOf('\r') < 0) {
            return field;
        }
        return QUOTE + field.replace(String.valueOf(QUOTE), "\"\"") + QUOTE;
    }

}
