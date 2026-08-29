package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
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
 * Writes what each model scored, as a comma-separated file holding one row per model.
 * <p>
 * A row is flushed as soon as its model is done, for the same reason the dataset's own writer flushes a
 * release as soon as it has been measured: a model already measured is worth keeping when a later one
 * fails.
 * <p>
 * The numbers go through {@link BigDecimal} rather than {@code String.format}, which would write the
 * decimal separator of the default locale — on a machine whose separator is a comma, every score would
 * quietly split its row in two.
 */
@Slf4j
public class CsvEvaluationReportWriter implements AutoCloseable {

    private static final String SEPARATOR = ",";
    private static final String RECORD_SEPARATOR = "\n";
    private static final char QUOTE = '"';

    /**
     * How many decimals a score is written with. Four separates two models that differ, and does not
     * pretend the fifth means anything on twenty thousand rows.
     */
    private static final int SCALE = 4;

    private static final List<String> COLUMNS = List.of("Classifier", "Phase", "Folds", "Accuracy",
            "Precision", "Recall", "F1", "AUC", "Kappa", "ElapsedSeconds");

    private final Path file;
    private final BufferedWriter writer;

    private CsvEvaluationReportWriter(Path file, BufferedWriter writer) {
        this.file = file;
        this.writer = writer;
    }

    /**
     * Creates the report file, along with the directory meant to hold it, and writes its header.
     * Anything already at that path is overwritten: a report is the product of one run, and the rows of
     * two runs interleaved would describe an evaluation nobody performed.
     * <p>
     * The header is written on its own, before the stream the rows are appended through is opened.
     * Writing it through that stream would leave it open, with nobody able to close it, whenever the
     * header is the write that fails.
     *
     * @param file where to write the report
     * @return a writer appending to it
     * @throws MlException if it cannot be created
     */
    public static CsvEvaluationReportWriter open(Path file) throws MlException {
        try {
            Path directory = file.getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            StringJoiner header = recordLine();
            COLUMNS.forEach(header::add);
            Files.writeString(file, header.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            log.info("Writing what each model scored to {}", file);
            return new CsvEvaluationReportWriter(file,
                    Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.APPEND));
        } catch (IOException e) {
            throw new MlException("Unable to create the report file '" + file + "'", e);
        }
    }

    /**
     * Appends what one model scored, and makes it durable, since the run it came from may not survive to
     * be closed.
     *
     * @param outcome how it did
     * @throws MlException if the row cannot be written
     */
    public void write(EvaluationOutcome outcome) throws MlException {
        StringJoiner row = recordLine();
        row.add(escape(outcome.classifier().getLabel()));
        row.add(escape(outcome.phase().getLabel()));
        // Blank rather than zero for the inference phase, which cuts no folds: a zero there would read
        // as a validation over no folds, which is a different thing from not having been one
        row.add(outcome.folds() > 0 ? String.valueOf(outcome.folds()) : "");
        row.add(format(outcome.accuracy()));
        row.add(format(outcome.precision()));
        row.add(format(outcome.recall()));
        row.add(format(outcome.fMeasure()));
        row.add(format(outcome.areaUnderRoc()));
        row.add(format(outcome.kappa()));
        row.add(String.valueOf(outcome.elapsed().toSeconds()));
        try {
            writer.write(row.toString());
            writer.flush();
        } catch (IOException e) {
            throw new MlException("Unable to append a row to the report '" + file + "'", e);
        }
    }

    @Override
    public void close() throws MlException {
        try {
            writer.close();
        } catch (IOException e) {
            throw new MlException("Unable to close the report '" + file + "'", e);
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
     * of the buggy class comes to when a model never predicted it
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
     * @param field the value of the column naming a row
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
