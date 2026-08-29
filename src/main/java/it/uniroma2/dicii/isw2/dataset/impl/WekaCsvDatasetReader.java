package it.uniroma2.dicii.isw2.dataset.impl;

import it.uniroma2.dicii.isw2.dataset.DatasetReader;
import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import lombok.extern.slf4j.Slf4j;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Reads the comma-separated dataset {@link CsvDatasetWriter} produces into Weka instances.
 * <p>
 * Three things happen on the way in, none of which the file itself says:
 * <ul>
 * <li>the label is forced to be <b>nominal</b>. It is written as {@code 0} or {@code 1}, which a loader
 * left to guess reads as a number, and a numeric class turns the file into a regression problem — every
 * classifier would refuse it, and the ones that did not would be answering a different question;</li>
 * <li>the columns naming a row are <b>dropped</b>. The version and the class name identify a row, they
 * do not describe it: kept as attributes, the name of the class alone would let a learner memorise
 * which classes of this one project were found faulty, which is not a finding about anything;</li>
 * <li>the label becomes the <b>class</b> attribute.</li>
 * </ul>
 * The columns are checked against the ones the writer declares rather than dropped by position: the
 * two are the same file's ends, and a dataset whose shape has moved should say so here rather than
 * silently cost the analysis two metrics.
 */
@Slf4j
public class WekaCsvDatasetReader implements DatasetReader {

    @Override
    public Instances read(Path file) throws DatasetException {
        return withoutIdentityColumns(file, readRetainingIdentity(file));
    }

    /**
     * Reads the file with the columns naming each row still on it.
     * <p>
     * They are of no use to a classifier, and {@link #read(Path)} is what hands over the rows one is
     * trained on. They are what a release-aware split reads, though: cutting the training set from the
     * test set at a release means knowing which release each row belongs to, and that has to happen
     * before the column saying so is dropped.
     *
     * @param file the dataset to read
     * @return its rows, every column included, with the label already set as the class
     * @throws DatasetException if the file is not the comma-separated dataset it is expected to be
     */
    public Instances readRetainingIdentity(Path file) throws DatasetException {
        if (!Files.isReadable(file)) {
            throw new DatasetException("The dataset '" + file + "' does not exist, or cannot be read. "
                    + "It is the file the extraction workflow writes, which has to have run first");
        }
        Instances data = load(file);
        checkColumns(file, data);
        data.setClassIndex(data.numAttributes() - 1);
        checkLabel(file, data);
        log.info("Read {} rows of {} from {}, described by {} metrics and labelled {}",
                data.numInstances(), data.relationName(), file,
                data.numAttributes() - 1 - CsvDatasetWriter.IDENTITY_COLUMNS.size(),
                data.classAttribute().name());
        return data;
    }

    /**
     * Loads the file as it is written, forcing nothing but the type of the label.
     *
     * @param file the dataset to read
     * @return its rows, columns and all
     * @throws DatasetException if the file is not the comma-separated dataset it is expected to be
     */
    private static Instances load(Path file) throws DatasetException {
        CSVLoader loader = new CSVLoader();
        // The label is the last column, and is the only one whose type cannot be guessed from how it
        // reads: see the note on the class
        loader.setNominalAttributes("last");
        try {
            loader.setSource(new File(file.toString()));
            return loader.getDataSet();
        } catch (Exception e) {
            // CSVLoader.getDataSet reports anything from a missing file to a ragged row as a bare
            // Exception, which leaves nothing more specific to catch
            throw new DatasetException("Unable to read the dataset '" + file + "'", e);
        }
    }

    /**
     * Checks that the file holds the columns the writer of a dataset declares, in the order it writes
     * them.
     *
     * @param file the dataset being read, named in the report
     * @param data its rows, as they were loaded
     * @throws DatasetException if a column is not where the writer put it
     */
    private static void checkColumns(Path file, Instances data) throws DatasetException {
        List<String> identityColumns = CsvDatasetWriter.IDENTITY_COLUMNS;
        if (data.numAttributes() <= identityColumns.size()) {
            throw new DatasetException("The dataset '" + file + "' holds " + data.numAttributes()
                    + " columns, which is no more than the " + identityColumns.size()
                    + " naming a row: there is nothing in it to learn from");
        }
        for (int i = 0; i < identityColumns.size(); i++) {
            String expected = identityColumns.get(i);
            String found = data.attribute(i).name();
            if (!expected.equals(found)) {
                throw new DatasetException("Column " + (i + 1) + " of the dataset '" + file + "' is '"
                        + found + "' where the dataset is written with '" + expected + "'");
            }
        }
        String label = data.attribute(data.numAttributes() - 1).name();
        if (!CsvDatasetWriter.LABEL_COLUMN.equals(label)) {
            throw new DatasetException("The last column of the dataset '" + file + "' is '" + label
                    + "' where the dataset is written with '" + CsvDatasetWriter.LABEL_COLUMN
                    + "', which is the label a classifier is trained to predict");
        }
    }

    /**
     * Drops the columns naming a row, leaving the ones measuring it and the one labelling it.
     * <p>
     * Public because a split cuts the rows while they still say which release they belong to, and then
     * has two sets of them to strip rather than one.
     *
     * @param file the dataset being read, named in the report
     * @param data its rows, as they were loaded
     * @return the same rows, described by their metrics alone, with the label set as the class
     * @throws DatasetException if the columns cannot be dropped
     */
    public static Instances withoutIdentityColumns(Path file, Instances data) throws DatasetException {
        int[] identityIndices = new int[CsvDatasetWriter.IDENTITY_COLUMNS.size()];
        for (int i = 0; i < identityIndices.length; i++) {
            identityIndices[i] = i;
        }
        Remove remove = new Remove();
        remove.setAttributeIndicesArray(identityIndices);
        try {
            remove.setInputFormat(data);
            Instances stripped = Filter.useFilter(data, remove);
            // Set again rather than left to the filter: the label is still the last column, but its
            // index has moved by however many columns were dropped from in front of it
            stripped.setClassIndex(stripped.numAttributes() - 1);
            return stripped;
        } catch (Exception e) {
            // Filter.useFilter reports every failure as a bare Exception
            throw new DatasetException("Unable to drop the columns naming the rows of the dataset '"
                    + file + "'", e);
        }
    }

    /**
     * Checks that the label came out as a class a classifier can be trained on, rather than as the
     * numbers it is written as.
     *
     * @param file the dataset being read, named in the report
     * @param data its rows, with the label already set as the class
     * @throws DatasetException if the label is not nominal, or does not read the way it is written
     */
    private static void checkLabel(Path file, Instances data) throws DatasetException {
        Attribute label = data.classAttribute();
        if (!label.isNominal()) {
            throw new DatasetException("The label '" + label.name() + "' of the dataset '" + file
                    + "' was read as " + Attribute.typeToString(label)
                    + " rather than as the class a classifier predicts");
        }
        if (label.indexOfValue(CsvDatasetWriter.BUGGY) < 0) {
            throw new DatasetException("No row of the dataset '" + file + "' is labelled '"
                    + CsvDatasetWriter.BUGGY + "': the label reads " + valuesOf(label)
                    + ", so there is no buggy class to predict");
        }
    }

    /**
     * @param label the class attribute of the dataset
     * @return the values it takes, as they read in the file
     */
    private static String valuesOf(Attribute label) {
        List<String> values = new ArrayList<>(label.numValues());
        for (int i = 0; i < label.numValues(); i++) {
            values.add(label.value(i));
        }
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        values.forEach(joiner::add);
        return joiner.toString();
    }

}
