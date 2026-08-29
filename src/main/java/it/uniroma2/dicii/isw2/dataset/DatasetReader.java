package it.uniroma2.dicii.isw2.dataset;

import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import weka.core.Instances;

import java.nio.file.Path;

/**
 * Reads back the dataset a {@link DatasetWriter} produced, as the set of instances a learner is given.
 * <p>
 * Reading is the inverse of writing and belongs beside it, since the two share what nobody else knows:
 * which columns name a row rather than measure it, and how the label reads. What comes out is no longer
 * the file, though — the columns naming a row are identifiers and not predictors, so a reader is
 * expected to drop them, and the label has to arrive as a class and not as the number it is written as.
 */
public interface DatasetReader {

    /**
     * Reads the dataset into the instances a learner can be trained on: one attribute per metric, and
     * the buggy/not-buggy label as the class.
     *
     * @param file the dataset to read
     * @return its rows, without the columns naming them, with the label as the class attribute
     * @throws DatasetException if the file cannot be read, or does not hold the columns a dataset does
     */
    Instances read(Path file) throws DatasetException;

}
