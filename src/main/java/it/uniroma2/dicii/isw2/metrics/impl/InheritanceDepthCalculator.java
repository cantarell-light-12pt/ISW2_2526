package it.uniroma2.dicii.isw2.metrics.impl;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Measures the depth of the inheritance tree of the classes of one snapshot, i.e. how many classes
 * stand between each of them and the root every Java hierarchy is rooted at.
 * <p>
 * The measure counts the classes of the chain from the one being measured up to, but not including,
 * {@link Object}: a class extending nothing is worth 1, one extending a class of the snapshot that
 * extends nothing is worth 2, and so on. Only single inheritance counts, as the metric is defined:
 * the interfaces a class implements are not part of its depth.
 * <p>
 * This is measured here rather than read off CK, which reports it wrongly for any class declaring
 * nested types: {@code CKVisitor} runs the metrics of the <em>enclosing</em> class over every nested
 * type declaration it walks into, and the depth CK accumulates is never reset between those calls, so
 * a class ends up credited with the depth of each of the types nested in it as well. It is what made
 * {@code KeeperException}, an exception three classes deep declaring dozens of nested subclasses of
 * itself, come out 96 deep.
 * <p>
 * A calculator is about one snapshot and cannot be reused across releases: the chains it resolves are
 * the ones the sources of that release declare.
 */
@Slf4j
class InheritanceDepthCalculator {

    /**
     * The depth of a class whose superclass is nowhere to be found: it is not {@link Object}, since it
     * was named, so the class is at least one level below one level below the root.
     */
    private static final int UNRESOLVED_DEPTH = 2;

    /**
     * The package a source file gets its types from without importing them.
     */
    private static final String IMPLICIT_IMPORT = "java.lang.";

    /**
     * How an import of a whole package ends.
     */
    private static final String ON_DEMAND = ".*";

    /**
     * The packages a superclass may be looked for in through reflection. The platform class loader
     * reaches the classes of the Java runtime and no other, so a name outside these packages could
     * only ever resolve to a class of <em>this</em> project that happens to be named alike.
     */
    private static final List<String> RUNTIME_PACKAGES = List.of("java.", "javax.");

    /**
     * The fully qualified name of every class of the snapshot, whether it extends anything or not:
     * knowing that a class is one of the snapshot is what tells a chain to keep going through it.
     */
    private final Set<String> declared = new HashSet<>();

    /**
     * The superclass of each class of the snapshot that has one, as its source file names it.
     */
    private final Map<String, Supertype> supertypes = new HashMap<>();

    /**
     * The depth of the classes already measured, so that a hierarchy shared by fifty classes is walked
     * once rather than fifty times.
     */
    private final Map<String, Integer> depths = new HashMap<>();

    /**
     * Records what a source file of the snapshot declares. Every file has to be declared before any
     * class is measured: a chain can only be followed through the classes the calculator knows about.
     *
     * @param unit the syntax tree of the source file
     */
    void declare(CompilationUnit unit) {
        // Only the types declared at the top level of the file: the dataset holds no row of a nested
        // class, and how deep one sits says nothing about how deep the class declaring it sits
        for (TypeDeclaration<?> type : unit.getTypes()) {
            String qualifiedName = qualifiedName(unit, type);
            declared.add(qualifiedName);
            Supertype supertype = supertypeOf(unit, type);
            if (supertype != null) {
                supertypes.put(qualifiedName, supertype);
            }
        }
    }

    /**
     * Measures the depth of the inheritance tree of a class of the snapshot.
     *
     * @param qualifiedName the fully qualified name of the class
     * @return the number of classes from it up to the root of its hierarchy, the root excluded
     */
    int depthOf(String qualifiedName) {
        return depthOf(qualifiedName, new HashSet<>());
    }

    /**
     * Measures the depth of a class, guarding against a hierarchy that closes on itself: a source tree
     * being mined is whatever was committed, which does not have to compile, and following a cycle
     * would not terminate.
     *
     * @param qualifiedName the fully qualified name of the class
     * @param walking       the classes whose depth is already being measured further down the stack
     * @return the depth of the class
     */
    private int depthOf(String qualifiedName, Set<String> walking) {
        Integer known = depths.get(qualifiedName);
        if (known != null) {
            return known;
        }
        if (!walking.add(qualifiedName)) {
            log.warn("The hierarchy of {} extends itself: measuring it as if its superclass were unknown",
                    qualifiedName);
            return UNRESOLVED_DEPTH;
        }
        int depth = measure(qualifiedName, walking);
        walking.remove(qualifiedName);
        depths.put(qualifiedName, depth);
        return depth;
    }

    /**
     * Measures the depth of a class the memory holds no measure of yet.
     *
     * @param qualifiedName the fully qualified name of the class
     * @param walking       the classes whose depth is already being measured further down the stack
     * @return the depth of the class
     */
    private int measure(String qualifiedName, Set<String> walking) {
        Supertype supertype = supertypes.get(qualifiedName);
        if (supertype == null) {
            return 1;
        }
        // The names the superclass could stand for, most specific first, worked out once: the chain
        // carries on through the first of them the snapshot declares, and failing that through the
        // first the Java runtime holds
        List<String> candidates = candidates(supertype);
        return candidates.stream()
                .filter(declared::contains)
                .findFirst()
                .map(resolved -> 1 + depthOf(resolved, walking))
                .or(() -> runtimeDepth(candidates).map(depth -> 1 + depth))
                .orElse(UNRESOLVED_DEPTH);
    }

    /**
     * Lists the fully qualified names a superclass could stand for, in the order the language resolves
     * them: a name already qualified stands for itself; then come the types imported one at a time,
     * then the package the source file itself declares, then the packages imported whole, and last the
     * one every source file reads without importing it.
     * <p>
     * The name is qualified as a whole rather than reduced to its last segment, so that
     * {@code extends Outer.Inner} under {@code import a.b.Outer} is looked for as {@code a.b.Outer.Inner}
     * rather than as an unrelated {@code a.b.Inner}.
     *
     * @param supertype the superclass to resolve, as its source file names it
     * @return the names it could stand for
     */
    private static List<String> candidates(Supertype supertype) {
        String name = supertype.name();
        int scope = name.indexOf('.');
        List<String> candidates = new ArrayList<>();
        if (scope >= 0) {
            candidates.add(name);
        }
        String head = scope < 0 ? name : name.substring(0, scope);
        String nested = scope < 0 ? "" : name.substring(scope);
        supertype.imports().stream()
                .filter(imported -> imported.endsWith('.' + head))
                .map(imported -> imported + nested)
                .forEach(candidates::add);
        if (!supertype.packageName().isEmpty()) {
            candidates.add(supertype.packageName() + '.' + name);
        }
        supertype.imports().stream()
                .filter(imported -> imported.endsWith(ON_DEMAND))
                .map(imported -> imported.substring(0, imported.length() - 1) + name)
                .forEach(candidates::add);
        // A source file of the default package names the classes of that package by their bare name
        candidates.add(name);
        candidates.add(IMPLICIT_IMPORT + name);
        return candidates;
    }

    /**
     * Measures how deep a superclass the snapshot does not declare sits, by loading it from the Java
     * runtime. It is what tells an exception apart from a plain class: {@code extends IOException} is
     * three classes below the root, {@code extends Thread} one.
     * <p>
     * The class is loaded through the platform class loader, which reaches the runtime and nothing
     * else, and without being initialised, so that no code of the project being mined can run.
     *
     * @param candidates the names the superclass could stand for
     * @return how many classes stand between it and the root, the root excluded, or an empty optional
     * if it is not a class of the Java runtime
     */
    private static Optional<Integer> runtimeDepth(List<String> candidates) {
        return candidates.stream()
                .filter(candidate -> RUNTIME_PACKAGES.stream().anyMatch(candidate::startsWith))
                .map(InheritanceDepthCalculator::loadFromRuntime)
                .flatMap(Optional::stream)
                .findFirst()
                .map(InheritanceDepthCalculator::chainLength);
    }

    /**
     * @param qualifiedName the fully qualified name of a class of the Java runtime
     * @return that class, or an empty optional if the runtime holds no such class
     */
    private static Optional<Class<?>> loadFromRuntime(String qualifiedName) {
        try {
            return Optional.of(Class.forName(qualifiedName, false, ClassLoader.getPlatformClassLoader()));
        } catch (ClassNotFoundException | LinkageError e) {
            return Optional.empty();
        }
    }

    /**
     * @param type a class of the Java runtime
     * @return how many classes there are from it up to the root of its hierarchy, the root excluded
     */
    private static int chainLength(Class<?> type) {
        int length = 0;
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            length++;
        }
        return length;
    }

    /**
     * Reads the superclass of a type, which only a class can have: an interface, an enumeration, a
     * record or an annotation is one level below the root and nothing more, whatever it extends or
     * implements.
     *
     * @param unit the syntax tree of the source file declaring the type
     * @param type the type to read
     * @return its superclass, or {@code null} if it has none the metric counts
     */
    private static Supertype supertypeOf(CompilationUnit unit, TypeDeclaration<?> type) {
        if (!(type instanceof ClassOrInterfaceDeclaration declaration) || declaration.isInterface()) {
            return null;
        }
        return declaration.getExtendedTypes().stream()
                .findFirst()
                .map(ClassOrInterfaceType::getNameWithScope)
                .map(name -> new Supertype(name, packageOf(unit), importsOf(unit)))
                .orElse(null);
    }

    /**
     * @param unit the syntax tree of a source file
     * @param type one of the types it declares at its top level
     * @return the fully qualified name of that type
     */
    private static String qualifiedName(CompilationUnit unit, TypeDeclaration<?> type) {
        String packageName = packageOf(unit);
        return packageName.isEmpty() ? type.getNameAsString() : packageName + '.' + type.getNameAsString();
    }

    /**
     * @param unit the syntax tree of a source file
     * @return the package it declares, empty if it declares none
     */
    private static String packageOf(CompilationUnit unit) {
        return unit.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");
    }

    /**
     * @param unit the syntax tree of a source file
     * @return what it imports, the static imports left out since no type is ever named by one
     */
    private static List<String> importsOf(CompilationUnit unit) {
        return unit.getImports().stream()
                .filter(imported -> !imported.isStatic())
                .map(imported -> imported.isAsterisk()
                        ? imported.getNameAsString() + ON_DEMAND
                        : imported.getNameAsString())
                .toList();
    }

    /**
     * The superclass of a class, as the source file declaring it names it, along with what that file
     * has to say about which type the name stands for.
     *
     * @param name        the name of the superclass, exactly as it is written after {@code extends}
     * @param packageName the package of the source file, empty if it declares none
     * @param imports     what the source file imports
     */
    private record Supertype(String name, String packageName, List<String> imports) {
    }
}
