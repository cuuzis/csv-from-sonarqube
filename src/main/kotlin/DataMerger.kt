import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import com.opencsv.bean.CsvToBeanBuilder
import csv_model.extracted.GitCommits
import csv_model.extracted.JiraFaults
import csv_model.extracted.SonarIssues
import csv_model.merged.Correlations
import gui.MainGui.Companion.logger
import sonarqube.SonarProject
import sonarqube.getRuleKeys
import sonarqube.getTags
import java.io.*


/**
 * Returns the requested architecture smell file for a project
 */
fun findArchitectureSmellFile(projectKey: String, fileName: String): File {
    val architectureRoot = File("architecture-smells-arcan/")
    val architectureFolder = architectureRoot.listFiles().find {
        it.toString().toLowerCase().startsWith(
                architectureRoot.name + File.separatorChar + projectKey.removePrefix("QC:").toLowerCase())
    }!!
    return architectureFolder.listFiles().find { it.name == fileName}!!
}

/**
 * Reads each line from file into a string list
 */
fun readListFromFile(filename: String): List<String> {
    val result = mutableListOf<String>()
    val file = File(filename)
    try {
        BufferedReader(FileReader(file)).use { br ->
            do {
                val line = br.readLine()
                if (line != null)
                    result.add(line)
            } while (line != null)
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
    return result
}

/**
 * For each fault: finds commits that were fixing it and the changed .java files with their measures
 */
fun mapFaultFileCommit(sonarProject: SonarProject): String {
    val issueFile = sonarProject.getProjectFolder() + "sonar-issues.csv"
    val faultFile = sonarProject.getProjectFolder() + "jira-faults.csv"
    val commitFile = sonarProject.getProjectFolder() + "git-commits.csv"
    val measureFile = sonarProject.getProjectFolder() + "measure-history.csv"
    val outFile =  sonarProject.getProjectFolder() + "fault-file-commit.csv"
    logger.info("Mapping faults, commits, sonar-issues to files")
    val commitBeans = CsvToBeanBuilder<GitCommits>(FileReader(File(commitFile)))
            .withType(GitCommits::class.java).build().parse()
            .map { it as GitCommits }
    val faultBeans = CsvToBeanBuilder<JiraFaults>(FileReader(File(faultFile)))
            .withType(JiraFaults::class.java).build().parse()
            .map { it as JiraFaults }
    val issueBeans = CsvToBeanBuilder<SonarIssues>(FileReader(File(issueFile)))
            .withType(SonarIssues::class.java).build().parse()
            .map { it as SonarIssues }

    // group by tags
    val builtInTags = listOf<String>(
            "brain-overload",  // there is too much to keep in your head at one time
            "bad-practice", // the code likely works as designed, but the way it was designed is widely recognized as being a bad idea.
            // bug - something is wrong and it will probably affect production
            "cert", // relates to a rule in a CERT standard. There are currently three CERT standards: C, C++, and Java. Many of these rules are not language-specific, but are good programming practices. That's why you'll see this tag on non-C/C++, Java rules.
            "clumsy", // extra steps are used to accomplish something that could be done more clearly and concisely. (E.G. calling .toString() on a String).
            "confusing", // will take maintainers longer to understand than is really justified by what the code actually does
            "convention", // coding convention - typically formatting, naming, whitespace...
            "cwe", // relates to a rule in the Common Weakness Enumeration. For more on CWE in SonarQube language plugins, and on security-related rules in general, see Security-related rules.
            "design", // there is something questionable about the design of the code
            "lock-in", //environment-specific features are used
            "misra", // relates to a rule in one of the MISRA standards.
            "pitfall", // nothing is wrong yet, but something could go wrong in the future; a trap has been set for the next guy, & he'll probably fall into it and screw up the code.
            // security - relates to the security of an application.
            "suspicious", // it's not guaranteed that this is a bug, but it looks suspiciously like one. At the very least, the code should be re-examined & likely refactored for clarity.
            "unpredictable", // the code may work fine under current conditions, but may fail erratically if conditions change.
            "unused", // unused code, E.G. a private variable that is never used.
            "user-experience", // there's nothing technically wrong with your code, but it may make some or all of your users hate you.

            // multiple tags start with these:
            "owasp", // relates to a rule in the OWASP Top Ten security standards.
            "sans-top25" // relates to the SANS Top 25 Coding Errors, which are security-related. Note that the SANS Top 25 list is pulled directly from the CWE.
    )
    val serverTags = getTags(sonarProject.sonarServer.serverAddress)
    val tagRules = mutableMapOf<String,List<String>>()
    for (builtInTag in builtInTags) {
        val subTags = serverTags.filter {
            it == builtInTag || (it.startsWith(builtInTag) && (builtInTag == "owasp" || builtInTag == "sans-top25"))
        }.joinToString(",")
        val rulesForTag = getRuleKeys(sonarProject.sonarServer.serverAddress, "&tags=$subTags")
        tagRules.put(builtInTag, rulesForTag)
    }

    val issueRuleKeys = sortedSetOf<String>()
    issueBeans.mapTo(issueRuleKeys) { it.ruleKey.orEmpty() }
    val header = mutableListOf<String>("jira-key","git-hash","git-sonar-date","sonar-component","sonar-debt")
    for (ruleKey in issueRuleKeys) {
        header.add(ruleKey + "-closed")
        header.add(ruleKey + "-opened")
        header.add(ruleKey)
        header.add(ruleKey + "-debt-closed")
        header.add(ruleKey + "-debt-opened")
        header.add(ruleKey + "-debt")
    }
    //group by tags
    for (tag in builtInTags) {
        header.add(tag + "-closed")
        header.add(tag + "-opened")
        header.add(tag)
        header.add(tag + "-debt-closed")
        header.add(tag + "-debt-opened")
        header.add(tag + "-debt")
    }
    val rows = mutableListOf<Array<String>>()
    for (fault in faultBeans) {
        val commits = getRelatedCommits(fault, commitBeans)
        for (commit in commits) {
            val changedFiles = commit.getChangedFilesList().filter { it.endsWith(".java") }
            for (file in changedFiles) {
                val issues = issueBeans.filter { it.component == file }
                val issuesOpenedAt = issues.filter { it.creationDate.orEmpty() == commit.sonarDate.orEmpty() }
                val issuesClosedAt = issues.filter { it.updateDate.orEmpty() == commit.sonarDate.orEmpty() }
                val issuesActiveAt = issues.filter { it.creationDate.orEmpty() <= commit.sonarDate.orEmpty()
                        && (it.updateDate.orEmpty().isEmpty() || it.updateDate.orEmpty() > commit.sonarDate.orEmpty()) }
                val technicalDebt = issuesActiveAt.sumBy { it.effort!! }
                val row = mutableListOf<String>(
                        fault.jiraKey.orEmpty(), commit.hash.orEmpty(), commit.sonarDate.orEmpty(), file, technicalDebt.toString())
                for (ruleKey in issueRuleKeys) {
                    val issuesForRuleClosed = issuesClosedAt.filter { it.ruleKey.orEmpty() == ruleKey}
                    val issuesForRuleOpened = issuesOpenedAt.filter { it.ruleKey.orEmpty() == ruleKey}
                    val issuesForRule = issuesActiveAt.filter { it.ruleKey.orEmpty() == ruleKey}
                    val effortForRuleClosed = issuesForRuleClosed.sumBy { it.effort!! }
                    val effortForRuleOpened = issuesForRuleOpened.sumBy { it.effort!! }
                    val effortForRule = issuesForRule.sumBy { it.effort!! }
                    row.add(issuesForRuleClosed.count().toString())
                    row.add(issuesForRuleOpened.count().toString())
                    row.add(issuesForRule.count().toString())
                    row.add(effortForRuleClosed.toString())
                    row.add(effortForRuleOpened.toString())
                    row.add(effortForRule.toString())
                }
                // group by tags
                for (tag in builtInTags) {
                    val issuesForTagClosed = issuesClosedAt.filter { it.ruleKey.orEmpty() in tagRules[tag].orEmpty() }
                    val issuesForTagOpened = issuesOpenedAt.filter { it.ruleKey.orEmpty() in tagRules[tag].orEmpty() }
                    val issuesForTag = issuesActiveAt.filter { it.ruleKey.orEmpty() in tagRules[tag].orEmpty() }
                    val effortForTagClosed = issuesForTagClosed.sumBy { it.effort!! }
                    val effortForTagOpened = issuesForTagOpened.sumBy { it.effort!! }
                    val effortForTag = issuesForTag.sumBy { it.effort!! }
                    row.add(issuesForTagClosed.count().toString())
                    row.add(issuesForTagOpened.count().toString())
                    row.add(issuesForTag.count().toString())
                    row.add(effortForTagClosed.toString())
                    row.add(effortForTagOpened.toString())
                    row.add(effortForTag.toString())
                }
                rows.add(row.toTypedArray())
            }
        }
    }
    FileWriter(outFile).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeNext(header.toTypedArray())
        csvWriter.writeAll(rows)
    }
    logger.info("Mapped faults, commits, issues and files to saved to $outFile")


    updateSummary(sonarProject, "total-commits", commitBeans.size.toString())
    updateSummary(sonarProject, "total-faults", faultBeans.size.toString())
    updateSummary(sonarProject, "total-sonar-issues", issueBeans.size.toString())

    updateSummary(sonarProject, "mapped-commits", rows.distinctBy { it[1] }.count().toString())
    updateSummary(sonarProject, "mapped-faults", rows.distinctBy { it[0] }.count().toString() )
    updateSummary(sonarProject, "analysis", (readListFromFile(measureFile).size - 1).toString())
    //updateSummary(sonarProject, "analysis-with-closed-faults", (readListFromFile(measureFile).size - 1).toString())
    updateSummary(sonarProject, "analysis-first-date", issueBeans.minBy { it.creationDate.orEmpty() }!!.creationDate!!)
    updateSummary(sonarProject, "analysis-last-date", issueBeans.maxBy { it.creationDate.orEmpty() }!!.creationDate!!)

    return outFile
}

/**
 * Aggregates faults, commits and issues, grouping them by files
 */
fun groupByFile(sonarProject: SonarProject): String {

    val faultFileCommitFile = sonarProject.getProjectFolder() + "fault-file-commit.csv"
    val reader = CSVReader(FileReader(faultFileCommitFile))
    val inputHeader = reader.readNext()
    val inputRows = reader.readAll()
    logger.info("Grouping faults, commits, issues by files")
    val header = mutableListOf<String>("sonar-component", "commit-fault-count", "jira-faults", "fault-related-commits")

    val componentIdx = inputHeader.indexOf("sonar-component")
    val jiraKeyIdx = inputHeader.indexOf("jira-key")
    val gitHashIdx = inputHeader.indexOf("git-hash")
    val sonarDebtIdx = inputHeader.indexOf("sonar-debt")
    for (idx in sonarDebtIdx..inputHeader.lastIndex) {
        header.add(inputHeader[idx] + "-sum")
        header.add(inputHeader[idx] + "-min")
        header.add(inputHeader[idx] + "-max")
        header.add(inputHeader[idx] + "-avg")
    }

    val rowsGroupedByFile = inputRows.groupBy { it[componentIdx] }
    val rowCount = rowsGroupedByFile.mapValues { it.value.size }
    val faultCount = rowsGroupedByFile.mapValues { it.value.distinctBy { it[jiraKeyIdx] }.count() }
    val commitCount = rowsGroupedByFile.mapValues { it.value.distinctBy { it[gitHashIdx] }.count() }
    val issueAggregations = mutableMapOf<Int, Aggregations>()
    for (idx in sonarDebtIdx..inputHeader.lastIndex) {
        val sum = rowsGroupedByFile.mapValues { it.value.sumBy { it[idx].toInt() } }
        val min = rowsGroupedByFile.mapValues { it.value.minBy { it[idx].toInt() }!![idx].toInt() }
        val max = rowsGroupedByFile.mapValues { it.value.maxBy { it[idx].toInt() }!![idx].toInt() }
        val avg = rowsGroupedByFile.mapValues { it.value.sumBy { it[idx].toInt() } / it.value.size.toDouble() }
        issueAggregations.put(idx, Aggregations(sum = sum, min = min, max = max, avg = avg))
    }

    val rows = mutableListOf<Array<String>>()
    for (componentKey in rowsGroupedByFile.keys) {
        val row = mutableListOf<String>()
        row.add(componentKey)
        row.add(rowCount[componentKey].toString())
        row.add(faultCount[componentKey].toString())
        row.add(commitCount[componentKey].toString())
        for (idx in sonarDebtIdx..inputHeader.lastIndex) {
            row.add(issueAggregations[idx]!!.sum[componentKey].toString())
            row.add(issueAggregations[idx]!!.min[componentKey].toString())
            row.add(issueAggregations[idx]!!.max[componentKey].toString())
            row.add(issueAggregations[idx]!!.avg[componentKey].toString())
        }
        rows.add(row.toTypedArray())
    }

    val fileName =  sonarProject.getProjectFolder() + "fault-file-commit-grouped.csv"
    FileWriter(fileName).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeNext(header.toTypedArray())
        csvWriter.writeAll(rows)
    }
    logger.info("Grouped faults, commits, issues saved to $fileName")
    updateSummary(sonarProject, "files-affected", rows.size.toString())
    return fileName
}

private fun  getRelatedCommits(fault: JiraFaults, commitBeans: List<GitCommits>): List<GitCommits> {
    return commitBeans.filter {
        val commitMessage = it.message.orEmpty().toLowerCase()
        val faultKey = fault.jiraKey.toString().toLowerCase()
        commitMessage.contains("(\\W$faultKey\\W|^$faultKey\\W|\\W$faultKey\$)".toRegex())
    }
}

/**
 * Updates key-value pairs in "summary.csv" for project
 */
private fun updateSummary(sonarProject: SonarProject, key: String, value: String) {
    val summaryFile = sonarProject.getProjectFolder() + "summary.csv"
    val summary = mutableMapOf<String, String>()

    // read previous values
    if (File(summaryFile).exists()) {
        val reader = CSVReader(FileReader(summaryFile))
        val keys = reader.readNext().toList()
        val values = reader.readNext().toList()
        keys.withIndex().forEach { (idx, key) ->
            summary.put(key, values[idx])
        }
    }

    // update key-value pairs
    summary.put(key, value)

    // save values to file
    FileWriter(summaryFile).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeNext(summary.keys.toTypedArray())
        csvWriter.writeNext(summary.values.toTypedArray())
    }
}

/**
 * Merges together extracted csv files for projects.
 */
fun mergeProjectFiles(sonarProjects: List<SonarProject>, csvFilename: String) {
    logger.info("Merging $csvFilename")
    // extract all column names occurring in files
    val columnNames = mutableSetOf<String>("project")
    for (project in sonarProjects) {
        val reader = CSVReader(FileReader(project.getProjectFolder() + csvFilename))
        val header = reader.readNext()
        columnNames.addAll(header)
    }
    val result = mutableListOf<Array<String>>()
    result.add(columnNames.toTypedArray())
    for (project in sonarProjects) {
        // map column indexes
        val reader = CSVReader(FileReader(project.getProjectFolder() + csvFilename))
        val header = reader.readNext()
        val mappedPositions = mutableMapOf<Int,Int>()
        for ((index, column) in header.withIndex())
            mappedPositions.put(index, columnNames.indexOf(column))
        // save mapped values, put "0" if column does not exist
        val csvRows: List<Array<String>> = reader.readAll()
        for (csvRow in csvRows) {
            val row = Array<String>(columnNames.size, { _ -> "0"})
            row[0] = project.getKey()
            for ((index, value) in csvRow.withIndex())
                row[mappedPositions[index]!!] = value
            result.add(row)
        }
    }
    // save data to file
    FileWriter(csvFilename).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(result)
    }
}

/**
 * Filters and counts issue correlations, finding rows with kendallPvalue < 0.05 and kendallCorrelationTau > 0.5
 */
private fun saveUsefulCorrelations(project: SonarProject) {
    val reader = FileReader(project.getProjectFolder() + "correlation-commits.csv")
    val correlationBeans = CsvToBeanBuilder<Correlations>(reader)
            .withType(Correlations::class.java).build().parse()
            .map { it as Correlations }

    val header = arrayOf("measureName","mannWhitneyPvalue","shapiroWilkPvalue","kendallPvalue",
            "kendallTau","pearsonPvalue","pearsonCor")
    val result = correlationBeans.filter {
        it.kendallPvalue?.toDoubleOrNull() != null && it.kendallPvalue.toDouble() < 0.05
        && it.kendallTau?.toDoubleOrNull() != null && it.kendallTau.toDouble() > 0.5
    }.map {
        arrayOf(it.measureName, it.mannWhitneyPvalue, it.shapiroWilkPvalue, it.kendallPvalue,
                it.kendallTau, it.pearsonPvalue, it.pearsonCor)
    }

    // save data to file
    FileWriter(project.getProjectFolder() + "correlation-summary.csv").use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeNext(header)
        csvWriter.writeAll(result)
    }
}

/**
 * counts the number of projects with correlations for faults and measures
 */
private fun countCorrelations() {
    val reader = FileReader("correlation-summary.csv")
    val correlationBeans = CsvToBeanBuilder<Correlations>(reader)
            .withType(Correlations::class.java).build().parse()
            .map { it as Correlations }
    val issueKeys = mutableSetOf<String>()
    correlationBeans.mapTo(issueKeys) { it.measureName.orEmpty() }
    val rows = mutableListOf<Array<String>>()
    for (issue in issueKeys) {
        val infectedProjects = correlationBeans.filter { it.measureName == issue }
        val measurableOccurrences = infectedProjects.filter { it.kendallPvalue?.toDoubleOrNull() != null && it.kendallPvalue.toDouble() < 0.05 }
        val correlation05Occurrences = measurableOccurrences.filter { it.kendallTau?.toDoubleOrNull() != null && it.kendallTau.toDouble() > 0.5 }
        val correlation06Occurrences = measurableOccurrences.count { it.kendallTau?.toDoubleOrNull() != null && it.kendallTau.toDouble() > 0.6 }
        rows.add(arrayOf(
                issue,
                infectedProjects.count().toString(),
                measurableOccurrences.count().toString(),
                correlation05Occurrences.count().toString(),
                correlation06Occurrences.toString(),
                correlation05Occurrences.joinToString(";") { it.project.orEmpty() }
        ))
    }
    val header: Array<String> = arrayOf<String>("measure", "#infectedProjects", "#pvalue005", "#correlation05", "#correlation06", "correlation05")
    val result: List<Array<String>> = rows // sorted by:       correlation06      correlation05      pvalue005          infectedProjects
            .sortedWith(compareBy({ it[4].toInt() }, { it[3].toInt() }, { it[2].toInt() }, { it[1].toInt() }))
            .plus(element = header)
            .reversed()

    // save data to file
    FileWriter("correlation-count.csv").use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(result)
    }
}

/**
 * Saves summary for selected projects in "summary.csv"
 */
fun saveSummary(sonarProjects: List<SonarProject>): String {
    val projectSummary = "summary.csv"
    val correlationsSummary = "correlation-commits.csv"
    val correlationsFiltered = "correlation-summary.csv"
    for (project in sonarProjects) {
        saveUsefulCorrelations(project)
    }
    mergeProjectFiles(sonarProjects, projectSummary)
    mergeProjectFiles(sonarProjects, correlationsSummary)
    mergeProjectFiles(sonarProjects, correlationsFiltered)
    countCorrelations()
    return "$projectSummary, $correlationsSummary, $correlationsFiltered"
}


private data class Aggregations(
        val sum: Map<String, Int>,
        val avg: Map<String, Double>,
        val min: Map<String, Int>,
        val max: Map<String, Int>)