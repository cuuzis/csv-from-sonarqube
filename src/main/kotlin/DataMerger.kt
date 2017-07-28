import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import com.opencsv.bean.CsvToBeanBuilder
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import com.opencsv.bean.StatefulBeanToCsvBuilder
import csv_model.extracted.ArchitectureSmells
import csv_model.extracted.GitCommits
import csv_model.extracted.JiraFaults
import csv_model.merged.MergedIssues
import csv_model.extracted.SonarIssues


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



    //TODO: make this a new function:
    //groupFaultsAndIssues
    val newFileName = "grouped-by-faults.csv"
    val faultKeys = mutableSetOf<String>()
    rows.mapTo(faultKeys) { it.jiraKey.orEmpty() }
    val issueKeys = mutableSetOf<String>()
    rows.mapTo(issueKeys) { it.sonarRuleKey.orEmpty() }


    val header = listOf<String>("jira-key") + issueKeys
    val dataRows = mutableListOf<List<String>>()
    dataRows.add(header)

    for (jiraKey in faultKeys) {
        val currentIssueCount = mutableMapOf<String, Int>()
        for (issueKey in issueKeys)
            currentIssueCount[issueKey] = 0

        val issuesForFault = rows.filter { it.jiraKey == jiraKey } // multiple commits for same fault?
        for (bean in issuesForFault) {
            currentIssueCount[bean.sonarRuleKey.orEmpty()] = currentIssueCount[bean.sonarRuleKey.orEmpty()]!! + 1
        }
        dataRows.add(listOf(jiraKey) + currentIssueCount.map { it.value.toString() })
    }

    // save data to file
    FileWriter(newFileName).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(dataRows.map { it.toTypedArray() })
        println("Combined data saved to $newFileName")
    }
}

/**
 * Merges architecture and code issues by components
 * Full join, ignores components without problems:
 *   Class | Arch issue | Smell
 *   ----------------------------
 *     A         1          1
 *     B         -          1
 *     C         1          -
 */
fun mergeArchitectureAndCodeIssues(outputByClass: String, outputByCycle: String, issueFile: String, cyclicDependencyFile: File) {
    println("Merging code and architecture issues")
    val startTime = System.currentTimeMillis()
    // read code smells
    val issueBeans = CsvToBeanBuilder<SonarIssues>(FileReader(File(issueFile)))
            .withType(SonarIssues::class.java).build().parse()
            .map { it as SonarIssues }

    val issueRuleKeys = sortedSetOf<String>()
    issueBeans.mapTo(issueRuleKeys) { it.ruleKey.orEmpty() }

    // read architectural smells
    val archSmellBeans = CsvToBeanBuilder<ArchitectureSmells>(FileReader(cyclicDependencyFile))
            .withType(ArchitectureSmells::class.java).build().parse()
            .map { it as ArchitectureSmells }


    val header = listOf<String>(
            "cycle-ids",
            "component",
            "arch-cycle-size",
            "arch-cycle-classes") +
            issueRuleKeys.toList()
    val rows = mutableListOf<List<String>>()
    rows.add(header)

    // add all sonar issues and match architectural smells to them
    for (sonarIssue in issueBeans.distinctBy { it.component }) {
        val archSmellOccurences = archSmellBeans
                .flatMap { it.getComponentList() }
                .count { sonarIssue.component.orEmpty().endsWith(it) }
        val archSmellComponents = archSmellBeans
                .filter { archSmell ->
                    var contains = false
                    archSmell.getComponentList().forEach { component ->
                        if (sonarIssue.component.orEmpty().endsWith(component))
                            contains = true
                    }
                    contains
                }
        val row = mutableListOf<String>(
                archSmellComponents.map { it.cycleId }.joinToString(";"),
                sonarIssue.component.orEmpty(),
                archSmellOccurences.toString(),
                archSmellComponents.count().toString())
        for (issueRuleKey in issueRuleKeys) {
            val sonarIssues = issueBeans.filter { it.component == sonarIssue.component && it.ruleKey == issueRuleKey }
                    .count()
            row.add(sonarIssues.toString())
        }
        rows.add(row)
    }

    // add architecture smells that did not have a corresponding issue in SQ
    val archSmellsWithNoIssues = archSmellBeans
            .flatMap { it.getComponentList() }
            .filter { component ->
                issueBeans.distinctBy { it.component }
                        .none { it.component.orEmpty().endsWith(component) }
            }.distinct()
    //copy pasta:
    for (noIssueComponent in archSmellsWithNoIssues) {
        val archSmellOccurences = archSmellBeans
                .flatMap { it.getComponentList() }
                .count { noIssueComponent.orEmpty().endsWith(it) }
        val archSmellComponents = archSmellBeans
                .filter { archSmell ->
                    var contains = false
                    archSmell.getComponentList().forEach { component ->
                        if (noIssueComponent.orEmpty().endsWith(component))
                            contains = true
                    }
                    contains
                }
        val row = mutableListOf(
                archSmellComponents.map { it.cycleId }.joinToString(";"),
                noIssueComponent.orEmpty(),
                archSmellOccurences.toString(),
                archSmellComponents.count().toString())
        issueRuleKeys.forEach { row.add("0") }
        rows.add(row)
    }

    FileWriter(outputByClass).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(rows.map { it.toTypedArray() })
    }


    // group by cycles
    val rowsByCycles = mutableListOf(header)
    for (archSmell in archSmellBeans) {
        val affectedClasses = archSmell.getComponentList()
        val relatedIssues = issueBeans.filter { issue ->
            var contains = false
            affectedClasses.forEach { component ->
                if (issue.component.orEmpty().endsWith(component))
                    contains = true
            }
            contains
        }
        val row = mutableListOf<String>(
                archSmell.cycleId.orEmpty(),
                affectedClasses.joinToString(";"),
                affectedClasses.count().toString(),
                affectedClasses.distinct().count().toString())
        for (issueRuleKey in issueRuleKeys) {
            val sonarIssues = relatedIssues.count { it.ruleKey == issueRuleKey }
            row.add(sonarIssues.toString())
        }
        rowsByCycles.add(row)
    }

    FileWriter(outputByCycle).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(rowsByCycles.map { it.toTypedArray() })
    }

    println("Architectural and code issues saved to $outputByClass and $outputByCycle")
    println("Merging took ${(System.currentTimeMillis()-startTime)/1000.0} seconds")
}
