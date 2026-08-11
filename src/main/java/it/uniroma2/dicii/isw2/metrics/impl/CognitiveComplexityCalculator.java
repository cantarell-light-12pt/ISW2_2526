package it.uniroma2.dicii.isw2.metrics.impl;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Measures the cognitive complexity of a single method, as defined by G. A. Campbell, <i>Cognitive
 * Complexity: A New Way of Measuring Understandability</i> (SonarSource, 2018).
 * <p>
 * Unlike the cyclomatic complexity CK measures, which counts every branch alike, this measure scores
 * how hard the control flow of a method is to follow: a structure interrupting the linear reading of
 * the code is worth one point, plus one further point for each structure it is nested in, while the
 * shorthands a reader takes in at a glance — the {@code else} of an {@code if}, the cases of a
 * {@code switch} — are worth nothing or are not penalised for their nesting.
 * <p>
 * Two rules of the reference algorithm are deliberately left out:
 * <ul>
 * <li>the point a recursive call is worth, since recognising one means resolving the method a call
 * refers to, which {@code javaparser-core} cannot do on its own without mistaking an overload for
 * the enclosing method;</li>
 * <li>the unwinding of negated boolean expressions, so that a sequence of logical operators is
 * scored as written rather than as its De Morgan equivalent.</li>
 * </ul>
 */
class CognitiveComplexityCalculator {

    /**
     * Measures the cognitive complexity of a method or a constructor.
     *
     * @param callable the method to measure
     * @return its cognitive complexity, 0 if it is linear or if it declares no body at all, as an
     * abstract, interface or native method does
     */
    int complexityOf(CallableDeclaration<?> callable) {
        return body(callable).map(body -> score(body, 0)).orElse(0);
    }

    /**
     * @param callable the method to measure
     * @return the statements it is made of, or an empty optional if it declares no body
     */
    private static Optional<BlockStmt> body(CallableDeclaration<?> callable) {
        if (callable instanceof MethodDeclaration method) {
            return method.getBody();
        }
        if (callable instanceof ConstructorDeclaration constructor) {
            return Optional.of(constructor.getBody());
        }
        return Optional.empty();
    }

    /**
     * Scores a node of the syntax tree and everything below it, walking the tree down while keeping
     * track of how many structures the nodes being visited are nested in.
     *
     * @param node    the node to score
     * @param nesting the number of structures the node is nested in
     * @return the cognitive complexity of the node and of its descendants
     */
    private static int score(Node node, int nesting) {
        int score = increment(node, nesting);
        int childrenNesting = nests(node) ? nesting + 1 : nesting;
        for (Node child : node.getChildNodes()) {
            score += score(child, childrenNesting);
        }
        return score;
    }

    /**
     * Scores a single node, leaving its descendants to {@link #score(Node, int)}.
     *
     * @param node    the node to score
     * @param nesting the number of structures the node is nested in
     * @return how much the node alone is worth
     */
    private static int increment(Node node, int nesting) {
        if (isElseBranch(node)) {
            // Both "else" and "else if" are worth a single point and are never penalised for their
            // nesting: the reader already paid for the "if" they belong to
            return 1;
        }
        if (isNestingStructure(node)) {
            return 1 + nesting;
        }
        if (node instanceof BinaryExpr binary && isLogicalRoot(binary)) {
            return logicalSequences(binary);
        }
        return isLabelledJump(node) ? 1 : 0;
    }

    /**
     * @param node a node of the syntax tree
     * @return whether the structures declared inside the node are to be considered one level deeper
     */
    private static boolean nests(Node node) {
        if (isElseBranch(node)) {
            // The body of an "else" is already one level deeper than the "if" it belongs to
            return false;
        }
        // A method declared inside another one, as the body of an anonymous class is, nests whatever it
        // declares without being worth a point of its own, and so does a lambda
        return isNestingStructure(node)
                || node instanceof LambdaExpr
                || node instanceof MethodDeclaration
                || node instanceof ConstructorDeclaration;
    }

    /**
     * @param node a node of the syntax tree
     * @return whether the node breaks the linear flow of the code, which makes it worth a point plus
     * one for each structure it is nested in
     */
    private static boolean isNestingStructure(Node node) {
        return node instanceof IfStmt
                || node instanceof ConditionalExpr
                || node instanceof SwitchStmt
                || node instanceof SwitchExpr
                || node instanceof ForStmt
                || node instanceof ForEachStmt
                || node instanceof WhileStmt
                || node instanceof DoStmt
                || node instanceof CatchClause;
    }

    /**
     * @param node a node of the syntax tree
     * @return whether the node is the {@code else} branch of the {@code if} it belongs to, whether it
     * is a plain {@code else} or the {@code if} of an {@code else if}
     */
    private static boolean isElseBranch(Node node) {
        return node.getParentNode()
                .filter(IfStmt.class::isInstance)
                .map(IfStmt.class::cast)
                .flatMap(IfStmt::getElseStmt)
                .filter(elseStmt -> elseStmt == node)
                .isPresent();
    }

    /**
     * @param node a node of the syntax tree
     * @return whether the node jumps to a label, which forces the reader to look the label up
     */
    private static boolean isLabelledJump(Node node) {
        if (node instanceof BreakStmt breakStmt) {
            return breakStmt.getLabel().isPresent();
        }
        return node instanceof ContinueStmt continueStmt && continueStmt.getLabel().isPresent();
    }

    /**
     * Tells whether an expression opens a chain of logical operators, i.e. whether it is a {@code &&} or
     * a {@code ||} that is not itself an operand of another one. Only the outermost operator of a chain
     * is scored, by {@link #logicalSequences(BinaryExpr)}, so that the chain is counted once; an operand
     * written between brackets opens a chain of its own, since its parent is the brackets rather than
     * the operator enclosing them.
     *
     * @param expression a binary expression
     * @return whether it is the outermost operator of a chain of logical ones
     */
    private static boolean isLogicalRoot(BinaryExpr expression) {
        return isLogical(expression) && expression.getParentNode()
                .filter(parent -> parent instanceof BinaryExpr enclosing && isLogical(enclosing))
                .isEmpty();
    }

    /**
     * Counts how many sequences of identical logical operators a chain of them is made of: a reader
     * takes in {@code a && b && c} as a single condition, while {@code a && b || c} forces them to
     * work out how the two operators combine.
     *
     * @param root the outermost operator of the chain
     * @return the number of sequences of identical operators it is made of, at least one
     */
    private static int logicalSequences(BinaryExpr root) {
        List<BinaryExpr.Operator> operators = new ArrayList<>();
        collectOperators(root, operators);
        int sequences = 1;
        for (int i = 1; i < operators.size(); i++) {
            if (operators.get(i) != operators.get(i - 1)) {
                sequences++;
            }
        }
        return sequences;
    }

    /**
     * Lists the logical operators of a chain in the order they are written in, by visiting each of them
     * between its operands.
     *
     * @param expression the expression to walk down, which stops the walk unless it is a logical operator
     * @param operators  the operators found so far
     */
    private static void collectOperators(Expression expression, List<BinaryExpr.Operator> operators) {
        if (!(expression instanceof BinaryExpr binary) || !isLogical(binary)) {
            return;
        }
        collectOperators(binary.getLeft(), operators);
        operators.add(binary.getOperator());
        collectOperators(binary.getRight(), operators);
    }

    /**
     * @param expression a binary expression
     * @return whether it combines its operands with {@code &&} or {@code ||}
     */
    private static boolean isLogical(BinaryExpr expression) {
        return expression.getOperator() == BinaryExpr.Operator.AND
                || expression.getOperator() == BinaryExpr.Operator.OR;
    }
}
