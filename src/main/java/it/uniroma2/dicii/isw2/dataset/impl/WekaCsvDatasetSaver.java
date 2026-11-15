package it.uniroma2.dicii.isw2.dataset.impl;

import it.uniroma2.dicii.isw2.dataset.DatasetSaver;
import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import lombok.extern.slf4j.Slf4j;
import weka.core.Instances;
import weka.core.converters.CSVSaver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes Weka instances as the comma-separated dataset {@link CsvDatasetWriter} produces, so that
 * {@link WekaCsvDatasetReader} reads them back as it reads the whole dataset.
 * <p>
 * Two things keep the file in that shape. A measure that was never taken is written as an empty field,
 * as the extraction writes it, rather than as the {@code ?} Weka would write, which no other reader of
 * the dataset would take for a missing value. And every column is written, those naming a row
 * included: they are what the release a row belongs to is read off, and dropping them is the reader's
 * job.
 */
@Slf4j
public class WekaCsvDatasetSaver implements DatasetSaver {

    /**
     * How a measure that was never taken reads in the dataset.
     */
    private static final String MISSING_VALUE = "";

    @Override
    public void save(Instances data, Path file) throws DatasetException {
        try {
            Path directory = file.toAbsolutePath().getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
        } catch (IOException e) {
            throw new DatasetException("Unable to create the directory of the dataset '" + file + "'", e);
        }
        CSVSaver saver = new CSVSaver();
        saver.setMissingValue(MISSING_VALUE);
        try {
            saver.setInstances(data);
            saver.setFile(file.toFile());
            saver.writeBatch();
        } catch (IOException e) {
            throw new DatasetException("Unable to write the dataset '" + file + "'", e);
        }
        log.info("Wrote {} rows of {} to {}", data.numInstances(), data.relationName(), file);
    }

}
