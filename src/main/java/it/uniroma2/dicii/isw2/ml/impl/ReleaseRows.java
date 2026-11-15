package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.dataset.impl.CsvDatasetWriter;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import weka.core.Attribute;
import weka.core.Instances;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the releases a dataset holds off the column naming the release of each row, and collects the
 * rows of some of them.
 * <p>
 * Both the trimming and the splitting of the dataset cut the history at a release, and both order the
 * releases as the file holds them — {@code Workflow} measures the versions in the order {@code
 * Version.getIndex()} gives them, which is by release date, and appends the rows of each as it goes.
 * That is an assumption about a file written elsewhere, so it is checked rather than trusted: a release
 * whose rows are not contiguous means the file was not written release by release, and a cut made on
 * it would fall at a point that is not a point in time.
 */
final class ReleaseRows {

    /**
     * Where the column naming the release a row belongs to sits, as {@link CsvDatasetWriter} writes it.
     */
    private static final int VERSION_COLUMN = 0;

    private ReleaseRows() {
        // Reads rows, and holds none
    }

    /**
     * Reads which releases the dataset holds, and checks that it holds each of them in one piece.
     *
     * @param data the whole dataset, with its identity columns
     * @param file the dataset the rows came from, named in the report of a failure
     * @return the releases, in the order the file introduces them
     * @throws MlException if the dataset does not name a release per row, or holds one in two pieces
     */
    static List<String> releasesInOrder(Instances data, Path file) throws MlException {
        Attribute version = data.attribute(VERSION_COLUMN);
        if (version == null || !CsvDatasetWriter.IDENTITY_COLUMNS.getFirst().equals(version.name())) {
            throw new MlException("The dataset '" + file + "' does not open with the column naming the "
                    + "release of a row, which is what the history is cut on");
        }
        List<String> releases = new ArrayList<>();
        String current = null;
        for (int i = 0; i < data.numInstances(); i++) {
            String release = data.instance(i).stringValue(VERSION_COLUMN);
            if (release.equals(current)) {
                continue;
            }
            if (releases.contains(release)) {
                throw new MlException("Release '" + release + "' holds rows in two places in the "
                        + "dataset '" + file + "', which is written release by release. Its releases "
                        + "can no longer be ordered by where its rows are, so the history cannot be cut "
                        + "at a point in time");
            }
            releases.add(release);
            current = release;
        }
        return releases;
    }

    /**
     * @param data     the whole dataset, with the columns naming each row still on it
     * @param releases the releases whose rows to keep
     * @return those rows, in the order the file holds them, under the header of the whole
     */
    static Instances of(Instances data, List<String> releases) {
        Set<String> kept = new HashSet<>(releases);
        Instances rows = new Instances(data, 0);
        for (int i = 0; i < data.numInstances(); i++) {
            if (kept.contains(data.instance(i).stringValue(VERSION_COLUMN))) {
                rows.add(data.instance(i));
            }
        }
        return rows;
    }

}
