import java.io.File

val sonarInstance = "http://localhost:9000"
//val sonarInstance = "http://sonar.inf.unibz.it"
val workDir = "extraction" + File.separatorChar


fun main(args: Array<String>) {
    val startTime = System.currentTimeMillis()

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


    val projectList = mutableListOf<HistoryProject>()
    projectList.add(HistoryProject("sasabus","",""))
    //projectList.add(HistoryProject("org.apache:commons-cli", "CLI", "https://github.com/apache/commons-cli.git"))
    //projectList.add(HistoryProject("org.apache:ambari", "AMBARI", "https://github.com/apache/ambari.git"))
    //projectList.add(HistoryProject("org.apache:hive", "HIVE", "https://github.com/apache/hive.git"))
    //projectList.add(HistoryProject("org.apache:lucene-core", "LUCENE", "https://github.com/apache/lucene-solr.git"))

    for (project in projectList) {

        val folderStr = getProjectFolder(project.sonarKey)
        makeEmptyFolder(folderStr)
        saveIssues(folderStr + "sonar-issues.csv", project.sonarKey, "CLOSED,OPEN", ruleKeys)

        //saveMeasureHistory("measures.csv", project.sonarKey)
        //mergeMeasuresWithIssues(folderStr + "measures.csv", folderStr + "sonar-issues.csv", folderStr + "measures-and-issues.csv")

        //saveJiraIssues(folderStr + "jira-faults.csv", project.jiraKey)
        //saveGitCommits(folderStr + "git-commits.csv", project.gitLink)

        //mapFaultFileCommit(folderStr + "sonar-issues.csv", folderStr + "jira-faults.csv", folderStr + "git-commits.csv", folderStr + "fault-file-commit.csv")
        /*val rFileList = listOf(
                "history-correlation-commits.R",
                "history-correlation-faults.R")
        rFileList.parallelStream().forEach { rFile -> runRscript(File(rFile), File(folderStr)) }*/
        //runRscript(File("history-correlation-commits.R"), File(folderStr))

        //mapFaultsToIssues(folderStr + "git-commits.csv",folderStr + "jira-faults.csv", folderStr + "sonar-issues.csv", folderStr + "faults-and-issues.csv")
        //groupIssuesByFaults(folderStr + "faults-and-issues.csv", folderStr + "faults-issue-count.csv")
    }

    println("Execution completed in ${(System.currentTimeMillis()-startTime)/1000.0} seconds (${(System.currentTimeMillis() - startTime)/60000} minutes)")
}

private class HistoryProject(val sonarKey: String, val jiraKey: String, val gitLink: String)
