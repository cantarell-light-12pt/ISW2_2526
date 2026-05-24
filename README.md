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
- **Weighted Cyclomatic Complexity** (WCC): the sum of the cyclomatic complexity of the class methods, divided by the number of methods.
- **Maximum Cyclomati Complexity** (MCC): the maximum cyclomatic complexity of the class methods.
- **Weighted Cognitive Complexity** (WCC): the sum of the cognitive complexity of the class methods, divided by the number of methods.
- **Maximum Cognitive Complexity** (MCC): the maximum cognitive complexity of the class methods.

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
