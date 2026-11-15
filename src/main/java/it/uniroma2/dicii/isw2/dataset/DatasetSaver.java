package it.uniroma2.dicii.isw2.dataset;

import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import weka.core.Instances;

import java.nio.file.Path;

/**
 * Writes out a dataset that was read and reshaped as instances, in a form a {@link DatasetReader} reads
 * back.
 * <p>
 * Apart from {@link DatasetWriter}, which writes the measures of a release as they are taken: what is
 * saved here is a dataset that already exists as rows, such as the part of the release history a model
 * is studied on.
 */
public interface DatasetSaver {

    /**
     * Writes the rows to the file, replacing whatever it held.
     *
     * @param data the rows to write, with every column they carry
     * @param file where to write them
     * @throws DatasetException if the file cannot be written
     */
    void save(Instances data, Path file) throws DatasetException;

}
