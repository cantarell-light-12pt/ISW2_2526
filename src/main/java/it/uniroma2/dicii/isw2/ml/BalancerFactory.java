package it.uniroma2.dicii.isw2.ml;

import it.uniroma2.dicii.isw2.ml.model.BalancingKind;

/**
 * Builds the balancer one {@link BalancingKind} names, configured as the settings ask.
 * <p>
 * It exists for the same reason {@link ModelFactory} does: nothing above it has to name a Weka filter,
 * so adding a third technique to the comparison is a value of the enum and a branch here, and no change
 * at all to whatever reads the balanced rows.
 */
public interface BalancerFactory {

    /**
     * @param kind which technique to balance with
     * @return a fresh balancer applying it. Fresh every time, for the same reason a model is: a filter
     * carries the header it was last given, and one reused across two datasets would resample the
     * second against the shape of the first
     */
    DatasetBalancer build(BalancingKind kind);

}
