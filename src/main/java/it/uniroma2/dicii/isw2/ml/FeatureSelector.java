package it.uniroma2.dicii.isw2.ml;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.FeatureSelectionKind;
import weka.classifiers.Classifier;
import weka.core.Instances;

import java.util.List;

/**
 * Chooses which of the attributes of the training rows a model is trained on, as one
 * {@link FeatureSelectionKind} names.
 * <p>
 * The choice is made of the <b>training</b> rows alone, for the reason the balancing is: an attribute
 * kept because it told the label of the rows a model is then measured on has been chosen with the
 * answer in hand, and the figure measured over it is an estimate of nothing. So the selection is part of
 * the model, as the balancing is, and a cross-validation choosing anew inside every fold chooses out of
 * its nine tenths and never out of the tenth it holds out.
 * <p>
 * It exists for the same reason {@link BalancerFactory} does: nothing above it has to name a Weka
 * evaluator or search, so a fourth technique is a value of the enum and a branch here.
 */
public interface FeatureSelector {

    /**
     * @param model the model to train on the attributes kept, untrained
     * @param kind  which technique to keep them by
     * @return a model choosing the attributes out of whatever it is trained on, training the given one on
     * them, and reducing every row it is asked about to them before handing it over. The model itself
     * when every attribute is kept, so that a model trained on all of them is the very one it was before
     * selection existed
     */
    Classifier selecting(Classifier model, FeatureSelectionKind kind);

    /**
     * Runs a technique over some rows and reports what it keeps, for a reader to see which metrics a
     * selection is made of: the attributes a model actually trains on are chosen anew out of every set
     * of rows it is trained on, and are never read back out of it.
     *
     * @param data the labelled rows to choose out of, with their class attribute set
     * @param kind which technique to keep them by
     * @return the names of the attributes kept, the label not among them, in the order the technique
     * ranks them — or the order the rows declare them in, when it does not rank them
     * @throws MlException if the technique cannot be run over the rows
     */
    List<String> select(Instances data, FeatureSelectionKind kind) throws MlException;

}
