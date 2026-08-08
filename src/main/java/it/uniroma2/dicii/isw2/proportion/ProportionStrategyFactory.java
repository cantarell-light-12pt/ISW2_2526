package it.uniroma2.dicii.isw2.proportion;

import it.uniroma2.dicii.isw2.proportion.exception.ProportionException;
import it.uniroma2.dicii.isw2.proportion.impl.IncrementalProportion;
import it.uniroma2.dicii.isw2.proportion.impl.SimpleProportion;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates the {@link ProportionStrategy} implementing a given variant of the Proportion approach,
 * letting the method used to estimate the injected versions be chosen at runtime — typically from the
 * {@code project.proportion.method} configuration property — without the rest of the application
 * depending on any concrete implementation.
 */
@Slf4j
public class ProportionStrategyFactory {

    /**
     * Creates the strategy implementing the given method.
     *
     * @param method the variant of the Proportion approach to use
     * @return the corresponding strategy
     * @throws ProportionException if the method is null
     */
    public ProportionStrategy getStrategy(ProportionMethod method) throws ProportionException {
        if (method == null) {
            throw new ProportionException("The proportion method cannot be null");
        }
        log.info("Estimating the injected versions through the {} method", method.getMethod());
        return switch (method) {
            case SIMPLE -> new SimpleProportion();
            case INCREMENTAL -> new IncrementalProportion();
        };
    }

    /**
     * Creates the strategy implementing the method with the given name, as read from the configuration.
     *
     * @param method the name of the variant to use, e.g. "simple" or "incremental"
     * @return the corresponding strategy
     * @throws ProportionException if the name does not match any known method
     */
    public ProportionStrategy getStrategy(String method) throws ProportionException {
        return getStrategy(ProportionMethod.from(method));
    }
}
