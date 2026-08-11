package it.uniroma2.dicii.isw2.metrics.impl;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Scores methods whose cognitive complexity is known, either because it is worked out in G. A.
 * Campbell, <i>Cognitive Complexity: A New Way of Measuring Understandability</i> (SonarSource,
 * 2018), or because it exercises a single rule of that algorithm at a time.
 */
public class CognitiveComplexityCalculatorTest {

    private CognitiveComplexityCalculator calculator;

    @Before
    public void setUp() {
        calculator = new CognitiveComplexityCalculator();
    }

    /**
     * The first example of the paper: the outer loop is worth 1, the inner one 2 for being nested in
     * it, the {@code if} 3 for being nested in both, and the jump to a label 1.
     */
    @Test
    public void testSumOfPrimesOfTheReferencePaper() {
        assertComplexity(7, """
                int sumOfPrimes(int max) {
                    int total = 0;
                    OUT: for (int i = 1; i <= max; ++i) {
                        for (int j = 2; j < i; ++j) {
                            if (i % j == 0) {
                                continue OUT;
                            }
                        }
                        total += i;
                    }
                    return total;
                }
                """);
    }

    /**
     * The second example of the paper: a {@code switch} is worth 1 no matter how many cases it lists,
     * which is what makes this method simpler to read than the one above even though its cyclomatic
     * complexity is higher.
     */
    @Test
    public void testGetWordsOfTheReferencePaper() {
        assertComplexity(1, """
                String getWords(int number) {
                    switch (number) {
                        case 1:
                            return "one";
                        case 2:
                            return "a couple";
                        default:
                            return "lots";
                    }
                }
                """);
    }

    @Test
    public void testLinearMethodCostsNothing() {
        assertComplexity(0, """
                int add(int first, int second) {
                    int sum = first + second;
                    return sum;
                }
                """);
    }

    @Test
    public void testMethodWithoutABodyCostsNothing() {
        assertComplexity(0, "abstract int compute(int value);");
    }

    /**
     * Each branch of the chain is worth exactly one point: neither the {@code else if} nor the
     * {@code else} is penalised for the {@code if} it continues.
     */
    @Test
    public void testElseChainIsNotPenalisedForNesting() {
        assertComplexity(3, """
                String classify(int value) {
                    if (value < 0) {
                        return "negative";
                    } else if (value == 0) {
                        return "zero";
                    } else {
                        return "positive";
                    }
                }
                """);
    }

    /**
     * The body of an {@code else} is one level deeper than the {@code if} it belongs to: the
     * {@code else} is worth 1, and the {@code if} nested in it 2.
     */
    @Test
    public void testStructuresNestedInAnElseArePenalised() {
        assertComplexity(4, """
                String classify(int value) {
                    if (value < 0) {
                        return "negative";
                    } else {
                        if (value == 0) {
                            return "zero";
                        }
                        return "positive";
                    }
                }
                """);
    }

    @Test
    public void testNestingIsCountedForEachEnclosingStructure() {
        assertComplexity(6, """
                void walk(int[][] matrix) {
                    for (int[] row : matrix) {
                        while (row.length > 0) {
                            if (row[0] == 0) {
                                return;
                            }
                        }
                    }
                }
                """);
    }

    @Test
    public void testSequenceOfIdenticalOperatorsIsWorthOnePoint() {
        assertComplexity(2, """
                boolean check(boolean a, boolean b, boolean c) {
                    if (a && b && c) {
                        return true;
                    }
                    return false;
                }
                """);
    }

    @Test
    public void testMixedOperatorsAreWorthOnePointPerSequence() {
        assertComplexity(3, """
                boolean check(boolean a, boolean b, boolean c) {
                    if (a && b || c) {
                        return true;
                    }
                    return false;
                }
                """);
    }

    /**
     * An operand written between brackets is a sequence of its own, so the condition is worth two
     * points here as well, one for the {@code &&} and one for the bracketed {@code ||}.
     */
    @Test
    public void testBracketsOpenASequenceOfTheirOwn() {
        assertComplexity(3, """
                boolean check(boolean a, boolean b, boolean c) {
                    if (a && (b || c)) {
                        return true;
                    }
                    return false;
                }
                """);
    }

    /**
     * The {@code try} is worth nothing, the {@code catch} 1, and the ternary written inside it 2, since
     * a {@code catch} nests whatever it holds.
     */
    @Test
    public void testTernaryAndCatchAreCounted() {
        assertComplexity(3, """
                String read(String value) {
                    try {
                        return value.trim();
                    } catch (RuntimeException e) {
                        return value == null ? "" : value;
                    }
                }
                """);
    }

    /**
     * A lambda is worth nothing on its own, but whatever it declares is one level deeper: the
     * {@code if} it holds is worth 2 rather than 1.
     */
    @Test
    public void testLambdaNestsWithoutCostingAnything() {
        assertComplexity(2, """
                void register(java.util.List<String> values) {
                    values.forEach(value -> {
                        if (value.isEmpty()) {
                            throw new IllegalArgumentException(value);
                        }
                    });
                }
                """);
    }

    /**
     * The body of an anonymous class is read as part of the method declaring it: the method it declares
     * nests whatever it holds without being worth a point of its own, so the {@code if} is worth 2.
     */
    @Test
    public void testAnonymousClassIsPartOfTheMethodDeclaringIt() {
        assertComplexity(2, """
                void start(String name) {
                    new Thread(new Runnable() {
                        public void run() {
                            if (name.isEmpty()) {
                                throw new IllegalArgumentException();
                            }
                        }
                    }).start();
                }
                """);
    }

    @Test
    public void testLabelledBreakIsCounted() {
        assertComplexity(4, """
                void scan(int[][] matrix) {
                    OUT: for (int[] row : matrix) {
                        for (int value : row) {
                            break OUT;
                        }
                    }
                }
                """);
    }

    @Test
    public void testConstructorIsMeasuredLikeAMethod() {
        assertComplexity(1, """
                Sample(int value) {
                    if (value < 0) {
                        throw new IllegalArgumentException();
                    }
                }
                """);
    }

    /**
     * Parses a method declared by a class named {@code Sample}, so that constructors can be written as
     * they are in the sources, and asserts its cognitive complexity.
     *
     * @param expected the complexity the method is worth
     * @param method   the declaration of the method to measure
     */
    private void assertComplexity(int expected, String method) {
        TypeDeclaration<?> declaration = StaticJavaParser
                .parse("abstract class Sample {\n" + method + "\n}")
                .getType(0);
        CallableDeclaration<?> callable = declaration.getMethods().isEmpty()
                ? declaration.getConstructors().getFirst()
                : declaration.getMethods().getFirst();
        assertEquals(method, expected, calculator.complexityOf(callable));
    }
}
