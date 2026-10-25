package it.uniroma2.dicii.isw2.ml.model;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * How the two classes of the training rows are brought to the same size, selected at runtime out of the
 * configuration.
 * <p>
 * Some 23% of the rows of the dataset are buggy, which is enough for a model that finds none of them to
 * score .77 accuracy. The two techniques close that gap from opposite ends and pay for it in opposite
 * ways: undersampling cuts the majority down to the size of the minority, and throws away most of what
 * a model could have learnt about the classes nobody ever fixed a defect in; SMOTE synthesises the
 * minority back up to the size of the majority, out of rows lying between each buggy class and its
 * nearest buggy neighbours, and teaches the model classes that no release of the project ever held.
 * Running both is what says which of the two the dataset answers to.
 */
@Getter
@AllArgsConstructor
public enum BalancingKind {

    /**
     * The rows as they are, which is what every figure reported so far was measured on and is therefore
     * what the other two are read against.
     */
    NONE("None"),

    /**
     * The majority is cut down to the size of the minority.
     */
    UNDERSAMPLING("Undersampling"),

    /**
     * The minority is synthesised back up to the size of the majority, through the Synthetic Minority
     * Over-sampling TEchnique.
     */
    SMOTE("SMOTE");

    /**
     * How the technique reads in the configuration and in the reports.
     */
    private final String label;

    /**
     * Converts the given label to the corresponding {@code BalancingKind}. The comparison is
     * case-insensitive.
     * <p>
     * As with {@link ClassifierKind#from(String)}, no fallback is returned for an unknown label: the
     * technique is read from the configuration, so a typo must fail loudly rather than quietly report a
     * run of the unbalanced rows under the heading of a balanced one.
     *
     * @param label the name to convert, e.g. "Undersampling"
     * @return the technique it names
     * @throws MlException if it names none of them
     */
    public static BalancingKind from(String label) throws MlException {
        for (BalancingKind kind : values()) {
            if (kind.getLabel().equalsIgnoreCase(label)) {
                return kind;
            }
        }
        String known = Arrays.stream(values()).map(BalancingKind::getLabel)
                .collect(Collectors.joining(", "));
        throw new MlException("Unknown balancing technique '" + label + "'. Known techniques are: "
                + known);
    }

}
