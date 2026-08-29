package it.uniroma2.dicii.isw2.dataset.impl;

import it.uniroma2.dicii.isw2.dataset.DatasetWriter;
import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.versions.model.Version;
import lombok.extern.slf4j.Slf4j;

/**
 * A writer forwarding to another one the earliest releases of the project alone, and dropping the
 * ones past a cutoff. It is what trims the dataset to the first part of the release history, the
 * whole of it being written by the writer this one sits beside in the composite.
 * <p>
 * Which releases are the earliest is read off {@link Version#getIndex()}, the 1-based ordinal
 * {@link Version#numberVersions(java.util.List)} assigns by release date, and not off the order the
 * versions happen to be handed over in. The two coincide today, since the extraction walks the list
 * the numbering sorted, but only the index says what "the first releases" means: a project
 * maintaining several lines at once publishes 3.5.10 after 3.6.0, so an ordering by name would put
 * the wrong releases in the trimmed dataset and nothing in the file would say so.
 * <p>
 * A version carrying no index is forwarded rather than dropped. An unnumbered version is a version
 * nothing can place, and the trimmed dataset holding a release it should not is a visible mistake,
 * whereas a release silently missing from it is not.
 */
@Slf4j
public class FirstReleasesDatasetWriter implements DatasetWriter {

    /**
     * The ordinal a version that has not been numbered yet carries.
     */
    private static final int UNNUMBERED = 0;

    private final DatasetWriter delegate;
    private final int releases;

    /**
     * @param delegate where the releases within the cutoff are written
     * @param releases how many of the earliest releases to keep
     */
    public FirstReleasesDatasetWriter(DatasetWriter delegate, int releases) {
        this.delegate = delegate;
        this.releases = releases;
        log.info("The trimmed dataset holds the {} earliest releases of the project", releases);
    }

    /**
     * How many releases a fraction of the release history comes to, rounded to the nearest whole
     * release: a release is measured or it is not, and 33% of the 61 releases ZooKeeper had tagged at
     * the last run is 20.13 of them.
     * <p>
     * The count is never less than one release — a trimmed dataset with nothing in it is no dataset,
     * and rounding a small enough history down would leave one — and never more than the whole
     * history, a fraction asking for more releases than the project has being simply all of them.
     *
     * @param releases how many releases the project has
     * @param fraction which part of them to keep, between 0 and 1
     * @return how many of the earliest ones that comes to
     */
    public static int fractionOf(int releases, double fraction) {
        long kept = Math.round(releases * fraction);
        return (int) Math.max(1, Math.min(releases, kept));
    }

    @Override
    public void write(Version version, MetricsReport report) throws DatasetException {
        if (version != null && version.getIndex() != UNNUMBERED && version.getIndex() > releases) {
            log.debug("Version {} is the {}th release, past the {} the trimmed dataset holds: leaving "
                    + "it out of it", version.getName(), version.getIndex(), releases);
            return;
        }
        delegate.write(version, report);
    }

    @Override
    public void close() throws DatasetException {
        delegate.close();
    }
}
