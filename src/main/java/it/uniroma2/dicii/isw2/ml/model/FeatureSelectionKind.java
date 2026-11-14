package it.uniroma2.dicii.isw2.ml.model;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Which of the attributes of the training rows a model is trained on, selected at runtime out of the
 * configuration.
 * <p>
 * The dataset describes a class by some twenty-five metrics, and they are far from independent: the
 * size metrics move together, the history ones are counts of the same commits read four ways. A model
 * pays for that differently depending on what it is — naive Bayes counts a redundant metric twice, a
 * nearest-neighbour classifier adds an uninformative one to every distance it measures — which is why
 * the selection is chosen per model and not once for all of them.
 * <p>
 * The three techniques differ in what they ask of an attribute. Information gain ranks each of them on
 * its own, by how much knowing it tells of the label, and keeps the best few: it is cheap, and blind to
 * two attributes saying the same thing. The two searches judge a <i>subset</i> instead, by how well its
 * attributes predict the label and how little they predict each other, and walk towards the best one
 * from opposite ends: forward from no attribute, adding the one that helps most, and backward from all
 * of them, dropping the one that hurts most, each stopping when no step improves the subset. They tend
 * to agree; where they do not, it is because an attribute only helps in the company of another one,
 * which only the backward search starts out holding.
 */
@Getter
@AllArgsConstructor
public enum FeatureSelectionKind {

    /**
     * Every attribute, which is what every figure reported so far was measured on and is therefore what
     * the other three are read against.
     */
    NONE("None"),

    /**
     * The attributes telling most of the label on their own, ranked by information gain.
     */
    INFO_GAIN("InfoGain"),

    /**
     * The subset a greedy search reaches starting from no attribute and adding one at a time.
     */
    FORWARD_SEARCH("ForwardSearch"),

    /**
     * The subset a greedy search reaches starting from every attribute and dropping one at a time.
     */
    BACKWARD_SEARCH("BackwardSearch");

    /**
     * How the technique reads in the configuration and in the reports.
     */
    private final String label;

    /**
     * Converts the given label to the corresponding {@code FeatureSelectionKind}. The comparison is
     * case-insensitive.
     * <p>
     * As with {@link BalancingKind#from(String)}, no fallback is returned for an unknown label: a typo
     * must fail loudly rather than quietly report a model trained on every attribute under the heading
     * of one trained on a few.
     *
     * @param label the name to convert, e.g. "InfoGain"
     * @return the technique it names
     * @throws MlException if it names none of them
     */
    public static FeatureSelectionKind from(String label) throws MlException {
        for (FeatureSelectionKind kind : values()) {
            if (kind.getLabel().equalsIgnoreCase(label)) {
                return kind;
            }
        }
        String known = Arrays.stream(values()).map(FeatureSelectionKind::getLabel)
                .collect(Collectors.joining(", "));
        throw new MlException("Unknown feature selection technique '" + label + "'. Known techniques "
                + "are: " + known);
    }

}
