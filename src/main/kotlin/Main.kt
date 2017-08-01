import java.io.File

val sonarInstance = "http://sonar.inf.unibz.it"
val workDir = "extraction" + File.separatorChar


fun main(args: Array<String>) {
    val startTime = System.currentTimeMillis()

    val metricKeys = getMetricKeys()
    val ruleKeys = getRuleKeys()

    //val projectKeys = getProjectsContainingString("QC -")//QC - aspectj, QC - jboss, QC - jtopen

    // extract issues from sonarInstance, takes long
    //for (projectKey in projectKeys)
    //    saveIssues("current-issues.csv", projectKey, "OPEN", ruleKeys)

    // map sonar issues to arch cycle smells (~2h30min)
    //mapIssuesToCyclicDependencies(projectKeys)
    // map sonar issues to arch MAS smells (40min)
    //mapIssuesToMAS(projectKeys)

    // calculate correlations (~30min)
    //calculateCorrelationsForProjects(projectKeys)
    // merge correlation values from projects (~1s)
    //mergeCorrelationsByProject(projectKeys)
    // extracts a summary for the calculated correlations (~90s)
    //summariseCorrelations(outFile = "by-project-summary.csv")
    // runs R scripts treating the whole dataset as a single project. Takes at least a night.
    //calculateFullDatasetCorrelation(projectKeys)


    //save history csv for "org.apache:commons-cli"
    val projectKey = "org.apache:commons-cli"
    saveIssues("sonar-issues.csv", projectKey, "CLOSED,OPEN", ruleKeys)
    val folderStr = getProjectFolder(projectKey)
    saveNonemptyPastMeasures(folderStr + "nonempty-past-measures.txt", projectKey, metricKeys)
    val usefulMetricKeys = readListFromFile(folderStr + "nonempty-past-measures.txt")
    saveMeasureHistory("measures.csv", projectKey, usefulMetricKeys)
    mergeMeasuresWithIssues(folderStr + "measures.csv", folderStr + "sonar-issues.csv", folderStr + "measures-and-issues.csv")

    saveJiraIssues(folderStr + "jira-faults.csv", "CLI")
    saveGitCommits(folderStr + "git-commits.csv", "https://github.com/apache/commons-cli.git")

    mapFaultsToIssues(folderStr + "git-commits.csv",folderStr + "jira-faults.csv",
            folderStr + "sonar-issues.csv", folderStr + "faults-and-issues.csv")
    groupIssuesByFaults(folderStr + "faults-and-issues.csv", folderStr + "faults-issue-count.csv")

    println("Execution completed in ${(System.currentTimeMillis()-startTime)/1000.0} seconds (${(System.currentTimeMillis() - startTime)/60000} minutes)")
}