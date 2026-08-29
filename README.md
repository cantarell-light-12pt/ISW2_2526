[![Quality gate status](https://sonarcloud.io/api/project_badges/measure?project=cantarell-light-12pt_ISW2_2526&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=cantarell-light-12pt_ISW2_2526)
# ISW2_2526
Repo for the ISW2 course project, a.a. 2025/2026

## Milestone 1: Dataset creation
This milestone consists in the retrieval of a set of class-level metrics from the source code of the Apache ZooKeeper project.
These metrics will be extracted from each class for each released version of the project.

Also, by associating the project commits messages to the Jira tickets' number, each class in each version will be labeled as "buggy" (if it contained a bug on that version) or "not buggy" otherwise.

Next follows a description of the metrics that will be extracted, divided by category.
### Code complexity metrics
These metrics aim to quantify the complexity of the source code of a class.
- **Size** (LOC): number of lines of code in the class at the current release.
- **Number of Methods** (NM): number of methods in the class.
- **Number of Attributes** (NA): number of attributes in the class.
- **Weighted Cyclomatic Complexity** (WCyC): the sum of the cyclomatic complexity of the class methods, divided by the number of methods.
- **Maximum Cyclomatic Complexity** (MCyC): the maximum cyclomatic complexity of the class methods.
- **Weighted Cognitive Complexity** (WCoC): the sum of the cognitive complexity of the class methods, divided by the number of methods.
- **Maximum Cognitive Complexity** (MCoC): the maximum cognitive complexity of the class methods.

### Object-oriented metrics
These metrics aim to quantify the complexity of a class from an object-oriented perspective.
- **Coupling Between Objects** (CBO): the number of direct dependencies between the class and its superclasses.
- **Response for a Class** (RFC): the set of methods that can potentially be executed in response to a message received by an object of that class. It measures the potential communication complexity.
- **Depth of Inheritance Tree** (DIT): the number of superclasses in the class inheritance tree.
- **Lack of Cohesion of Methods** (LCOM): measures how well the methods of a class are related to each other via shared fields. High LCOM means the class is trying to do too many unrelated things
- **Number of Children** (NOC): the number of immediate subclasses in the class inheritance tree.

### Evolution metrics
These metrics quantify the evolution of a class over releases.
- **Churn** (CH): the number of lines of coded added and removed from the previous release.
- **Max Churn** (MCH): the maximum number of lines of code added and removed in a single release.
- **Change in Size** (CIS): the number of lines of code added or removed in the class over time.
- **Number of Revisions** (NR): the number of commits the class has been subjected to along revisions.
- **Number of Distinct Authors** (NDA): the number of distinct authors that have contributed to the class over releases.
- **Age** (AGE): number of releases the class has been in the project.
- **Number of Latest Bug Fixes** (NLBF): the number of commits that fixed a bug in the class since the last release.
- **Weighted Number of Bug Fixes** (WNBF): the number of commits that fixed a bug in the class over released, divided by the class age.

### Smells metrics
These metrics quantity the number of code smells detected in a class, according to their severity as described by PMD ([reference](https://pmd.github.io/pmd/pmd_rules_java.html)).
- **Number of Blocker Smells** (BS): the number of blocker-severity code smells detected in the class.
- **Number of High Smells** (HS): the number of high-severity code smells detected in the class.
- **Number of Medium Smells** (MS): the number of medium-severity code smells detected in the class.
- **Number of Minor Smells** (LS): the number of low-severity code smells detected in the class.
- **Number of Info Smells** (IS): the number of info-severity code smells detected in the class.

## Milestone 2: Defect prediction
This milestone trains a classifier on the dataset built in Milestone 1 and measures how well it predicts which classes are faulty.

The model is trained on the **trimmed** dataset, i.e. the earliest third of the releases. The buggy label of a release is only as good as the defects reported against it, and the most recent releases have had the least time to have any reported: learning from the tail would be learning that the recent releases are clean, which is a fact about the calendar rather than about the code.

That dataset is then split in two, at a release and never at a row: the earliest **two thirds** of the releases are what the models are trained on, and the remaining third is held back to test them on. Cutting at a release is what makes every training row older than every test row, which is the only arrangement under which the result means "how well would this have predicted what came next". A random row split would instead let a model read a class's future — the same class one release later, barely changed and carrying the answer — which is information no predictor could ever have had.

The run then has two measured phases:
- **Validation**: each model is cross-validated by **10-fold cross validation** over the training releases alone, on folds stratified so that each holds the buggy share of the whole. This is what one model is chosen over another by; confining it to the training releases is what stops choosing from spending the test set.
- **Inference**: each model is then trained on all of the training releases and asked about the held-out ones, which nothing has read until this point.

Both phases compute the following metrics, reported side by side in a single CSV file:
- **Accuracy**: the share of classes labelled correctly.
- **Precision**: of the classes predicted buggy, how many were.
- **Recall**: of the buggy classes, how many were found.
- **F1**: the harmonic mean of precision and recall.
- **AUC**: the area under the ROC curve, i.e. how well the model separates the two classes at any threshold.
- **Kappa**: the agreement with the truth over and above what guessing at the observed rates would reach.

Precision, recall, F1 and AUC are of the **buggy** class alone rather than averaged over the two. Around 23% of the rows are buggy, so a model answering "not buggy" to every class already scores 77% accuracy while finding nothing at all, and only the figures of the minority class say so.

The test figures are expected to be the lower pair, and the distance between the two phases is itself a result: it is how much of the validation score was the model recognising classes it had already met rather than recognising faults.

Which classifiers are trained is configuration rather than code (`project.ml.classifiers`), so that the comparison can be extended to several models, and the workflow takes the rows to train on as an argument, so that it can equally be run over a dataset reduced by feature selection.
