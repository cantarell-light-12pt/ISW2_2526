package it.uniroma2.dicii.isw2.proportion;

import it.uniroma2.dicii.isw2.proportion.exception.ProportionException;
import it.uniroma2.dicii.isw2.proportion.impl.IncrementalProportion;
import it.uniroma2.dicii.isw2.proportion.impl.SimpleProportion;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ProportionStrategyFactoryTest {

    private ProportionStrategyFactory factory;

    @Before
    public void setUp() {
        factory = new ProportionStrategyFactory();
    }

    @Test
    public void createsTheSimpleStrategy() throws ProportionException {
        Assert.assertTrue(factory.getStrategy(ProportionMethod.SIMPLE) instanceof SimpleProportion);
    }

    @Test
    public void createsTheIncrementalStrategy() throws ProportionException {
        Assert.assertTrue(factory.getStrategy(ProportionMethod.INCREMENTAL) instanceof IncrementalProportion);
    }

    @Test
    public void createsTheStrategyFromItsConfiguredName() throws ProportionException {
        Assert.assertTrue(factory.getStrategy("incremental") instanceof IncrementalProportion);
        Assert.assertTrue(factory.getStrategy("simple") instanceof SimpleProportion);
    }

    @Test
    public void matchesTheConfiguredNameIgnoringItsCase() throws ProportionException {
        Assert.assertTrue(factory.getStrategy("Incremental") instanceof IncrementalProportion);
        Assert.assertTrue(factory.getStrategy("SIMPLE") instanceof SimpleProportion);
    }

    @Test(expected = ProportionException.class)
    public void rejectsAnUnknownMethodName() throws ProportionException {
        factory.getStrategy("cold-start");
    }

    @Test(expected = ProportionException.class)
    public void rejectsAMissingMethodName() throws ProportionException {
        factory.getStrategy((String) null);
    }

    @Test(expected = ProportionException.class)
    public void rejectsANullMethod() throws ProportionException {
        factory.getStrategy((ProportionMethod) null);
    }

    @Test
    public void reportsTheKnownMethodsWhenRejectingAnUnknownOne() {
        try {
            factory.getStrategy("cold-start");
            Assert.fail("An unknown method must be rejected");
        } catch (ProportionException e) {
            Assert.assertTrue("The error must list the known methods, got: " + e.getMessage(),
                    e.getMessage().contains("simple") && e.getMessage().contains("incremental"));
        }
    }
}
