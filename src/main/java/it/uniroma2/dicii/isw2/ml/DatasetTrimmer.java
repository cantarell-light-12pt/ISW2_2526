package it.uniroma2.dicii.isw2.ml;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import weka.core.Instances;

/**
 * Cuts the dataset down to the part of the release history a model is studied on.
 * <p>
 * The extraction workflow writes the whole history, and it is here that the part of it worth learning
 * from is chosen: the buggy label of a release is only as good as the defects reported against it, and
 * the last releases of a project have had the least time to have any reported.
 */
public interface DatasetTrimmer {

    /**
     * @param data the whole dataset, with the columns naming each row still on it — trimming needs to
     *             read which release a row belongs to
     * @return the rows of the releases kept, under the header of the whole and with those columns still
     * on, so that they can be written out as the whole would be
     * @throws MlException if the dataset cannot be trimmed, or what is left of it holds nothing to learn
     */
    Instances trim(Instances data) throws MlException;

}
