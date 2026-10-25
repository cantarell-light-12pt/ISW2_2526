package it.uniroma2.dicii.isw2.metrics.model;

/**
 * What the Git history says about a single class as of a single released version, i.e. the raw
 * counts the evolution metrics of one row of the dataset are read off.
 * <p>
 * The measures come in two flavours, as the catalogue of the README defines them. Some of them are
 * about the release being measured alone — how much the class changed since the previous one — while
 * the others are about the whole life of the class up to that release. Both are held here, so that
 * the extractor is left with nothing to compute but the division deriving the weighted count.
 * <p>
 * The counts of commits — the revisions, the authors, the change in size and the bug fixes over the
 * whole life of the class — are read off the history the release was built from, i.e. the commits its
 * tag can reach, and nothing else: a commit either is part of the code a release shipped or it is
 * not. The measures defined in terms of releases — the churn and the bug fixes since the previous
 * one, the highest churn of a single release and the age — are read off the releases in the order
 * they were published, the one order the release history is numbered in.
 *
 * @param revisions      the number of commits the class has been subjected to in the history of
 *                       this release
 * @param authors        the number of distinct authors that have contributed to it in the history of
 *                       this release
 * @param churn          the lines added to and removed from it since the previous release
 * @param maxChurn       the highest churn it has taken in a single release so far
 * @param sizeChange     the lines added to it minus the ones removed from it, in the history of this
 *                       release
 * @param age            the number of releases it has been part of, counting this one
 * @param latestBugFixes the number of commits that fixed a bug in it since the previous release
 * @param totalBugFixes  the number of commits that fixed a bug in it in the history of this release
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
     * Replaces the counts of commits of this history with the ones read off the history a release was
     * built from, keeping the measures defined in terms of releases as they are.
     *
     * @param lineage the history of the class as the commits the release can reach tell it
     * @return this history, counting the commits of {@code lineage} instead of its own
     */
    public ClassHistory withCommitsOf(ClassHistory lineage) {
        return new ClassHistory(lineage.revisions(), lineage.authors(), churn, maxChurn, lineage.sizeChange(),
                age, latestBugFixes, lineage.totalBugFixes());
    }

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
