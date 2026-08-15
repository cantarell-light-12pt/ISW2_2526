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
    WCYC("Weighted Cyclomatic Complexity"),

    /**
     * Maximum Cyclomatic Complexity: the highest cyclomatic complexity among the methods of the class.
     */
    MCYC("Maximum Cyclomatic Complexity"),

    /**
     * Weighted Cognitive Complexity: the sum of the cognitive complexity of the methods of the class,
     * divided by the number of methods.
     */
    WCOC("Weighted Cognitive Complexity"),

    /**
     * Maximum Cognitive Complexity: the highest cognitive complexity among the methods of the class.
     */
    MCOC("Maximum Cognitive Complexity"),

    /**
     * Churn: the number of lines of code added to and removed from the class since the previous
     * release.
     */
    CH("Churn"),

    /**
     * Maximum Churn: the highest number of lines of code added to and removed from the class in a
     * single release, over the releases it has been part of.
     */
    MCH("Maximum Churn"),

    /**
     * Change In Size: how much the class has grown or shrunk over the releases it has been part of,
     * i.e. the lines of code added to it minus the ones removed from it.
     */
    CIS("Change In Size"),

    /**
     * Number of Revisions: the number of commits the class has been subjected to over the releases it
     * has been part of.
     */
    NR("Number of Revisions"),

    /**
     * Number of Distinct Authors: the number of distinct authors that have contributed to the class
     * over the releases it has been part of.
     */
    NDA("Number of Distinct Authors"),

    /**
     * Age: the number of releases the class has been part of, counting the one being measured.
     */
    AGE("Age"),

    /**
     * Number of Latest Bug Fixes: the number of commits that fixed a bug in the class since the
     * previous release.
     */
    NLBF("Number of Latest Bug Fixes"),

    /**
     * Weighted Number of Bug Fixes: the number of commits that fixed a bug in the class over the
     * releases it has been part of, divided by its age.
     */
    WNBF("Weighted Number of Bug Fixes"),

    /**
     * Number of Blocker Smells: the number of blocker-severity code smells detected in the class.
     */
    BS("Number of Blocker Smells"),

    /**
     * Number of High Smells: the number of high-severity code smells detected in the class.
     */
    HS("Number of High Smells"),

    /**
     * Number of Medium Smells: the number of medium-severity code smells detected in the class.
     */
    MS("Number of Medium Smells"),

    /**
     * Number of Minor Smells: the number of low-severity code smells detected in the class.
     */
    LS("Number of Minor Smells"),

    /**
     * Number of Info Smells: the number of info-severity code smells detected in the class.
     */
    IS("Number of Info Smells");

    private final String description;
}
