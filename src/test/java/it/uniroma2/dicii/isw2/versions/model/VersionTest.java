package it.uniroma2.dicii.isw2.versions.model;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class VersionTest {

    private static final String V1_2_0 = "1.2.0";
    private static final String MESSAGE_FORMAT = "Expected: %s %s %s";

    private final String v1Name;
    private final String v2Name;
    private final int expectedResult; // -1 for <, 0 for ==, 1 for >

    public VersionTest(String v1Name, String v2Name, int expectedResult) {
        this.v1Name = v1Name;
        this.v2Name = v2Name;
        this.expectedResult = expectedResult;
    }

    @Parameterized.Parameters(name = "{index}: compare({0}, {1}) should be {2}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{{V1_2_0, "1.10.0", -1}, {"1.10.0", V1_2_0, 1}, {V1_2_0, "1.2", 0}, {"1.2", V1_2_0, 0}, {V1_2_0, V1_2_0, 0}, {"2.0.0", "1.9.9", 1}, {"1.0.0", "2.0.0", -1}, {"1.0", "1.0.1", -1}, {"1.0.1", "1.0", 1}});
    }

    @Test
    public void testCompareTo() {
        Version v1 = new Version("id1", v1Name, LocalDate.now(), true, false);
        Version v2 = new Version("id2", v2Name, LocalDate.now(), true, false);

        int result = v1.compareTo(v2);

        if (expectedResult < 0) {
            Assert.assertTrue(String.format(MESSAGE_FORMAT, v1Name, "<", v2Name), result < 0);
        } else if (expectedResult > 0) {
            Assert.assertTrue(String.format(MESSAGE_FORMAT, v1Name, ">", v2Name), result > 0);
        } else {
            Assert.assertEquals(String.format(MESSAGE_FORMAT, v1Name, "==", v2Name), 0, result);
        }
    }
}
