package it.uniroma2.dicii.isw2.association.impl;

import it.uniroma2.dicii.isw2.association.IssueCommitAssociator;
import it.uniroma2.dicii.isw2.issues.model.Issue;
import it.uniroma2.dicii.isw2.repo.model.Commit;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class JiraGitAssociator implements IssueCommitAssociator {

    @Override
    public Map<Issue, List<Commit>> associate(List<Issue> issues, List<Commit> commits) {
        log.info("Associating {} Jira issues with {} Git commits...", issues.size(), commits.size());
        Map<Issue, List<Commit>> associationMap = new HashMap<>();

        for (Issue issue : issues) {
            associationMap.put(issue, new ArrayList<>());
            String issueKey = issue.getKey(); // e.g., ZOOKEEPER-123

            // We use word boundaries (\b) to avoid partial matches.
            // i.e., ZOOKEEPER-123 should NOT match ZOOKEEPER-1234
            String regex = "\\b" + Pattern.quote(issueKey) + "\\b";
            Pattern pattern = Pattern.compile(regex);

            String commitIds;
            for (Commit commit : commits) {
                if (commit.fullMessage() != null) {
                    Matcher matcher = pattern.matcher(commit.fullMessage());
                    if (matcher.find()) {
                        associationMap.get(issue).add(commit);
                        log.debug("Associated commit {} to issue {}", commit.id(), issueKey);
                        if (associationMap.get(issue).size() > 1) {
                            commitIds = associationMap.get(issue).stream().map(Commit::id).reduce((id1, id2) -> id1 + ", " + id2).orElse("");
                            log.warn("Multiple commits associated to issue {}: \n{}", issueKey, commitIds);
                        }
                    }
                }
            }
        }

        log.info("Association complete. Processed {} mappings.", associationMap.size());
        return associationMap;
    }
}