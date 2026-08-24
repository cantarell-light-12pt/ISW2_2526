package it.uniroma2.dicii.isw2.buggyness.impl;

import it.uniroma2.dicii.isw2.metrics.ClassNameResolver;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The fully qualified names of the classes a repository held at some point of its history, read out
 * of the objects it stores rather than out of a working tree.
 * <p>
 * The labelling needs them because it has to name a class the way the dataset names it, and the
 * dataset names a class by the package it declares followed by the file it lives in — see
 * {@link ClassNameResolver}. Nothing about the path says what that package is: {@code src/java/main}
 * and {@code zookeeper-server/src/main/java} are both source roots of the same project, so the
 * declaration has to be read from the source itself.
 * <p>
 * Names are cached by path. A Java source declares one package over its whole life, since moving a
 * class to another package moves its file with it, so the blob behind a path is read once however
 * many fixes touched it. The one case the cache is wrong about is a commit correcting a package
 * declaration without moving the file, which no project does on purpose.
 */
@Slf4j
class DeclaredClassNames {

    private static final String PACKAGE_KEYWORD = "package";

    private static final String BLOCK_COMMENT_START = "/*";

    private static final String BLOCK_COMMENT_END = "*/";

    private static final String LINE_COMMENT = "//";

    /**
     * The byte order mark a source file may begin with, which would otherwise be read as the first
     * character of the package it declares.
     */
    private static final char BYTE_ORDER_MARK = '\uFEFF';

    /**
     * How many lines of a source file are read while looking for its package declaration. The Java
     * Language Specification puts the declaration before anything but comments and annotations, so a
     * file that has not declared one within its first lines declares none; the bound is what keeps a
     * pathological blob — a single-line generated file of a few megabytes, say — from being scanned
     * in full.
     */
    private static final int MAX_LINES = 500;

    private final Repository repository;

    private final Map<String, String> byPath = new HashMap<>();

    DeclaredClassNames(Repository repository) {
        this.repository = repository;
    }

    /**
     * Names the class a source file declares.
     *
     * @param path the path of the source file, relative to the root of the repository
     * @param blob the identifier of the content the file held, or {@code null} if the change that
     *             produced it did not report one
     * @return the fully qualified name of the class, i.e. the package the file declares followed by
     * the name of the file itself
     */
    String of(String path, ObjectId blob) {
        return byPath.computeIfAbsent(path,
                key -> ClassNameResolver.qualifiedName(packageOf(key, blob), key));
    }

    /**
     * @return how many distinct sources have been read
     */
    int size() {
        return byPath.size();
    }

    /**
     * Reads the package a source file declares out of the repository, without checking anything out.
     * <p>
     * A file whose package cannot be read is left in the default package, and is therefore named after
     * itself alone. That name matches no row of a project that puts its classes in packages, so the
     * fix goes unlabelled rather than labelling the wrong class.
     *
     * @param path the path of the source file, used for reporting
     * @param blob the identifier of the content it held
     * @return the package it declares, empty if it declares none or cannot be read
     */
    private String packageOf(String path, ObjectId blob) {
        if (blob == null || ObjectId.zeroId().equals(blob)) {
            log.debug("The change to {} reports no content of its own: leaving the class it declares "
                    + "in the default package", path);
            return "";
        }
        try (InputStream source = repository.open(blob, Constants.OBJ_BLOB).openStream()) {
            return declaredPackage(source);
        } catch (IOException e) {
            log.warn("Unable to read {} out of the repository: {}. The class it declares will be named "
                    + "after its file alone", path, e.getMessage());
            return "";
        }
    }

    /**
     * Scans a source file down to its package declaration.
     * <p>
     * The scan strips the comments as it goes rather than matching the declaration with a regular
     * expression, because a licence header is a comment and the word {@code package} is an ordinary
     * English word: ZooKeeper's own headers talk about "this package", and a pattern anchored at the
     * beginning of a line would happily read a sentence as a declaration. Stripping is exact here, and
     * cheap, because the specification lets nothing but comments and annotations precede the
     * declaration — no string literal can be reached before it.
     * <p>
     * The reader decodes as UTF-8 and replaces whatever it cannot decode, rather than failing: a
     * history spanning twenty years holds sources whose comments are not valid UTF-8, and a package
     * declaration is pure ASCII in every one of them.
     *
     * @param source the content of the source file
     * @return the package it declares, empty if it declares none
     * @throws IOException if the content cannot be read
     */
    private static String declaredPackage(InputStream source) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8))) {
            boolean commented = false;
            int read = 0;
            String line;
            for (line = reader.readLine(); line != null && read < MAX_LINES; line = reader.readLine(), read++) {
                Code code = strip(read == 0 ? withoutByteOrderMark(line) : line, commented);
                commented = code.commented();
                String statement = code.text().trim();
                if (statement.isEmpty() || statement.charAt(0) == '@') {
                    continue;
                }
                // The first thing that is neither a comment nor an annotation either is the package
                // declaration or means the file declares no package
                return declaresPackage(statement) ? nameIn(statement) : "";
            }
        }
        return "";
    }

    /**
     * Removes from a line the comments it holds, and reports whether it left a block comment open for
     * the line that follows.
     *
     * @param line      the line to strip
     * @param commented whether the line begins inside a block comment opened by an earlier one
     * @return the code the line holds, and the state the next line begins in
     */
    private static Code strip(String line, boolean commented) {
        StringBuilder code = new StringBuilder();
        boolean inComment = commented;
        int cursor = 0;
        while (cursor < line.length()) {
            if (inComment) {
                int end = line.indexOf(BLOCK_COMMENT_END, cursor);
                inComment = end < 0;
                cursor = inComment ? line.length() : end + BLOCK_COMMENT_END.length();
            } else if (line.startsWith(BLOCK_COMMENT_START, cursor)) {
                inComment = true;
                cursor += BLOCK_COMMENT_START.length();
            } else if (line.startsWith(LINE_COMMENT, cursor)) {
                cursor = line.length();
            } else {
                code.append(line.charAt(cursor));
                cursor++;
            }
        }
        return new Code(code.toString(), inComment);
    }

    /**
     * @param statement the first statement of a source file, stripped of its comments
     * @return whether it is a package declaration, as opposed to a type whose name merely begins with
     * the same letters
     */
    private static boolean declaresPackage(String statement) {
        return statement.startsWith(PACKAGE_KEYWORD)
                && statement.length() > PACKAGE_KEYWORD.length()
                && Character.isWhitespace(statement.charAt(PACKAGE_KEYWORD.length()));
    }

    /**
     * @param statement a package declaration
     * @return the package it names, empty if the declaration does not end on the line it began on,
     * which no source file written by a human does
     */
    private static String nameIn(String statement) {
        int end = statement.indexOf(';');
        return end < 0 ? "" : statement.substring(PACKAGE_KEYWORD.length(), end).trim();
    }

    /**
     * @param line the first line of a source file
     * @return it, without the byte order mark it may begin with
     */
    private static String withoutByteOrderMark(String line) {
        return !line.isEmpty() && line.charAt(0) == BYTE_ORDER_MARK ? line.substring(1) : line;
    }

    /**
     * What a single line of a source file holds once its comments have been removed.
     *
     * @param text      the code it holds, possibly empty
     * @param commented whether it left a block comment open for the line that follows
     */
    private record Code(String text, boolean commented) {
    }
}
