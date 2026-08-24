package it.uniroma2.dicii.isw2.versions.model;

import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VersionNumberingTest {

    private static Version version(String name) {
        return new Version("id-" + name, name, true, false);
    }

    private static Version version(String name, LocalDateTime releaseDate) {
        Version version = version(name);
        version.setReleaseDate(releaseDate);
        return version;
    }

    private static LocalDateTime at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute);
    }

    @Test
    public void numberVersionsSortsFromTheOldestToTheNewest() {
        Version middle = version("1.2.0");
        Version newest = version("1.10.0");
        Version oldest = version("1.0.0");
        List<Version> versions = new ArrayList<>(Arrays.asList(newest, oldest, middle));

        Version.numberVersions(versions);

        Assert.assertEquals("The oldest version must come first", oldest, versions.get(0));
        Assert.assertEquals(middle, versions.get(1));
        Assert.assertEquals("1.10.0 must be newer than 1.2.0", newest, versions.get(2));
    }

    @Test
    public void numberVersionsAssignsContiguousOneBasedIndices() {
        Version oldest = version("1.0.0");
        Version middle = version("1.2.0");
        Version newest = version("1.10.0");
        List<Version> versions = new ArrayList<>(Arrays.asList(newest, oldest, middle));

        Version.numberVersions(versions);

        Assert.assertEquals("The oldest version must be numbered 1", 1, oldest.getIndex());
        Assert.assertEquals(2, middle.getIndex());
        Assert.assertEquals(3, newest.getIndex());
    }

    @Test
    public void numberVersionsClosesTheGapsLeftByRemovedVersions() {
        Version oldest = version("1.0.0");
        Version middle = version("1.2.0");
        Version newest = version("1.10.0");
        List<Version> versions = new ArrayList<>(Arrays.asList(oldest, middle, newest));
        Version.numberVersions(versions);

        // Mimics the pruning of the versions having no Git tag
        versions.remove(middle);
        Version.numberVersions(versions);

        Assert.assertEquals(1, oldest.getIndex());
        Assert.assertEquals("Renumbering must leave no gap behind the removed version", 2, newest.getIndex());
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

    /**
     * A project maintaining two lines at once does not release them in the order their numbers suggest:
     * ZooKeeper published 3.6.0 more than two years before 3.5.10, which the numbering must reproduce,
     * or every release of the older line comes out claiming to hold work written after it.
     */
    @Test
    public void numberVersionsOrdersByReleaseDateAndNotByName() {
        Version maintenance = version("3.5.10", at(2022, 5, 29, 11, 6));
        Version newLine = version("3.6.0", at(2020, 2, 25, 14, 17));
        List<Version> versions = new ArrayList<>(Arrays.asList(maintenance, newLine));

        Version.numberVersions(versions);

        Assert.assertEquals("The release published first must be numbered first", 1, newLine.getIndex());
        Assert.assertEquals("3.5.10 was released after 3.6.0, whatever its name says", 2, maintenance.getIndex());
    }

    /**
     * The two releases of a same day belong to different lines, so the older name is not the older
     * release: ZooKeeper cut 3.4.0 fifteen hours before 3.3.4.
     */
    @Test
    public void numberVersionsSeparatesTwoReleasesOfTheSameDayByTheirTime() {
        Version morning = version("3.4.0", at(2011, 11, 23, 7, 19));
        Version evening = version("3.3.4", at(2011, 11, 23, 22, 48));
        List<Version> versions = new ArrayList<>(Arrays.asList(evening, morning));

        Version.numberVersions(versions);

        Assert.assertEquals("The release cut in the morning must come first", 1, morning.getIndex());
        Assert.assertEquals(2, evening.getIndex());
    }

    /**
     * The eleven releases ZooKeeper had published by the time it moved from Subversion to Git all carry
     * the timestamp of the import, so the only thing left to order them by is their name.
     */
    @Test
    public void numberVersionsBreaksATieOnTheReleaseNames() {
        LocalDateTime imported = at(2010, 11, 24, 21, 19);
        Version newest = version("3.3.2", imported);
        Version oldest = version("3.0.0", imported);
        Version middle = version("3.2.0", imported);
        List<Version> versions = new ArrayList<>(Arrays.asList(newest, oldest, middle));

        Version.numberVersions(versions);

        Assert.assertEquals("Releases sharing a date must fall back on their name", 1, oldest.getIndex());
        Assert.assertEquals(2, middle.getIndex());
        Assert.assertEquals(3, newest.getIndex());
    }

    /**
     * The versions are numbered once when they are read from Jira, before any tag has said when they
     * were released, and again once the tags have.
     */
    @Test
    public void numberVersionsSortsTheVersionsOfUnknownReleaseDateLast() {
        Version dated = version("2.0.0", at(2021, 1, 1, 0, 0));
        Version undated = version("1.0.0");
        List<Version> versions = new ArrayList<>(Arrays.asList(undated, dated));

        Version.numberVersions(versions);

        Assert.assertEquals("A release of known date must precede one of unknown date", 1, dated.getIndex());
        Assert.assertEquals(2, undated.getIndex());
    }
}
