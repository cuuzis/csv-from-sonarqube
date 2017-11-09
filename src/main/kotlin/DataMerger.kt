import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import com.opencsv.bean.CsvToBeanBuilder
import com.opencsv.bean.StatefulBeanToCsvBuilder
import csv_model.extracted.GitCommits
import csv_model.extracted.JiraFaults
import csv_model.merged.MergedIssues
import csv_model.extracted.SonarIssues
import gui.MainGui.Companion.logger
import sonarqube.SonarProject
import java.io.*
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit


/**
 * Merges measures, issues, commits and faults together for every sonarqube analysis
 */
fun mergeAllToAnalysis(measuresFile: String, issuesFile: String, faultFile: String, commitFile: String, combinedFile: String) {
    println("Merging measures and issues")
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

    val commitBeans = CsvToBeanBuilder<GitCommits>(FileReader(File(commitFile)))
            .withType(GitCommits::class.java).build().parse()
            .map { it as GitCommits }

    val faultBeans = CsvToBeanBuilder<JiraFaults>(FileReader(File(faultFile)))
            .withType(JiraFaults::class.java).build().parse()
            .map { it as JiraFaults }

    //val commitToFaultMap: MutableMap<JiraFaults, List<GitCommits>> = mutableMapOf()
    val faultToCommitMap: MutableMap<GitCommits, MutableList<JiraFaults>> = mutableMapOf()
    for (fault in faultBeans) {
        for (commit in getRelatedCommits(fault, commitBeans)) {
            faultToCommitMap.get(commit)?.add(fault)
            faultToCommitMap.putIfAbsent(commit, mutableListOf(fault))
        }
        //commitToFaultMap.put(fault, getRelatedCommits(fault, commitBeans))
    }

    val gitCols = listOf<String>("git-hash", "git-message", "git-committer", "git-total-committers", "git-changed-files")
    val faultCols = listOf<String>("faults", "fault-keys", "fault-creation-dates", "fault-resolution-dates", "fault-priorities")

    val header = measures[0].toList() + ruleKeys.toList() + gitCols + faultCols
    val rows = mutableListOf<List<String>>()
    rows.add(header)
    for (measure in measures.subList(1, measures.size)) {
        // add measures
        val measureDate = measure[0]
        val openedIssues = issuesByDateOpened.getOrDefault(measureDate, mutableListOf())
        for (ruleKey in openedIssues) {
            currentIssueCount[ruleKey] = currentIssueCount[ruleKey]!! + 1
        }
        val closedIssues = issuesByDateClosed.getOrDefault(measureDate, mutableListOf())
        for (ruleKey in closedIssues) {
            currentIssueCount[ruleKey] = currentIssueCount[ruleKey]!! - 1
        }


        // add git commits
        val relatedCommit = commitBeans.find { it.sonarDate == measureDate }!!
        val commitValues = listOf<String>(relatedCommit.hash.orEmpty(), relatedCommit.message.orEmpty(), relatedCommit.committer.orEmpty(),
                relatedCommit.totalCommitters.toString(), relatedCommit.changedFiles.orEmpty())


        // add jira faults
        val jiraIssues = faultToCommitMap.get(relatedCommit)
        val jiraValues =
                if (jiraIssues == null)
                    listOf("0", "", "", "", "")
                else
                    listOf(jiraIssues.count().toString(),
                            jiraIssues.map { it.jiraKey.orEmpty() }.joinToString(";"),
                            jiraIssues.map { it.creationDate.orEmpty() }.joinToString(";"),
                            jiraIssues.map { it.resolutionDate.orEmpty() }.joinToString(";"),
                            jiraIssues.map { it.priority.orEmpty() }.joinToString(";"))

        rows.add(measure.toList() + // sonarqube measures
                        currentIssueCount.values.toList().map { it.toString() } + // sonarqube issues
                commitValues + // git commit
        jiraValues)
    }

    // save data to file
    FileWriter(combinedFile).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(rows.map { it.toTypedArray() })
    }
    println("Measures and issues merged in $combinedFile")
}

/*
* Merges Jira issue (fault) with corresponding commit that closed it
*/
fun mapFaultsToIssues(commitFile: String, faultFile: String, issuesFile: String, resultFile: String) {
    val startTime = System.currentTimeMillis()
    println("Mapping jira faults to sonar issues")
    val commitBeans = CsvToBeanBuilder<GitCommits>(FileReader(File(commitFile)))
            .withType(GitCommits::class.java).build().parse()
            .map { it as GitCommits }
    val faultBeans = CsvToBeanBuilder<JiraFaults>(FileReader(File(faultFile)))
            .withType(JiraFaults::class.java).build().parse()
            .map { it as JiraFaults }
    val issueBeans = CsvToBeanBuilder<SonarIssues>(FileReader(File(issuesFile)))
            .withType(SonarIssues::class.java).build().parse()
            .map { it as SonarIssues }

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
    println("Faults with issues saved to $resultFile in ${(System.currentTimeMillis() - startTime) / 1000.0} seconds")
}

/**
 * Saves jira faults with the issue count at the time of closing
 */
fun groupIssuesByFaults(faultAndIssueFile: String, resultFile: String) {
    val faultsAndIssues = CsvToBeanBuilder<MergedIssues>(FileReader(File(faultAndIssueFile)))
            .withType(MergedIssues::class.java).build().parse()
            .map { it as MergedIssues }

    val faultKeys = mutableSetOf<String>()
    faultsAndIssues.mapTo(faultKeys) { it.jiraKey.orEmpty() }
    val issueKeys = mutableSetOf<String>()
    faultsAndIssues.mapTo(issueKeys) { it.sonarRuleKey.orEmpty() }

    val header = listOf<String>("jira-key") + issueKeys
    val dataRows = mutableListOf<List<String>>()
    dataRows.add(header)

    for (jiraKey in faultKeys) {
        val currentIssueCount = mutableMapOf<String, Int>()
        for (issueKey in issueKeys)
            currentIssueCount[issueKey] = 0

        val issuesForFault = faultsAndIssues.filter { it.jiraKey == jiraKey } // multiple commits for same fault?
        for (bean in issuesForFault) {
            currentIssueCount[bean.sonarRuleKey.orEmpty()] = currentIssueCount[bean.sonarRuleKey.orEmpty()]!! + 1
        }
        dataRows.add(listOf(jiraKey) + currentIssueCount.map { it.value.toString() })
    }

    // save data to file
    FileWriter(resultFile).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(dataRows.map { it.toTypedArray() })
    }
    println("Faults with their sonar issue count saved to $resultFile")
}

/**
 * Merges together extracted csv files for projects.
 */
fun mergeExtractedCsvFiles(projectKeys: List<String>, csvFilename: String) {
    println("Merging $csvFilename")
    // extract all column names occurring in files
    val columnNames = mutableSetOf<String>("project")
    for (projectKey in projectKeys) {
        val reader = CSVReader(FileReader(getProjectFolder(projectKey) + csvFilename))
        val header = reader.readNext()
        columnNames.addAll(header)
    }
    val result = mutableListOf<Array<String>>()
    result.add(columnNames.toTypedArray())
    for (projectKey in projectKeys) {
        // map column indexes
        val reader = CSVReader(FileReader(getProjectFolder(projectKey) + csvFilename))
        val header = reader.readNext()
        val mappedPositions = mutableMapOf<Int,Int>()
        for ((index, column) in header.withIndex())
            mappedPositions.put(index, columnNames.indexOf(column))
        // save mapped values, put "0" if column does not exist
        val csvRows: List<Array<String>> = reader.readAll()
        for (csvRow in csvRows) {
            val row = Array<String>(columnNames.size, { _ -> "0"})
            row[0] = projectKey
            for ((index, value) in csvRow.withIndex())
                row[mappedPositions[index]!!] = value
            result.add(row)
        }
    }
    // save data to file
    FileWriter(workDir + csvFilename).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(result)
    }
}

/**
 * Merges extracted csv files for projects, provided the files have the same columns.
 */
fun mergeExtractedSameCsvFiles(projectKeys: List<String>, csvFilename: String) {
    println("Merging $csvFilename")
    BufferedWriter(FileWriter(workDir + "by-project-$csvFilename")).use { bw ->
        var commonHeader: String? = null
        for (projectKey in projectKeys) {
            val allFile = readListFromFile(getProjectFolder(projectKey) + csvFilename)
            val header = allFile[0]
            val rows = allFile.subList(1,allFile.size)
            if (commonHeader == null) {
                commonHeader = header
                bw.write("\"project\"," + commonHeader)
                bw.newLine()
            }
            if (header != commonHeader) {
                throw Exception("Files to merge have different columns" +
                        "\nexpected: $commonHeader" +
                        "\n$projectKey: $header")
            }
            rows.forEach { row ->
                bw.write("\"$projectKey\"," + row)
                bw.newLine()
            }
        }
    }
}

/**
 * Creates an empty folder at the specified directory.
 * If the specified folder already exists its contents are deleted.
 */
fun makeEmptyFolder(directoryStr: String) {
    val folder = File(directoryStr)
    if (folder.exists()) {
        if (!folder.deleteRecursively())
            throw Exception("Could not delete ${folder.name} directory")
    }
    if (!folder.mkdirs())
        throw Exception("Could not create ${folder.name} directory")
}

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
 * Returns valid folder name for projectKey
 */
fun getProjectFolder(projectKey: String): String {
    return workDir + projectKey.replace("\\W".toRegex(),"-") + File.separatorChar
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
fun mapFaultFileCommitOld(issueFile: String, faultFile: String, commitFile: String, measureFile: String, outFile: String, groupByDay: Boolean) {
    println("Mapping faults, commits, sonar-issues to files")
    val commitBeans = CsvToBeanBuilder<GitCommits>(FileReader(File(commitFile)))
            .withType(GitCommits::class.java).build().parse()
            .map { it as GitCommits }
    val faultBeans = CsvToBeanBuilder<JiraFaults>(FileReader(File(faultFile)))
            .withType(JiraFaults::class.java).build().parse()
            .map { it as JiraFaults }
    val issueBeans = CsvToBeanBuilder<SonarIssues>(FileReader(File(issueFile)))
            .withType(SonarIssues::class.java).build().parse()
            .map { it as SonarIssues }
    //.filterNot { it.ruleKey == "squid:S00117" }

    val summary = mutableMapOf<String, String>()
    summary.put("total-commits", commitBeans.size.toString())
    summary.put("total-faults", faultBeans.size.toString())
    summary.put("total-sonar-issues", issueBeans.size.toString())

    val issueRuleKeys = sortedSetOf<String>()
    issueBeans.mapTo(issueRuleKeys) { it.ruleKey.orEmpty() }
    val header = mutableListOf<String>("jira-key","git-hash","git-sonar-date","sonar-component","sonar-debt")
    for (ruleKey in issueRuleKeys) {
        header.add(ruleKey + "-closed")
        header.add(ruleKey + "-opened")
        header.add(ruleKey)
    }
    val rows = mutableListOf<Array<String>>()
    for (fault in faultBeans) {
        val commits = getRelatedCommits(fault, commitBeans)
        for (commit in commits) {
            val changedFiles = commit.getChangedFilesList().filter { it.endsWith(".java") }
            val commitSonarDate = if (groupByDay) {
                OffsetDateTime.parse(commit.sonarDate.orEmpty()).toInstant()
                        .truncatedTo(ChronoUnit.DAYS)
                        .plus(1, ChronoUnit.DAYS).toString()
            } else {
                commit.sonarDate.orEmpty()
            }
            for (file in changedFiles) {
                var issues = issueBeans.filter { it.component == file }
                if (groupByDay)
                    issues = issues.map {
                        SonarIssues(
                                creationDate = if (it.creationDate.orEmpty() != "")
                                    OffsetDateTime.parse(it.creationDate.orEmpty()).toInstant()
                                            .truncatedTo(ChronoUnit.DAYS).toString()
                                else
                                    "",
                                updateDate = if (it.updateDate.orEmpty() != "")
                                    OffsetDateTime.parse(it.updateDate.orEmpty()).toInstant()
                                            .truncatedTo(ChronoUnit.DAYS).toString()
                                else
                                    "",
                                component = it.component,
                                effort = it.effort,
                                ruleKey = it.ruleKey)
                    }
                //val issuesClosedBefore = issues.filter { it.updateDate.orEmpty() < commitSonarDate }
                //val issuesOpenedAfter = issues.filter { it.creationDate.orEmpty() > commitSonarDate }
                val issuesOpenedAt = issues.filter { it.creationDate.orEmpty() == commitSonarDate }
                val issuesClosedAt = issues.filter { it.updateDate.orEmpty() == commitSonarDate }
                val issuesActiveAt = issues.filter { it.creationDate.orEmpty() <= commitSonarDate && (it.updateDate.orEmpty().isEmpty() || it.updateDate.orEmpty() > commitSonarDate) }
                val technicalDebt = issuesActiveAt.sumBy { it.effort!! }
                val row = mutableListOf<String>(
                        fault.jiraKey.orEmpty(), commit.hash.orEmpty(), commitSonarDate, file, technicalDebt.toString())
                for (ruleKey in issueRuleKeys) {
                    val issuesForRuleClosed = issuesClosedAt.count { it.ruleKey.orEmpty() == ruleKey}
                    val issuesForRuleOpened = issuesOpenedAt.count { it.ruleKey.orEmpty() == ruleKey}
                    val issuesForRule = issuesActiveAt.count { it.ruleKey.orEmpty() == ruleKey}
                    row.add(issuesForRuleClosed.toString())
                    row.add(issuesForRuleOpened.toString())
                    row.add(issuesForRule.toString())
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
    println("Mapped faults, commits, issues and files to saved to $outFile")

    summary.put("mapped-commits", rows.distinctBy { it[1] }.count().toString())
    summary.put("mapped-faults", rows.distinctBy { it[0] }.count().toString() )
    summary.put("analysis", (readListFromFile(measureFile).size - 1).toString())
    //summary.put("analysis-with-closed-faults", (readListFromFile(measureFile).size - 1).toString())
    summary.put("analysis-first-date", issueBeans.minBy { it.creationDate.orEmpty() }!!.creationDate!!)
    summary.put("analysis-last-date", issueBeans.maxBy { it.creationDate.orEmpty() }!!.creationDate!!)

    groupByFileOld(header.toTypedArray(), rows, outFile, summary)
}

/**
 * Aggregates faults, commits and issues, grouping them by files
 */
private fun groupByFileOld(inputHeader: Array<String>, inputRows: MutableList<Array<String>>, outFile: String, summary: MutableMap<String, String>) {
    println("Grouping faults, commits, issues by files")
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

    val fileName = outFile.replace(".csv", "-grouped.csv")
    FileWriter(fileName).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeNext(header.toTypedArray())
        csvWriter.writeAll(rows)
    }
    println("Grouped faults, commits, issues saved to $fileName")
    summary.put("files-affected", rows.size.toString())
    println(summary)
    FileWriter(fileName.replace("-grouped.csv","-summary.csv")).use { fw ->
        val keys = summary.keys.toString()
        val values = summary.values.toString()
        fw.write(keys.substring(1, keys.lastIndex))
        fw.write(System.lineSeparator())
        fw.write(values.substring(1, values.lastIndex))
    }
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
    //.filterNot { it.ruleKey == "squid:S00117" }

    val issueRuleKeys = sortedSetOf<String>()
    issueBeans.mapTo(issueRuleKeys) { it.ruleKey.orEmpty() }
    val header = mutableListOf<String>("jira-key","git-hash","git-sonar-date","sonar-component","sonar-debt")
    for (ruleKey in issueRuleKeys) {
        header.add(ruleKey + "-closed")
        header.add(ruleKey + "-opened")
        header.add(ruleKey)
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
                    val issuesForRuleClosed = issuesClosedAt.count { it.ruleKey.orEmpty() == ruleKey}
                    val issuesForRuleOpened = issuesOpenedAt.count { it.ruleKey.orEmpty() == ruleKey}
                    val issuesForRule = issuesActiveAt.count { it.ruleKey.orEmpty() == ruleKey}
                    row.add(issuesForRuleClosed.toString())
                    row.add(issuesForRuleOpened.toString())
                    row.add(issuesForRule.toString())
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
    //groupByFileOld(header.toTypedArray(), rows, outFile, summary)
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
 * Saves summary for selected projects in "summary.csv"
 */
fun saveSummary(sonarProjects: List<SonarProject>): String {
    val fileName = "summary.csv"
    mergeProjectFiles(sonarProjects, fileName)
    return fileName
}


private data class Aggregations(
        val sum: Map<String, Int>,
        val avg: Map<String, Double>,
        val min: Map<String, Int>,
        val max: Map<String, Int>)