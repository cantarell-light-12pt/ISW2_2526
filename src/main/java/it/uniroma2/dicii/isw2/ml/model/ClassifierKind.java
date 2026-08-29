package it.uniroma2.dicii.isw2.ml.model;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The classifiers a model can be trained as, selected at runtime out of the configuration.
 * <p>
 * The three answer to the shape of a dataset in different ways, which is why running more than one of
 * them says something a single one would not. A forest already selects while it splits and is expected
 * to shrug at a metric that carries nothing; naive Bayes assumes its attributes independent and is the
 * one a redundant metric hurts most; a nearest-neighbour classifier measures distances over every
 * attribute at once, so an uninformative one is noise added to every comparison it makes.
 */
@Getter
@AllArgsConstructor
public enum ClassifierKind {

    RANDOM_FOREST("RandomForest"),
    NAIVE_BAYES("NaiveBayes"),
    IBK("IBk");

    /**
     * How the classifier reads in the configuration and in the reports, which is the name Weka knows it
     * by.
     */
    private final String label;

    /**
     * Converts the given label to the corresponding {@code ClassifierKind}. The comparison is
     * case-insensitive.
     * <p>
     * As with {@code ProportionMethod.from}, no fallback is returned for an unknown label: the list of
     * classifiers is read from the configuration, so a typo must fail loudly rather than quietly leave
     * a model out of a comparison the reader believes is complete.
     *
     * @param label the name to convert, e.g. "RandomForest"
     * @return the classifier it names
     * @throws MlException if it names none of them
     */
    public static ClassifierKind from(String label) throws MlException {
        for (ClassifierKind kind : values()) {
            if (kind.getLabel().equalsIgnoreCase(label)) {
                return kind;
            }
        }
        String known = Arrays.stream(values()).map(ClassifierKind::getLabel)
                .collect(Collectors.joining(", "));
        throw new MlException("Unknown classifier '" + label + "'. Known classifiers are: " + known);
    }

}
