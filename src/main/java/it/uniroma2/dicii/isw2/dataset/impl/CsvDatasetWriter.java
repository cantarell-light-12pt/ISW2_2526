package it.uniroma2.dicii.isw2.dataset.impl;

import it.uniroma2.dicii.isw2.dataset.DatasetWriter;
import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import it.uniroma2.dicii.isw2.metrics.Metric;
import it.uniroma2.dicii.isw2.metrics.model.ClassMetrics;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.versions.model.Version;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Writes the dataset as a comma-separated file, one row per class of each released version.
 * <p>
 * The columns are fixed: the two naming the row, followed by one per {@link Metric}, in the order
 * the metrics are declared. Declaring every metric rather than only the ones the extractors managed
 * to measure is what keeps two runs comparable — the code smells are missing from a run performed
 * where no container could be started, and their columns are then empty rather than absent, so that
 * whoever reads the file does not have to discover its shape before reading it.
 */
@Slf4j
public class CsvDatasetWriter implements DatasetWriter {

    private static final char SEPARATOR = ',';
    private static final String RECORD_SEPARATOR = "\n";
    private static final char QUOTE = '"';

    /**
     * How many decimals a measure is written with. Most of the metrics are counts, but a few of them
     * are averages, and those do not terminate.
     */
    private static final int SCALE = 4;

    /**
     * The columns naming a row, and the key a row of the dataset is identified by: the released
     * version it describes, and the fully qualified name of the class of it that it measures. The
     * ordinal index of the version and the path of the source file are left out — the index is an
     * artefact of the numbering the Proportion method needs, and the path names the same class
     * differently across the releases that moved it, so neither identifies anything on its own.
     */
    private static final List<String> IDENTITY_COLUMNS = List.of("Version", "ClassName");

    private final Path file;
    private final BufferedWriter writer;

    @Getter
    private int rows;

    private CsvDatasetWriter(Path file, BufferedWriter writer) {
        this.file = file;
        this.writer = writer;
    }

    /**
     * Creates the dataset file, along with the directory meant to hold it, and writes its header.
     * Any file already at that path is overwritten: a dataset is the product of one whole run, and
     * appending the rows of a new run to the ones of an old one would describe a project that never
     * existed.
     * <p>
     * The header is written on its own, before the stream the rows are appended through is opened.
     * Writing it through that stream instead would leave it open, with nobody holding it and nobody
     * able to close it, whenever the header is the write that fails.
     *
     * @param file the file to write the dataset to
     * @return a writer appending to that file
     * @throws DatasetException if the file cannot be created or the header cannot be written
     */
    public static CsvDatasetWriter open(Path file) throws DatasetException {
        try {
            Path directory = file.getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            Files.writeString(file, header(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            log.info("Writing the dataset to {}", file);
            return new CsvDatasetWriter(file, writer);
        } catch (IOException e) {
            throw new DatasetException("Unable to create the dataset file '" + file + "'", e);
        }
    }

    @Override
    public void write(Version version, MetricsReport report) throws DatasetException {
        if (version == null) {
            throw new DatasetException("The measures of a snapshot taken outside the release history "
                    + "are no rows of the dataset, since nothing says which release they describe");
        }
        // A row is named by its version and its class alone, so two source files declaring the same
        // qualified name — the same class kept in two modules, say — would write two rows nothing tells
        // apart. It does not happen in the projects measured so far, and reporting it beats both
        // dropping a class that was measured and leaving a duplicate key to be discovered downstream
        Set<String> written = new HashSet<>();
        StringBuilder records = new StringBuilder();
        for (ClassMetrics metrics : report.getClasses()) {
            if (!written.add(metrics.getClassName())) {
                log.warn("Version {} declares class {} in {} as well, which the dataset already holds a "
                                + "row of: the two rows are told apart by nothing",
                        version.getName(), metrics.getClassName(), metrics.getPath());
            }
            records.append(row(version, metrics));
        }
        try {
            writer.write(records.toString());
            // Measuring a version costs minutes: flushing here, rather than when the writer is closed,
            // is what leaves the versions already measured on disk when a later one cannot be
            writer.flush();
        } catch (IOException e) {
            throw new DatasetException("Unable to write the rows of version '" + version.getName()
                    + "' to the dataset '" + file + "'", e);
        }
        rows += report.size();
        log.debug("Appended the {} classes of version {} to the dataset", report.size(), version.getName());
    }

    @Override
    public void close() throws DatasetException {
        try {
            writer.close();
        } catch (IOException e) {
            throw new DatasetException("Unable to close the dataset '" + file + "'", e);
        }
        log.info("The dataset written to {} holds {} rows", file, rows);
    }

    /**
     * @return the first row of the dataset, naming the columns every following row holds
     */
    private static String header() {
        StringJoiner header = createEmptyRecord();
        IDENTITY_COLUMNS.forEach(header::add);
        for (Metric metric : Metric.values()) {
            header.add(metric.name());
        }
        return header.toString();
    }

    /**
     * @param version the released version the measures were taken on
     * @param metrics the measures taken on one of its classes
     * @return the row of the dataset describing that class in that version
     */
    private static String row(Version version, ClassMetrics metrics) {
        StringJoiner row = createEmptyRecord();
        row.add(escape(version.getName()));
        row.add(escape(metrics.getClassName()));
        for (Metric metric : Metric.values()) {
            OptionalDouble value = metrics.get(metric);
            row.add(value.isPresent() ? format(value.getAsDouble()) : "");
        }
        return row.toString();
    }

    /**
     * @return an empty record, i.e. a joiner separating the fields of a row and terminating it
     */
    private static StringJoiner createEmptyRecord() {
        return new StringJoiner(String.valueOf(SEPARATOR), "", RECORD_SEPARATOR);
    }

    /**
     * Renders a measure the way the dataset should read it: the counts most of the metrics are as
     * plain integers rather than as {@code 42.0}, and the few that are averages with a bounded number
     * of decimals.
     * <p>
     * The rendering goes through {@link BigDecimal} rather than through {@code String.format}, which
     * would write the decimal separator of the default locale: on the machines a comma is the
     * separator of, every average would silently split its row in two.
     *
     * @param value the measure to render
     * @return its representation in the dataset, empty if it is not a finite number
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
     * break the record: the separator, a quote, or a line break. No version name and no class name
     * holds any of them today, but the writer has no way of knowing that, and a field splitting its
     * row in two would corrupt the dataset without failing anything.
     *
     * @param field the value of one of the columns naming a row
     * @return it, quoted if it has to be
     */
    private static String escape(String field) {
        if (field == null) {
            return "";
        }
        if (field.indexOf(SEPARATOR) < 0 && field.indexOf(QUOTE) < 0
                && field.indexOf('\n') < 0 && field.indexOf('\r') < 0) {
            return field;
        }
        return QUOTE + field.replace(String.valueOf(QUOTE), "\"\"") + QUOTE;
    }
}
