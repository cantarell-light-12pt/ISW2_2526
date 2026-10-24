package it.uniroma2.dicii.isw2.association.impl;

import it.uniroma2.dicii.isw2.repo.model.Tag;
import it.uniroma2.dicii.isw2.versions.model.Version;
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VersionTagAssociatorImplTest {

    private static final String PREFIX = "release-";

    private static Version version(String name) {
        return new Version("id-" + name, name, true, false);
    }

    private static Tag tag(String name, ZonedDateTime date) {
        return new Tag(PREFIX + name, "commit-" + name, date);
    }

    private static ZonedDateTime at(int year, int month, int day, int hour, int minute, ZoneOffset offset) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, offset);
    }

    private static void associate(List<Tag> tags, List<Version> versions) {
        new VersionTagAssociatorImpl(PREFIX).associateTagsToVersions(tags, versions);
    }

    @Test
    public void associatingATagRecordsTheCommitItPointsAt() {
        Version version = version("3.6.0");
        List<Version> versions = new ArrayList<>(List.of(version));

        associate(List.of(tag("3.6.0", at(2020, 2, 25, 14, 17, ZoneOffset.UTC))), versions);

        Assert.assertEquals("commit-3.6.0", version.getCommitId());
    }

    /**
     * A tag is dated by the commit it points at, which is not always the one the release was built
     * from: ZooKeeper's releases 3.0.0 to 3.3.2 are tagged on the commits that moved them out of
     * Subversion, all dated 2010-11-24T21:19:37, whereas Jira dates each of them on the day it was
     * published.
     */
    @Test
    public void theDateReportedByJiraIsKept() {
        Version version = version("3.2.0");
        version.setReleaseDate(LocalDateTime.of(2009, 7, 8, 0, 0));
        List<Version> versions = new ArrayList<>(List.of(version));

        associate(List.of(tag("3.2.0", at(2010, 11, 24, 21, 19, ZoneOffset.UTC))), versions);

        Assert.assertEquals("The tag must not overwrite the release date Jira reported",
                LocalDateTime.of(2009, 7, 8, 0, 0), version.getReleaseDate());
    }

    /**
     * The tags of the releases moved out of Subversion all share one date, so ordering them by it
     * would leave the name to decide; ordering them by the dates Jira reports puts 3.1.2, published
     * in December 2009, after 3.2.1, published three months earlier.
     */
    @Test
    public void releasesSharingTheDateOfTheirTagsAreOrderedByTheDatesJiraReports() {
        Version maintenance = version("3.1.2");
        maintenance.setReleaseDate(LocalDateTime.of(2009, 12, 14, 0, 0));
        Version minor = version("3.2.1");
        minor.setReleaseDate(LocalDateTime.of(2009, 9, 4, 0, 0));
        List<Version> versions = new ArrayList<>(Arrays.asList(maintenance, minor));

        associate(Arrays.asList(tag("3.1.2", at(2010, 11, 24, 21, 19, ZoneOffset.UTC)),
                tag("3.2.1", at(2010, 11, 24, 21, 19, ZoneOffset.UTC))), versions);
        Version.numberVersions(versions);

        Assert.assertEquals(1, minor.getIndex());
        Assert.assertEquals(2, maintenance.getIndex());
    }

    /**
     * ZooKeeper's 3.6.0 is released and tagged, and yet Jira reports no date for it: left undated, it
     * would sort after every other release.
     */
    @Test
    public void theDateOfTheTagFillsInTheOneJiraDidNotReport() {
        Version version = version("3.6.0");
        List<Version> versions = new ArrayList<>(List.of(version));

        associate(List.of(tag("3.6.0", at(2020, 2, 25, 14, 17, ZoneOffset.UTC))), versions);

        Assert.assertEquals(LocalDateTime.of(2020, 2, 25, 14, 17), version.getReleaseDate());
    }

    /**
     * The committers of a project of this size are spread across the world, and ZooKeeper's release tags
     * carry offsets from -07:00 to +05:30. Reading the local time of the tag a version Jira left undated
     * falls back on would order it by the wall clock of whoever cut it.
     */
    @Test
    public void theDateOfTheTagIsNormalisedToUtc() {
        Version version = version("3.5.10");
        List<Version> versions = new ArrayList<>(List.of(version));

        associate(List.of(tag("3.5.10", at(2022, 5, 29, 13, 6, ZoneOffset.ofHours(2)))), versions);

        Assert.assertEquals("13:06+02:00 is 11:06 UTC",
                LocalDateTime.of(2022, 5, 29, 11, 6), version.getReleaseDate());
    }

    @Test
    public void twoReleasesOfDifferentZonesAreOrderedByTheMomentTheyHappened() {
        Version east = version("1.0.0");
        Version west = version("2.0.0");
        List<Version> versions = new ArrayList<>(Arrays.asList(east, west));

        // 09:00+05:30 is 03:30 UTC, so it happened before 07:00-07:00, i.e. 14:00 UTC
        associate(Arrays.asList(tag("1.0.0", at(2021, 6, 1, 9, 0, ZoneOffset.ofHoursMinutes(5, 30))),
                tag("2.0.0", at(2021, 6, 1, 7, 0, ZoneOffset.ofHours(-7)))), versions);
        Version.numberVersions(versions);

        Assert.assertEquals(1, east.getIndex());
        Assert.assertEquals(2, west.getIndex());
    }

    /**
     * A release candidate is tagged {@code release-3.8.0-1}, which is not the release itself. Matching
     * on the exact name keeps those out, at the cost of dropping a release the project published and
     * never tagged plainly — as ZooKeeper did with 3.8.0.
     */
    @Test
    public void aVersionHavingNoExactlyMatchingTagIsRemoved() {
        Version tagged = version("3.8.1");
        Version untagged = version("3.8.0");
        List<Version> versions = new ArrayList<>(Arrays.asList(tagged, untagged));

        associate(Arrays.asList(tag("3.8.1", at(2023, 1, 25, 17, 29, ZoneOffset.UTC)),
                tag("3.8.0-1", at(2022, 2, 25, 8, 38, ZoneOffset.UTC))), versions);

        Assert.assertEquals(List.of(tagged), versions);
    }

    @Test
    public void aVersionMatchingSeveralTagsIsLeftAloneAndRemoved() {
        Version version = version("3.6.0");
        List<Version> versions = new ArrayList<>(List.of(version));

        associate(Arrays.asList(tag("3.6.0", at(2020, 2, 25, 14, 17, ZoneOffset.UTC)),
                tag("3.6.0", at(2020, 3, 3, 21, 19, ZoneOffset.UTC))), versions);

        Assert.assertTrue("An ambiguous version must not be dated at random", versions.isEmpty());
        Assert.assertNull(version.getReleaseDate());
    }
}
