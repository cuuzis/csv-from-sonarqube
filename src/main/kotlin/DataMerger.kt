import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import com.opencsv.bean.CsvToBeanBuilder
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import com.opencsv.bean.StatefulBeanToCsvBuilder


/*
Merges the list of issues with the measure history,
grouping issues together by date and rule key
 */
fun mergeMeasuresWithIssues(measuresFile: String, issuesFile: String, combinedFile: String) {
    val ruleKeys = mutableSetOf<String>()
    val issuesByDateOpened = mutableMapOf<String, MutableList<String>>()
    val issuesByDateClosed = mutableMapOf<String, MutableList<String>>()

    val issueBeans = CsvToBeanBuilder<SonarIssues>(FileReader(File(issuesFile)))
            .withType(SonarIssues::class.java).build().parse()
            .map { it as SonarIssues }

    for (issue in issueBeans) {
        ruleKeys.add(issue.ruleKey.toString())
        issuesByDateOpened.computeIfAbsent(issue.creationDate.toString(), { _ -> mutableListOf() })
                .add(issue.ruleKey.toString())
        issuesByDateClosed.computeIfAbsent(issue.updateDate.toString(), { _ -> mutableListOf() })
                .add(issue.ruleKey.toString())
    }

    val currentIssueCount = mutableMapOf<String, Int>()
    for (ruleKey in ruleKeys)
        currentIssueCount[ruleKey] = 0

    val reader = CSVReader(FileReader(File(measuresFile)))
    val measures = reader.readAll()

    val header = measures[0].toList() + ruleKeys.toList()
    val rows = mutableListOf<List<String>>()
    rows.add(header)

    for (measure in measures.subList(1, measures.size)) {
        val measureDate = measure[0]
        val openedIssues = issuesByDateOpened.getOrDefault(measureDate, mutableListOf())
        for (ruleKey in openedIssues) {
            currentIssueCount[ruleKey] = currentIssueCount[ruleKey]!! + 1
        }
        val closedIssues = issuesByDateClosed.getOrDefault(measureDate, mutableListOf())
        for (ruleKey in closedIssues) {
            currentIssueCount[ruleKey] = currentIssueCount[ruleKey]!! - 1
        }
        rows.add(measure.toList() + currentIssueCount.values.toList().map { it.toString() })
    }

    // save data to file
    FileWriter(combinedFile).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(rows.map { it.toTypedArray() })
        println("Combined data saved to $combinedFile")
    }
}

/*
* Merges Jira issue (fault) with corresponding commit that closed it
*/
fun mergeFaultsAndSmells(commitFile: String, faultFile: String, issuesFile: String, resultFile: String) {
    val startTime = System.currentTimeMillis()
    val commitBeans = CsvToBeanBuilder<GitCommits>(FileReader(File(commitFile)))
            .withType(GitCommits::class.java).build().parse()
            .map { it as GitCommits }
    val faultBeans = CsvToBeanBuilder<JiraFaults>(FileReader(File(faultFile)))
            .withType(JiraFaults::class.java).build().parse()
            .map { it as JiraFaults }
    val issueBeans = CsvToBeanBuilder<SonarIssues>(FileReader(File(issuesFile)))
            .withType(SonarIssues::class.java).build().parse()
            .map { it as SonarIssues }
    println("Files parsed in ${(System.currentTimeMillis() - startTime)/1000.0}s")

    val rows = mutableListOf<MergedIssues>()

    for (commitBean in commitBeans) {
        // find relevant jira issues
        val faultsSolvedByCommit = faultBeans.filter {
            val key = it.jiraKey.toString().toLowerCase()
            val commitMessage = commitBean.message.orEmpty().toLowerCase()
            commitMessage.contains("(\\W$key\\W|^$key\\W|\\W$key\$)".toRegex())
        }
        // find smells closed with the same commit
        //if (faultsSolvedByCommit.isNotEmpty()) {
        for (faultBean in faultsSolvedByCommit) {
            val issuesSolvedByCommit = issueBeans.filter {
                it.updateDate == commitBean.sonarDate
                && it.creationDate.toString() < faultBean.creationDate.toString() // needs && SZZ instead
            }
            for (issueBean in issuesSolvedByCommit) {
                rows.add(MergedIssues(
                        gitHash = commitBean.hash,
                        gitMessage = commitBean.message,
                        gitCommitter = commitBean.committer,
                        jiraKey = faultBean.jiraKey,
                        sonarRuleKey = issueBean.ruleKey,
                        sonarCreationDate = issueBean.creationDate,
                        sonarUpdateDate = issueBean.updateDate,
                        sonarComponent = issueBean.component))
            }
        }
    }

    val writer = FileWriter(resultFile)
    val beanToCsv = StatefulBeanToCsvBuilder<MergedIssues>(writer).build()
    rows.forEach { row -> beanToCsv.write(row) }
    writer.close()

    println("Merged in ${(System.currentTimeMillis() - startTime)/1000.0}s")
    println("Merged result saved to $resultFile")
}



/*
    //val resultFile = projectKey.replace("\\W".toRegex(),"-") + ".csv"
    BufferedWriter(FileWriter(resultFile)).use { bw ->
        val header = measuresAndIssues[0].joinToString(",") + ",commit-date,commit-hash,committer,total-committers,faults-closed,open-faults,closed-faults"
        bw.write(header)
        bw.newLine()
        for (measureRow in measuresAndIssues.subList(1, measuresAndIssues.size)) {
            bw.write(measureRow.joinToString(","))
            // find corresponding commit
            val measureDate = measureRow[0]
            var foundCommit = false
            for (commit in gitCommits) {
                val commitSonarDate = commit[1]
                if (commitSonarDate == measureDate) {
                    foundCommit = true
                    val commitDate = commit[0]
                    val commitHash = commit[2]
                    val commitMessage = commit[3]
                    val committer = commit[4]
                    val totalCommitters = commit[5]
                    bw.write(",$commitDate,$commitHash,$committer,$totalCommitters")

                    // add jira info to commit
                    val jiraKeys = mutableListOf<String>()
                    val jiraOpenFaults = mutableListOf<String>()
                    val jiraClosedFaults = mutableListOf<String>()
                    jiraIssues.subList(1, jiraIssues.size)
                            .filter {
                                val key = it[0].toLowerCase()
                                commitMessage.toLowerCase().contains("(\\W$key\\W|^$key\\W|\\W$key\$)".toRegex())
                            }
                            .forEach {
                                jiraKeys.add(it[0])
                                jiraOpenFaults.add(it[6])
                                jiraClosedFaults.add(it[7])
                            }
                    bw.write(",")
                    bw.write(jiraKeys.joinToString(";"))
                    bw.write(",")
                    bw.write(jiraOpenFaults.joinToString(";"))
                    bw.write(",")
                    bw.write(jiraClosedFaults.joinToString(";"))

                    break
                }
            }
            if (!foundCommit)
                throw Exception("Git commit not found for sonarqube scan at $measureDate")
            bw.newLine()
        }
    }
    println("Results saved to $resultFile")

        */