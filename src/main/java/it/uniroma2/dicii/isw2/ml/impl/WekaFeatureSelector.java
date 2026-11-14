package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.FeatureSelector;
import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.FeatureSelectionKind;
import weka.attributeSelection.ASEvaluation;
import weka.attributeSelection.ASSearch;
import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.CfsSubsetEval;
import weka.attributeSelection.GreedyStepwise;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.classifiers.Classifier;
import weka.classifiers.meta.AttributeSelectedClassifier;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;

/**
 * Selects attributes through the Weka attribute selection engine.
 * <p>
 * Information gain is an evaluator of single attributes, so it is paired with the {@link Ranker}, which
 * orders them by it and keeps the configured number of the best. Kept by number rather than by a
 * threshold on the gain: every metric of the dataset tells <i>something</i> of the label, so the
 * threshold the ranker defaults to keeps all of them and selects nothing, and a gain measured in bits
 * means a different thing on every dataset it is read off.
 * <p>
 * The two searches are a {@link GreedyStepwise} walk, forward from no attribute or backward from all of
 * them, over subsets judged by {@link CfsSubsetEval}: a subset scores well when its attributes correlate
 * with the label and poorly with each other. The subsets are judged by a correlation and not by training
 * the model itself on each of them, which is the other way of doing it and is out of reach here — a
 * search visits some three hundred subsets of twenty-five metrics, the model would be cross-validated
 * over each of them, and all of that would happen again inside each of the ten folds the model is
 * measured over. The choice is then the same for the three models, made of the rows and not of what a
 * model makes of them, and what the comparison measures is how each model answers to it.
 */
public class WekaFeatureSelector implements FeatureSelector {

    private final MlSettings settings;

    public WekaFeatureSelector(MlSettings settings) {
        this.settings = settings;
    }

    @Override
    public Classifier selecting(Classifier model, FeatureSelectionKind kind) {
        if (kind == FeatureSelectionKind.NONE) {
            return model;
        }
        AttributeSelectedClassifier selecting = new AttributeSelectedClassifier();
        selecting.setClassifier(model);
        selecting.setEvaluator(evaluator(kind));
        selecting.setSearch(search(kind));
        return selecting;
    }

    @Override
    public List<String> select(Instances data, FeatureSelectionKind kind) throws MlException {
        if (data.classIndex() < 0) {
            throw new MlException("The " + data.numInstances() + " rows to select attributes out of do "
                    + "not say which of their attributes is the label");
        }
        if (kind == FeatureSelectionKind.NONE) {
            List<String> every = new ArrayList<>(data.numAttributes() - 1);
            for (int i = 0; i < data.numAttributes(); i++) {
                if (i != data.classIndex()) {
                    every.add(data.attribute(i).name());
                }
            }
            return every;
        }
        int[] kept = keptIndices(data, kind);
        List<String> names = new ArrayList<>(kept.length);
        for (int index : kept) {
            // The engine hands the label back along with what it kept, as the last of the indices
            if (index != data.classIndex()) {
                names.add(data.attribute(index).name());
            }
        }
        return names;
    }

    /**
     * Runs the selection itself.
     *
     * @param data the rows to choose out of
     * @param kind which technique to keep them by, which is not {@link FeatureSelectionKind#NONE}
     * @return the indices of the attributes it kept, the label's included
     * @throws MlException if the technique cannot be run over the rows
     */
    private int[] keptIndices(Instances data, FeatureSelectionKind kind) throws MlException {
        try {
            AttributeSelection selection = new AttributeSelection();
            selection.setEvaluator(evaluator(kind));
            selection.setSearch(search(kind));
            selection.SelectAttributes(data);
            return selection.selectedAttributes();
        } catch (Exception e) {
            // Weka reports everything from an evaluator refusing an attribute type to a search finding
            // nothing to start from as a bare Exception, which leaves nothing more specific to catch
            throw new MlException("Unable to select the attributes of " + data.numInstances()
                    + " rows by " + kind.getLabel(), e);
        }
    }

    /**
     * @param kind which technique to keep the attributes by, which is not
     *             {@link FeatureSelectionKind#NONE}
     * @return what judges an attribute, or a subset of them, under it. Fresh every time, since an
     * evaluator holds what it learnt of the last rows it was built on
     */
    private static ASEvaluation evaluator(FeatureSelectionKind kind) {
        return kind == FeatureSelectionKind.INFO_GAIN ? new InfoGainAttributeEval() : new CfsSubsetEval();
    }

    /**
     * @param kind which technique to keep the attributes by
     * @return how the attributes judged are walked through under it
     * @throws IllegalArgumentException if it is {@link FeatureSelectionKind#NONE}, which walks through
     *                                  nothing and is answered before any search is built
     */
    private ASSearch search(FeatureSelectionKind kind) {
        return switch (kind) {
            case INFO_GAIN -> ranker();
            case FORWARD_SEARCH -> greedy(false);
            case BACKWARD_SEARCH -> greedy(true);
            case NONE -> throw new IllegalArgumentException("Keeping every attribute takes no search");
        };
    }

    /**
     * @return a ranker keeping the configured number of attributes, the best by the gain first
     */
    private Ranker ranker() {
        Ranker ranker = new Ranker();
        ranker.setNumToSelect(settings.infoGainAttributes());
        return ranker;
    }

    /**
     * @param backwards whether to start from every attribute and drop them, rather than from none and add
     *                  them
     * @return a greedy walk through the subsets, stopping at the first step that improves nothing
     */
    private static GreedyStepwise greedy(boolean backwards) {
        GreedyStepwise search = new GreedyStepwise();
        search.setSearchBackwards(backwards);
        return search;
    }

}
