package it.uniroma2.dicii.isw2.buggyness.model;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Checks the one question the index is asked while the dataset is written: whether a class held a
 * defect at a release.
 */
public class BuggyClassesTest {

    private static final String BROKEN = "app.Broken";
    private static final String SOUND = "app.Sound";

    @Test
    public void testAClassIsBuggyOnlyAtTheReleasesItWasRecordedAt() {
        BuggyClasses buggy = new BuggyClasses();
        buggy.recordDefect(2, Set.of(BROKEN));

        assertTrue(buggy.holds(2, BROKEN));
        assertFalse("Another release of the same class", buggy.holds(3, BROKEN));
        assertFalse("Another class of the same release", buggy.holds(2, SOUND));
    }

    @Test
    public void testAReleaseNothingWasRecordedAtHoldsNothing() {
        BuggyClasses buggy = new BuggyClasses();

        assertFalse(buggy.holds(1, BROKEN));
        assertEquals(0, buggy.size(1));
        assertEquals(0, buggy.releases());
    }

    /**
     * Two defects fixed in the same class leave that class buggy once: the dataset holds one row per
     * class per release, and the label is a yes or a no rather than a count.
     */
    @Test
    public void testTwoDefectsInTheSameClassLeaveOneEntry() {
        BuggyClasses buggy = new BuggyClasses();
        buggy.recordDefect(2, List.of(BROKEN, SOUND));
        buggy.recordDefect(2, List.of(BROKEN));

        assertEquals(2, buggy.size(2));
        assertEquals(1, buggy.releases());
    }
}
