package it.uniroma2.dicii.isw2.ml;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.DatasetSplit;
import weka.core.Instances;

import java.util.List;

/**
 * Cuts the dataset into the rows a model is trained on and the rows it is finally tested on.
 */
public interface DatasetSplitter {

    /**
     * @param data the whole dataset, with the columns naming each row still on it — a split needs to
     *             read which release a row belongs to
     * @return the two halves, each stripped of those columns
     * @throws MlException if the dataset cannot be cut in two
     */
    DatasetSplit split(Instances data) throws MlException;

    /**
     * Collects the rows of some of the releases with the columns naming them still on, so that what one
     * split trained on can be split again — which is how a choice is made out of the training releases
     * alone, without reading the ones the final figure is measured on.
     *
     * @param data     the whole dataset, with the columns naming each row still on it
     * @param releases the releases whose rows to keep, as a split reports them
     * @return those rows, under the header of the whole
     */
    Instances rowsOfReleases(Instances data, List<String> releases);

}
