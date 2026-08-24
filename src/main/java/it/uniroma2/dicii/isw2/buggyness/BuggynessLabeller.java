package it.uniroma2.dicii.isw2.buggyness;

import it.uniroma2.dicii.isw2.buggyness.exception.BuggynessException;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.versions.model.Version;

/**
 * What tells the classes of a released version that held a defect from the ones that did not, i.e.
 * what produces the column a defect-prediction model is trained to predict.
 * <p>
 * The label is deliberately not a {@link it.uniroma2.dicii.isw2.metrics.Metric}: every metric of the
 * dataset is something measured on the class itself, whereas whether a class was buggy is something
 * the defect reports say about it, and a class no report ever mentioned is not unmeasured — it is
 * not buggy.
 * <p>
 * A labeller is handed one released version at a time, as {@link
 * it.uniroma2.dicii.isw2.dataset.DatasetWriter} is, so that the rows of a version can be labelled
 * and written as soon as they have been measured.
 */
public interface BuggynessLabeller {

    /**
     * Marks buggy the classes of a released version that held at least one defect at the time it was
     * released, leaving every other class of the report unmarked.
     *
     * @param version the released version the report describes
     * @param report  the measures taken on its classes, labelled in place
     * @throws BuggynessException if what the defects say about the project cannot be read
     */
    void label(Version version, MetricsReport report) throws BuggynessException;

}
