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
     * Jira records the day somebody marked a version released, which is not the moment the release was
     * cut; the tag points at the very commit it was built from. Mixing the two sources would leave the
     * history half dated by Jira and half by Git, ordering neither half against the other.
     */
    @Test
    public void theDateOfTheTagReplacesTheOneReportedByJira() {
        Version version = version("3.6.0");
        version.setReleaseDate(LocalDateTime.of(2020, 3, 4, 0, 0));
        List<Version> versions = new ArrayList<>(List.of(version));

        associate(List.of(tag("3.6.0", at(2020, 2, 25, 14, 17, ZoneOffset.UTC))), versions);

        Assert.assertEquals("The tag must overwrite the release date Jira reported",
                LocalDateTime.of(2020, 2, 25, 14, 17), version.getReleaseDate());
    }

    @Test
    public void theDateOfTheTagFillsInTheOneJiraDidNotReport() {
        Version version = version("3.6.0");
        List<Version> versions = new ArrayList<>(List.of(version));

        associate(List.of(tag("3.6.0", at(2020, 2, 25, 14, 17, ZoneOffset.UTC))), versions);

        Assert.assertEquals(LocalDateTime.of(2020, 2, 25, 14, 17), version.getReleaseDate());
    }

    /**
     * The committers of a project of this size are spread across the world, and ZooKeeper's release tags
     * carry offsets from -07:00 to +05:30. Reading the local time of a tag would order the releases by
     * the wall clock of whoever cut them.
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
