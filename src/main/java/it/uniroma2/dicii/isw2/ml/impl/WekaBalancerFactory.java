package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.BalancerFactory;
import it.uniroma2.dicii.isw2.ml.DatasetBalancer;
import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.model.BalancingKind;

/**
 * Builds the Weka filters the training rows are balanced through.
 * <p>
 * Two of the three draw at random and are therefore seeded out of the settings, so that two runs of the
 * same technique resample the same rows; the third draws nothing and has nothing to configure. They are
 * handed the seed and not the settings, since a balancer is serialised along with the model it is part
 * of and the seed is all of the settings it reads.
 */
public class WekaBalancerFactory implements BalancerFactory {

    private final MlSettings settings;

    public WekaBalancerFactory(MlSettings settings) {
        this.settings = settings;
    }

    @Override
    public DatasetBalancer build(BalancingKind kind) {
        return switch (kind) {
            case NONE -> new NoResampling();
            case UNDERSAMPLING -> new MajorityUndersampler(settings.seed());
            case SMOTE -> new SmoteOversampler(settings.seed());
        };
    }

}
