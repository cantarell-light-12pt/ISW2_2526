package it.uniroma2.dicii.isw2.metrics;

import java.io.File;
import java.nio.file.Path;

/**
 * The single rule every {@link MetricsExtractor} names a class by.
 * <p>
 * A row of the dataset is one source file, but each extractor learns about it through a different
 * tool, and each tool has its own idea of what the class is called: CK reports the name of the type
 * it happened to parse, JavaParser the types the file declares. Were they left to name the class
 * themselves, two leaves measuring the same file could hand two different names to
 * {@link it.uniroma2.dicii.isw2.metrics.model.MetricsReport#forClass(String, String)}, which keeps
 * the first one it is given.
 * <p>
 * The name is therefore always built the same way — the package the file declares, followed by the
 * name of the file itself, i.e. the type the file is named after — so that the fully qualified name
 * of a row does not depend on which extractor produced it.
 */
public final class ClassNameResolver {

    private static final String JAVA_EXTENSION = ".java";

    private static final char PACKAGE_SEPARATOR = '.';

    private ClassNameResolver() {
        // Utility class
    }

    /**
     * Expresses the path of a source file relative to the root of the repository, so that the measures
     * can be joined with the history of the same file, which Git reports relative to that same root
     * and always separated by {@code /}.
     *
     * @param root the root directory the sources are measured under, absolute and normalised
     * @param file the path of the source file
     * @return the path of the file relative to the root
     */
    public static String relativePath(Path root, Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        Path relative = absolute.startsWith(root) ? root.relativize(absolute) : absolute;
        return relative.toString().replace(File.separatorChar, '/');
    }

    /**
     * Builds the fully qualified name a class is known by in the dataset.
     *
     * @param packageName  the package the source file declares, empty for the default package
     * @param relativePath the path of the source file, relative to the root of the repository
     * @return the package followed by the name of the file, without its {@code .java} extension
     */
    public static String qualifiedName(String packageName, String relativePath) {
        String simpleName = fileName(relativePath);
        return packageName.isEmpty() ? simpleName : packageName + PACKAGE_SEPARATOR + simpleName;
    }

    /**
     * @param qualifiedName the fully qualified name of a class
     * @return the package it belongs to, empty if it belongs to the default one
     */
    public static String packageOf(String qualifiedName) {
        int separator = qualifiedName.lastIndexOf(PACKAGE_SEPARATOR);
        return separator < 0 ? "" : qualifiedName.substring(0, separator);
    }

    /**
     * @param qualifiedName the fully qualified name of a class
     * @return its name, without the package nor the types it is declared in
     */
    public static String simpleName(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf(PACKAGE_SEPARATOR) + 1);
    }

    /**
     * @param path the path of a source file
     * @return the name of the file, without its directories nor the {@code .java} extension
     */
    public static String fileName(String path) {
        String name = Path.of(path).getFileName().toString();
        return name.endsWith(JAVA_EXTENSION) ? name.substring(0, name.length() - JAVA_EXTENSION.length()) : name;
    }
}
