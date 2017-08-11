import java.io.File

//val sonarInstance = "http://localhost:9000"
val sonarInstance = "http://sonar.inf.unibz.it"
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
    //projectList.add(HistoryProject("sasabus","",""))
    projectList.add(HistoryProject("org.apache:commons-cli", "CLI", "https://github.com/apache/commons-cli.git"))
    //projectList.add(HistoryProject("org.apache:ambari", "AMBARI", "https://github.com/apache/ambari.git"))
    //projectList.add(HistoryProject("org.apache:hive", "HIVE", "https://github.com/apache/hive.git"))
    //projectList.add(HistoryProject("org.apache:lucene-core", "LUCENE", "https://github.com/apache/lucene-solr.git"))
    //projectList.add(HistoryProject("bcel", "BCEL", "https://github.com/apache/commons-bcel.git"))

    for (project in projectList) {

        val folderStr = getProjectFolder(project.sonarKey)
        val issuesFile = folderStr + "sonar-issues.csv"
        val measuresFile = folderStr + "measure-history.csv"
        val measuresAndIssuesFile = folderStr + "measures-and-issues.csv"
        val jiraFaultFile = folderStr + "jira-faults.csv"
        val gitCommitFile = folderStr + "git-commits.csv"
        val faultFileCommitFile = folderStr + "fault-file-commit.csv"
        val faultsAndIssuesFile = folderStr + "faults-and-issues.csv"
        val faultsIssueCountFile = folderStr + "faults-issue-count.csv"

        makeEmptyFolder(folderStr)
        saveIssues(issuesFile, project.sonarKey, "CLOSED,OPEN", ruleKeys)

        saveMeasureHistory(measuresFile, project.sonarKey)
        mergeMeasuresWithIssues(measuresFile, issuesFile, measuresAndIssuesFile)

        saveJiraIssues(jiraFaultFile, project.jiraKey)
        saveGitCommits(gitCommitFile, project.gitLink)

        mapFaultFileCommit(issuesFile, jiraFaultFile, gitCommitFile, measuresFile, faultFileCommitFile)
        val rFileList = listOf(
                "history-correlation-commits.R",
                "history-correlation-faults.R")
        rFileList.parallelStream().forEach { rFile -> runRscript(File(rFile), File(folderStr)) }
        runRscript(File("history-correlation-commits.R"), File(folderStr))

        mapFaultsToIssues(gitCommitFile, jiraFaultFile, issuesFile, faultsAndIssuesFile)
        groupIssuesByFaults(faultsAndIssuesFile, faultsIssueCountFile)
    }

    println("Execution completed in ${(System.currentTimeMillis()-startTime)/1000.0} seconds (${(System.currentTimeMillis() - startTime)/60000} minutes)")
}

private class HistoryProject(val sonarKey: String, val jiraKey: String, val gitLink: String)
