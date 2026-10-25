package it.uniroma2.dicii.isw2.ml;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.BalancingKind;
import weka.core.Instances;

import java.io.Serializable;

/**
 * Resamples labelled rows so that the buggy class and the not-buggy one are equally represented.
 * <p>
 * What it is handed is the <b>training</b> half of the split, and never the test half. Balancing is a
 * way of telling a model what to pay attention to, so it belongs where the model is taught; resampling
 * the rows it is finally measured on would instead change the population the figure is an estimate of,
 * and the figure would stop being an estimate of anything the project could be reported on.
 * <p>
 * Two invariants hold of every implementation, and the second follows from the first:
 * <ul>
 * <li>the rows it is given are <b>not</b> written into — what comes back is a set of its own, so a
 * caller can balance one half of a split without the other half changing under it;</li>
 * <li>the header of what comes back equals the header of what went in. A model trained on balanced rows
 * is tested on rows nothing has touched, and Weka will only put the two together if they agree on their
 * attributes.</li>
 * </ul>
 * It is {@link Serializable} because it travels inside the model it balances the training rows of:
 * Weka hands every fold of a cross-validation a copy of the model, and copies it by serialising it.
 */
public interface DatasetBalancer extends Serializable {

    /**
     * Balances the given rows.
     *
     * @param data the labelled rows to balance, with their class attribute set
     * @return them resampled, as a set of its own and under the same header. Rows of which only one
     * class is present come back as they are: there is nothing to balance, which is not a failure.
     * Rows whose classes are already the same size are still resampled — the balance is what is asked
     * for and what is returned, and a technique that draws with replacement will hand back a different
     * set of rows holding it
     * @throws MlException if the rows are missing, do not say which of their attributes is the label, or
     *                     cannot be resampled
     * @see BalancingKind
     */
    Instances balance(Instances data) throws MlException;

}
