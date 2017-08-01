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
    val folderStr = getProjectFolder(projectKey)
    makeEmptyFolder(folderStr)
    saveNonemptyPastMeasures(folderStr + "nonempty-past-measures.txt", projectKey, metricKeys)
    val usefulMetricKeys = readListFromFile(folderStr + "nonempty-past-measures.txt")
    saveMeasureHistory(fileName = "measures.csv", projectKey = projectKey, metricKeys = usefulMetricKeys)
    saveIssues(fileName = "issues.csv", projectKey = projectKey, statuses = "CLOSED,OPEN", ruleKeys = ruleKeys)
    mergeMeasuresWithIssues(measuresFile = folderStr + "measures.csv", issuesFile = folderStr + "issues.csv", combinedFile = folderStr + "measures-and-issues.csv")

    saveJiraIssues(folderStr + "jira-issues.csv", "CLI")
    saveGitCommits(folderStr + "git-commits.csv", "https://github.com/apache/commons-cli.git")

    //mergeFaultsAndSmells("git-commits.csv","jira-issues.csv", "issues.csv", "faults-and-smells.csv")


    println("Execution completed in ${(System.currentTimeMillis()-startTime)/1000.0} seconds (${(System.currentTimeMillis() - startTime)/60000} minutes)")
}