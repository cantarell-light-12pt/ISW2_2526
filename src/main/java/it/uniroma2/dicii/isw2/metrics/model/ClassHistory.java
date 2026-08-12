package it.uniroma2.dicii.isw2.metrics.model;

/**
 * What the Git history says about a single class as of a single released version, i.e. the raw
 * counts the evolution metrics of one row of the dataset are read off.
 * <p>
 * The measures come in two flavours, as the catalogue of the README defines them. Some of them are
 * about the release being measured alone — how much the class changed since the previous one — while
 * the others are about the whole life of the class up to that release. Both are held here, so that
 * the extractor is left with nothing to compute but the division deriving the weighted count.
 *
 * @param revisions      the number of commits the class has been subjected to up to this release
 * @param authors        the number of distinct authors that have contributed to it up to this release
 * @param churn          the lines added to and removed from it since the previous release
 * @param maxChurn       the highest churn it has taken in a single release so far
 * @param sizeChange     the lines added to it minus the ones removed from it, up to this release
 * @param age            the number of releases it has been part of, counting this one
 * @param latestBugFixes the number of commits that fixed a bug in it since the previous release
 * @param totalBugFixes  the number of commits that fixed a bug in it up to this release
 */
public record ClassHistory(int revisions, int authors, long churn, long maxChurn, long sizeChange,
                           int age, int latestBugFixes, int totalBugFixes) {

    /**
     * The history of a class the release being measured is the first one to hold, and that no commit
     * reachable from it ever touched. It is what a class added by a commit the walk does not attribute
     * to any release falls back to, so that the row is still part of the dataset.
     */
    public static final ClassHistory UNTOUCHED = new ClassHistory(0, 0, 0, 0, 0, 1, 0, 0);

    /**
     * Weighs the bug fixes the class has taken against how long it has been in the project, so that a
     * class fixed five times over twenty releases is not read as one fixed five times over two.
     *
     * @return the bug-fixing commits of the class divided by its age, 0 if it has no age yet
     */
    public double weightedBugFixes() {
        return age == 0 ? 0 : (double) totalBugFixes / age;
    }
}
