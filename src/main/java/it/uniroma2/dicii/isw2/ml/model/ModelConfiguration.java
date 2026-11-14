package it.uniroma2.dicii.isw2.ml.model;

/**
 * Everything that tells one measured model apart from another: what it is, how the rows it is trained
 * on are balanced, and which of their attributes it is trained on.
 * <p>
 * One value rather than three parameters, because the three travel together everywhere a model is
 * built, measured or reported, and a fourth knob is then a component here rather than a parameter added
 * to every signature between the workflow and Weka.
 *
 * @param classifier       which model to train
 * @param balancing        how the two classes of the rows it is trained on are brought to the same size
 * @param featureSelection which of the attributes of those rows it is trained on
 */
public record ModelConfiguration(ClassifierKind classifier, BalancingKind balancing,
                                 FeatureSelectionKind featureSelection) {

    public ModelConfiguration {
        if (classifier == null || balancing == null || featureSelection == null) {
            throw new IllegalArgumentException("A model is measured as a classifier, a balancing "
                    + "technique and a feature selection, each of them named, not as " + classifier
                    + ", " + balancing + " and " + featureSelection);
        }
    }

    /**
     * @return how the configuration reads in a log line or in the report of a failure
     */
    public String describe() {
        return classifier.getLabel() + " balanced by " + balancing.getLabel() + " with its features "
                + "selected by " + featureSelection.getLabel();
    }

}
