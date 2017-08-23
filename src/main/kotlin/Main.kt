import java.io.File
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

//val sonarInstance = "http://localhost:9000"
val sonarInstance = "http://sonar.inf.unibz.it"
val workDir = "extraction" + File.separatorChar


fun main(args: Array<String>) {
    val startTime = System.currentTimeMillis()

    val ruleKeys = getRuleKeys()

    // Qualitas Corpus
    val projectKeys = getProjectsContainingString("QC -")//QC - aspectj, QC - jboss, QC - jtopen

    // extract issues from sonarInstance, takes long
    //for (projectKey in projectKeys)
    //    saveIssues("current-issues.csv", projectKey, "OPEN", ruleKeys)

    // map sonar issues to arch cycle smells (~2h30min)
    //mapIssuesToCyclicDependencies(projectKeys, false)
    mapIssuesToCyclicDependencies(projectKeys, true)
    // map sonar issues to arch MAS smells (40min)
    //mapIssuesToMAS(projectKeys, false)
    mapIssuesToMAS(projectKeys, true)

    // calculate correlations (~30min)
    calculateCorrelationsForProjects(projectKeys)
    // merge correlation values from projects (~1s)
    mergeCorrelationsByProject(projectKeys)
    // extracts a summary for the calculated correlations (~90s)
    summariseCorrelations(outFile = "by-project-summary.csv")
    // runs R scripts treating the whole dataset as a single project. Takes at least a night.
    //calculateFullDatasetCorrelation(projectKeys)

    println("Execution completed in ${(System.currentTimeMillis()-startTime)/1000.0} seconds (${(System.currentTimeMillis() - startTime)/60000} minutes)")
    return


    val projectList = mutableListOf<HistoryProject>()
    //done:

    //projectList.add(HistoryProject("org.apache:commons-cli", "CLI", "https://github.com/apache/commons-cli.git", false))
    //projectList.add(HistoryProject("org.apache:ambari", "AMBARI", "https://github.com/apache/ambari.git", false))
    projectList.add(HistoryProject("org.apache:bcel", "BCEL", "https://github.com/apache/commons-bcel.git", false))
    projectList.add(HistoryProject("org.apache:dbutils", "DBUTILS", "https://github.com/apache/commons-dbutils.git", false))
    projectList.add(HistoryProject("org.apache:deamon", "DAEMON", "https://github.com/apache/commons-daemon.git", false))
    projectList.add(HistoryProject("org.apache:digester", "DIGESTER", "https://github.com/apache/commons-digester.git", false))
    projectList.add(HistoryProject("org.apache:dbcp", "DBCP", "https://github.com/apache/commons-dbcp.git", false))
    projectList.add(HistoryProject("org.apache:commons-exec", "EXEC", "https://github.com/apache/commons-exec.git", false))
    projectList.add(HistoryProject("org.apache:commons-fileupload", "FILEUPLOAD", "https://github.com/apache/commons-fileupload.git", false))
    projectList.add(HistoryProject("org.apache:httpclient", "HTTPCLIENT", "https://github.com/apache/httpcomponents-client.git", false))


    //need extraction
    //projectList.add(HistoryProject("org.apache:hive", "HIVE", "https://github.com/apache/hive.git", false)) java memory overflow
    //projectList.add(HistoryProject("org.apache:lucene-core", "LUCENE", "https://github.com/apache/lucene-solr.git", false)) java memory overflow

    //need analysis:
    //projectList.add(HistoryProject("org.apache:httpcore", "HTTPCORE", "https://github.com/apache/httpcomponents-core.git", false)) 860/2804 analyses failed
    projectList.add(HistoryProject("org.apache:commons-io", "IO", "https://github.com/apache/commons-io.git", false))
    projectList.add(HistoryProject("org.apache:commons-jelly", "JELLY", "https://github.com/apache/commons-jelly.git", false))
    projectList.add(HistoryProject("org.apache:commons-jexl", "JEXL", "https://github.com/apache/commons-jexl.git", false))


    //NOK:
    //projectList.add(HistoryProject("tomcat_QC", "", "https://github.com/apache/tomcat.git", false)) bugzilla issues
    //projectList.add(HistoryProject("jgit_QC", "", "https://github.com/eclipse/jgit.git", false)) bugzilla issues
    //projectList.add(HistoryProject("junit_QC", "", "https://github.com/junit-team/junit4.git", false)) github issues

    /*projectList.add(HistoryProject("bcel", "BCEL", "https://github.com/apache/commons-bcel.git", true))
    projectList.add(HistoryProject("beanutils", "BEANUTILS", "https://github.com/apache/commons-beanutils.git", true))
    projectList.add(HistoryProject("bsf", "BSF", "https://github.com/apache/commons-bsf.git", true))
    projectList.add(HistoryProject("cocoon", "COCOON", "https://github.com/apache/cocoon.git", true))
    projectList.add(HistoryProject("codec", "CODEC", "https://github.com/apache/commons-codec.git", true))
    projectList.add(HistoryProject("collections", "COLLECTIONS", "https://github.com/apache/commons-collections.git", true))
    projectList.add(HistoryProject("configuration", "CONFIGURATION", "https://github.com/apache/commons-configuration.git", true))
    projectList.add(HistoryProject("daemon", "DAEMON", "https://github.com/apache/commons-daemon.git", true))
    projectList.add(HistoryProject("cli", "CLI", "https://github.com/apache/commons-cli.git", true))*/



    //mergeExtractedSameCsvFiles(projectList.map { it.sonarKey }, "correlation-faults.csv")
    mergeExtractedSameCsvFiles(projectList.map { it.sonarKey }, "fault-file-commit-summary.csv")
    return

    for (project in projectList) {
    //projectList.parallelStream().forEach { project ->

        val folderStr = getProjectFolder(project.sonarKey)
        val issuesFile = folderStr + "sonar-issues.csv"
        val measuresFile = folderStr + "measure-history.csv"
        //val measuresAndIssuesFile = folderStr + "measures-and-issues.csv"
        val jiraFaultFile = folderStr + "jira-faults.csv"
        val gitCommitFile = folderStr + "git-commits.csv"
        val faultFileCommitFile = folderStr + "fault-file-commit.csv"
        val faultsAndIssuesFile = folderStr + "faults-and-issues.csv"
        val faultsIssueCountFile = folderStr + "faults-issue-count.csv"

        makeEmptyFolder(folderStr)
        saveIssues(issuesFile, project.sonarKey, "CLOSED,OPEN", ruleKeys)

        saveMeasureHistory(measuresFile, project.sonarKey)
        //mergeMeasuresWithIssues(measuresFile, issuesFile, measuresAndIssuesFile)

        saveJiraIssues(jiraFaultFile, project.jiraKey)
        saveGitCommits(gitCommitFile, project.gitLink)

        mapFaultFileCommit(issuesFile, jiraFaultFile, gitCommitFile, measuresFile, faultFileCommitFile, project.dailyAnalysis)
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

private class HistoryProject(val sonarKey: String, val jiraKey: String, val gitLink: String, val dailyAnalysis: Boolean)
