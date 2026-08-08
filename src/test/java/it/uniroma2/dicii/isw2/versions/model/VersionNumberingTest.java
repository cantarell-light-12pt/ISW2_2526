package it.uniroma2.dicii.isw2.versions.model;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VersionNumberingTest {

    private static Version version(String name) {
        return new Version("id-" + name, name, true, false);
    }

    @Test
    public void numberVersionsSortsFromTheOldestToTheNewest() {
        Version v1_2 = version("1.2.0");
        Version v1_10 = version("1.10.0");
        Version v1_0 = version("1.0.0");
        List<Version> versions = new ArrayList<>(Arrays.asList(v1_10, v1_0, v1_2));

        Version.numberVersions(versions);

        Assert.assertEquals("The oldest version must come first", v1_0, versions.get(0));
        Assert.assertEquals(v1_2, versions.get(1));
        Assert.assertEquals("1.10.0 must be newer than 1.2.0", v1_10, versions.get(2));
    }

    @Test
    public void numberVersionsAssignsContiguousOneBasedIndices() {
        Version v1_0 = version("1.0.0");
        Version v1_2 = version("1.2.0");
        Version v1_10 = version("1.10.0");
        List<Version> versions = new ArrayList<>(Arrays.asList(v1_10, v1_0, v1_2));

        Version.numberVersions(versions);

        Assert.assertEquals("The oldest version must be numbered 1", 1, v1_0.getIndex());
        Assert.assertEquals(2, v1_2.getIndex());
        Assert.assertEquals(3, v1_10.getIndex());
    }

    @Test
    public void numberVersionsClosesTheGapsLeftByRemovedVersions() {
        Version v1_0 = version("1.0.0");
        Version v1_2 = version("1.2.0");
        Version v1_10 = version("1.10.0");
        List<Version> versions = new ArrayList<>(Arrays.asList(v1_0, v1_2, v1_10));
        Version.numberVersions(versions);

        // Mimics the pruning of the versions having no Git tag
        versions.remove(v1_2);
        Version.numberVersions(versions);

        Assert.assertEquals(1, v1_0.getIndex());
        Assert.assertEquals("Renumbering must leave no gap behind the removed version", 2, v1_10.getIndex());
    }

    @Test
    public void numberVersionsTreatsMissingSegmentsAsZero() {
        Version v1 = version("1.2");
        Version v2 = version("1.2.1");
        List<Version> versions = new ArrayList<>(Arrays.asList(v2, v1));

        Version.numberVersions(versions);

        Assert.assertEquals("1.2 must be older than 1.2.1", 1, v1.getIndex());
        Assert.assertEquals(2, v2.getIndex());
    }

    @Test
    public void numberVersionsAcceptsAnEmptyList() {
        List<Version> versions = new ArrayList<>();

        Version.numberVersions(versions);

        Assert.assertTrue(versions.isEmpty());
    }
}
