package it.uniroma2.dicii.isw2.metrics;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The class-level metrics that make up the dataset, identified by the acronym they are catalogued
 * under in the README. Every metric is measured on a single class of a single released version, and
 * each {@link MetricsExtractor} contributes the subset it is able to compute.
 */
@Getter
@RequiredArgsConstructor
public enum Metric {

    /**
     * Coupling Between Objects: the number of direct dependencies between the class and other classes.
     */
    CBO("Coupling Between Objects"),

    /**
     * Response For a Class: the number of methods that can potentially be executed in response to a
     * message received by an object of the class.
     */
    RFC("Response For a Class"),

    /**
     * Depth of Inheritance Tree: the number of superclasses in the inheritance tree of the class.
     */
    DIT("Depth of Inheritance Tree"),

    /**
     * Lack of Cohesion of Methods: how loosely the methods of the class are related to each other
     * through the fields they share.
     */
    LCOM("Lack of Cohesion of Methods"),

    /**
     * Number of Children: the number of immediate subclasses of the class.
     */
    NOC("Number of Children"),

    /**
     * Size: the number of lines of code of the class.
     */
    LOC("Size"),

    /**
     * Number of Methods declared by the class.
     */
    NM("Number of Methods"),

    /**
     * Number of Attributes declared by the class.
     */
    NA("Number of Attributes"),

    /**
     * Weighted Cyclomatic Complexity: the sum of the cyclomatic complexity of the methods of the class,
     * divided by the number of methods.
     */
    WCC("Weighted Cyclomatic Complexity"),

    /**
     * Maximum Cyclomatic Complexity: the highest cyclomatic complexity among the methods of the class.
     */
    MCC("Maximum Cyclomatic Complexity");

    private final String description;
}
