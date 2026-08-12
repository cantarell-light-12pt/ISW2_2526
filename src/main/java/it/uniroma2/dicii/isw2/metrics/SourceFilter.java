package it.uniroma2.dicii.isw2.metrics;

/**
 * The rule telling the sources that are rows of the dataset from the ones that are not.
 * <p>
 * A snapshot of the project holds far more Java than the code the dataset is about: the tests
 * exercising the classes, the sources generated at build time, the output of a previous build left in
 * the working tree. None of them is functional code, so none of them belongs in a dataset predicting
 * where the defects of the project are, and letting them in would distort the classes that do belong
 * in it — a class extended only by its tests would be credited with children, and one called only by
 * them with couplings.
 * <p>
 * The decision is taken on the path of a source file alone, before either extractor parses anything,
 * so that the whole composite can be driven by the same set of files.
 */
public interface SourceFilter {

    /**
     * Decides whether a source file is functional code, and therefore a row of the dataset.
     *
     * @param relativePath the path of the source file, relative to the root of the repository and
     *                     always separated by {@code /}, as {@link ClassNameResolver#relativePath}
     *                     returns it
     * @return whether the class the file declares belongs in the dataset
     */
    boolean accepts(String relativePath);

    /**
     * The filter leaving every source in, which is what an extractor built without one falls back to:
     * pointed at a directory holding nothing but the sources to measure, an extractor has nothing to
     * exclude.
     *
     * @return a filter accepting every source file
     */
    static SourceFilter everything() {
        return relativePath -> true;
    }
}
