package it.uniroma2.dicii.isw2.ml;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.DatasetSplit;
import weka.core.Instances;

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

}
