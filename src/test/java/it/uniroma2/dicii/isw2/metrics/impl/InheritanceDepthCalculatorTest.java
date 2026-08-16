package it.uniroma2.dicii.isw2.metrics.impl;

import com.github.javaparser.StaticJavaParser;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Measures the depth of hierarchies whose depth can be counted by hand: the ones a snapshot declares
 * from end to end, the ones that leave it for the Java runtime, and the ones that leave it for good.
 */
public class InheritanceDepthCalculatorTest {

    private InheritanceDepthCalculator calculator;

    @Before
    public void setUp() {
        calculator = new InheritanceDepthCalculator();
    }

    /**
     * The root of every hierarchy is not counted, so a class extending nothing is worth 1.
     */
    @Test
    public void testAClassExtendingNothingIsOneDeep() {
        declare("package sample; public class Base {}");
        assertEquals(1, calculator.depthOf("sample.Base"));
    }

    /**
     * A chain declared by the snapshot is followed to its end, one class at a time.
     */
    @Test
    public void testAChainOfTheSnapshotIsFollowedToItsEnd() {
        declare("package sample; public class Base {}");
        declare("package sample; public class Child extends Base {}");
        declare("package sample; public class GrandChild extends Child {}");
        assertEquals(2, calculator.depthOf("sample.Child"));
        assertEquals(3, calculator.depthOf("sample.GrandChild"));
    }

    /**
     * A chain crossing packages is followed through whichever way the source file names its superclass.
     */
    @Test
    public void testASuperclassOfAnotherPackageIsResolvedThroughTheImports() {
        declare("package sample.base; public class Base {}");
        declare("package sample; import sample.base.Base; public class Imported extends Base {}");
        declare("package sample; import sample.base.*; public class OnDemand extends Base {}");
        declare("package sample; public class Qualified extends sample.base.Base {}");
        assertEquals(2, calculator.depthOf("sample.Imported"));
        assertEquals(2, calculator.depthOf("sample.OnDemand"));
        assertEquals(2, calculator.depthOf("sample.Qualified"));
    }

    /**
     * A chain leaving the snapshot for the Java runtime carries on through it, which is what tells an
     * exception apart from a plain class: {@code Thread} sits one class below the root, while both
     * {@code IOException} and {@code RuntimeException} sit three, under {@code Exception} and
     * {@code Throwable}.
     */
    @Test
    public void testAChainLeavingTheSnapshotCarriesOnThroughTheRuntime() {
        declare("package sample; public class Worker extends Thread {}");
        declare("package sample; import java.io.IOException; public class Failure extends IOException {}");
        declare("package sample; public class Unchecked extends RuntimeException {}");
        assertEquals(2, calculator.depthOf("sample.Worker"));
        assertEquals(4, calculator.depthOf("sample.Failure"));
        assertEquals(4, calculator.depthOf("sample.Unchecked"));
    }

    /**
     * A superclass that is neither of the snapshot nor of the runtime was still named, so it is not the
     * root: the class extending it is measured as sitting one level below one level below the root.
     */
    @Test
    public void testASuperclassNowhereToBeFoundIsOneLevelBelowTheRoot() {
        declare("package sample; import org.third.party.Widget; public class Custom extends Widget {}");
        assertEquals(2, calculator.depthOf("sample.Custom"));
    }

    /**
     * Only single inheritance counts. The interfaces a class implements are no part of its depth, and
     * neither is what an interface extends.
     */
    @Test
    public void testWhatIsImplementedIsNoPartOfTheDepth() {
        declare("package sample; public interface Named {}");
        declare("package sample; public interface Titled extends Named {}");
        declare("package sample; public class Labelled implements Named, Titled {}");
        assertEquals(1, calculator.depthOf("sample.Titled"));
        assertEquals(1, calculator.depthOf("sample.Labelled"));
    }

    /**
     * The regression this calculator exists for. CK runs the metrics of a class over every type nested
     * in it and never resets the depth it accumulates, so a class declaring subclasses of itself comes
     * out as deep as itself plus every one of them — which is how {@code KeeperException}, an exception
     * three classes deep, was measured 96 deep.
     */
    @Test
    public void testTheNestedTypesOfAClassAreNoPartOfItsOwnDepth() {
        declare("""
                package sample;

                public class Failure extends Exception {
                    public static class Missing extends Failure {}
                    public static class Denied extends Failure {}
                    public static class Expired extends Denied {}
                }
                """);
        assertEquals("Failure, Exception and Throwable", 3, calculator.depthOf("sample.Failure"));
    }

    /**
     * A source tree being mined is whatever was committed, which does not have to compile: a hierarchy
     * closing on itself has to come out with some bounded measure rather than be followed forever. The
     * measure is the chain up to where it closes, plus the one a superclass nowhere to be found is
     * worth — nothing to read into, beyond it being finite and the same on every run.
     */
    @Test
    public void testAHierarchyThatClosesOnItselfIsStillMeasured() {
        declare("package sample; public class Chicken extends Egg {}");
        declare("package sample; public class Egg extends Chicken {}");
        assertEquals(4, calculator.depthOf("sample.Chicken"));
    }

    /**
     * Declares a source file of the snapshot being measured.
     *
     * @param source the whole content of the file
     */
    private void declare(String source) {
        calculator.declare(StaticJavaParser.parse(source));
    }
}
