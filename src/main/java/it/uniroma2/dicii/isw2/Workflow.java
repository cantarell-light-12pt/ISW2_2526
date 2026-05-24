package it.uniroma2.dicii.isw2;

import it.uniroma2.dicii.isw2.properties.PropertiesManager;
import it.uniroma2.dicii.isw2.versions.VersionsRetriever;
import it.uniroma2.dicii.isw2.versions.exception.VersionsException;
import it.uniroma2.dicii.isw2.versions.impl.JiraVersionsRetrieverImpl;
import it.uniroma2.dicii.isw2.versions.model.Version;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class Workflow {

    public void execute() {
        String projectName = PropertiesManager.getInstance().getProperty("project.name");
        String jiraBaseUrl = PropertiesManager.getInstance().getProperty("project.jira.baseUrl");
        String jiraVersionsParameterizedPath = PropertiesManager.getInstance().getProperty("project.jira.versionsParameterizedPath");

        String jiraVersionUrl = String.format(jiraVersionsParameterizedPath, jiraBaseUrl, projectName);

        // 1. Retrieve the list of versions from Jira
        VersionsRetriever versionsRetriever = new JiraVersionsRetrieverImpl(jiraVersionUrl);
        try {
            List<Version> versions = versionsRetriever.retrieveVersions();
            versions.forEach(version -> log.debug("Version: {}", version.getName()));

            log.info("Workflow completed successfully!");
        } catch (VersionsException e) {
            log.error("Error retrieving versions from Jira", e);
        }

    }

}
