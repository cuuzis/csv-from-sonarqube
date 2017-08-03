import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import com.opencsv.bean.CsvToBeanBuilder
import com.opencsv.bean.StatefulBeanToCsvBuilder
import csv_model.architecture_smells.ArchClassCycles
import csv_model.architecture_smells.ArchMultiA
import csv_model.extracted.GitCommits
import csv_model.extracted.JiraFaults
import csv_model.merged.MergedIssues
import csv_model.extracted.SonarIssues
import java.io.*


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
 * Merges architecture and code issues by components
 * Full join, ignores components without problems:
 *   Class | Arch issue | Smell
 *   ----------------------------
 *     A         1          1
 *     B         -          1
 *     C         1          -
 */
fun mapIssuesToCyclicDependencies(projectKeys: List<String>) {
    for (projectKey in projectKeys) {
        val folderStr = getProjectFolder(projectKey)
        val archCycleSmellFile = findArchitectureSmellFile(projectKey, "classCyclesShapeTable.csv")
        mergeIssuesWithCyclicDependencies(
                outputByClass = folderStr + "cycles-issues-by-class.csv",
                outputByCycle = folderStr + "cycles-issues-by-cycle.csv",
                issueFile = folderStr + "current-issues.csv",
                cyclicDependencyFile = archCycleSmellFile)
    }
}

private fun mergeIssuesWithCyclicDependencies(outputByClass: String, outputByCycle: String, issueFile: String, cyclicDependencyFile: File) {
    println("Merging cyclic dependencies and sonar issues")
    val startTime = System.currentTimeMillis()
    // read code smells
    val issueBeans = CsvToBeanBuilder<SonarIssues>(FileReader(File(issueFile)))
            .withType(SonarIssues::class.java).build().parse()
            .map { it as SonarIssues }

    val issueRuleKeys = sortedSetOf<String>()
    issueBeans.mapTo(issueRuleKeys) { it.ruleKey.orEmpty() }

    // read architectural smells
    val archSmellBeans = CsvToBeanBuilder<ArchClassCycles>(FileReader(cyclicDependencyFile))
            .withType(ArchClassCycles::class.java).build().parse()
            .map { it as ArchClassCycles }


    val header = listOf<String>(
            "cycle-ids",
            "component",
            "arch-cycle-size",
            "arch-cycle-classes",
            "arch-cycle-exists") +
            issueRuleKeys.toList()
    val rows = mutableListOf<List<String>>()
    rows.add(header)

    // add all sonar issues and match architectural smells to them
    for (sonarIssue in issueBeans.distinctBy { it.component }) {
        val archSmellOccurrences = archSmellBeans
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
        val archSmellExists =
                if (archSmellOccurrences == 0 && archSmellComponents.count() == 0)
                    "0"
                else
                    "1"
        val row = mutableListOf<String>(
                archSmellComponents.map { it.cycleId }.joinToString(";"),
                sonarIssue.component.orEmpty(),
                archSmellOccurrences.toString(),
                archSmellComponents.count().toString(),
                archSmellExists)
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
        val archSmellOccurrences = archSmellBeans
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
                archSmellOccurrences.toString(),
                archSmellComponents.count().toString(),
                "0")
        issueRuleKeys.forEach { row.add("0") }
        rows.add(row)
    }

    FileWriter(outputByClass).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(rows.map { it.toTypedArray() })
    }


    // group by cycles
    val headerByCycles = listOf<String>(
            "cycle-ids",
            "component",
            "arch-cycle-size",
            "arch-cycle-classes") +
            issueRuleKeys.toList()
    val rowsByCycles = mutableListOf(headerByCycles)
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

    println("Cyclic dependencies and sonar issues saved to $outputByClass and $outputByCycle")
    println("Merging took ${(System.currentTimeMillis()-startTime)/1000.0} seconds")
}

/**
 * Merges architecture MAS and code issues by packages
 */
fun mapIssuesToMAS(projectKeys: List<String>) {
    for (projectKey in projectKeys) {
        val folderStr = getProjectFolder(projectKey)
        val archMasFile = findArchitectureSmellFile(projectKey, "mas.csv")
        mergeIssuesWithMAS(
                outputFile = folderStr + "mas-issues-by-package.csv",
                issueFile = folderStr + "current-issues.csv",
                masFile = archMasFile)
    }
}

private fun mergeIssuesWithMAS(outputFile: String, issueFile: String, masFile: File) {
    println("Merging MAS and sonar issues")
    val startTime = System.currentTimeMillis()
    // read code smells
    val issueBeans = CsvToBeanBuilder<SonarIssues>(FileReader(File(issueFile)))
            .withType(SonarIssues::class.java).build().parse()
            .map { it as SonarIssues }

    val issueRuleKeys = sortedSetOf<String>()
    issueBeans.mapTo(issueRuleKeys) { it.ruleKey.orEmpty() }

    // read architectural smells
    val archSmellBeans = CsvToBeanBuilder<ArchMultiA>(FileReader(masFile))
            .withType(ArchMultiA::class.java).build().parse()
            .map { it as ArchMultiA }


    val header = listOf<String>(
            "package",
            "ud",
            "hl",
            "cd",
            "ud-hl-cd-exists") +
            issueRuleKeys.toList()
    val rows = mutableListOf<List<String>>()
    rows.add(header)

    // group by mas
    val validArchSmells = archSmellBeans.filterNot {
        val packageName = it.name.orEmpty().replaceAfter("\$", "").replace("\$","")
        packageName.startsWith("java.") || packageName.startsWith("javax.") || packageName.length < 3
    }
    for (archSmell in validArchSmells) {
        val relatedIssues = issueBeans.filter { issue ->
            val packageStr = archSmell.name.orEmpty().replace(".","/")
            issue.component.orEmpty().contains(packageStr)
        }
        val archSmellExists =
                if (archSmell.ud.orEmpty() == "0" && archSmell.hl.orEmpty() == "0" && archSmell.cd.orEmpty() == "0")
                    "0"
                else
                    "1"
        val row = mutableListOf<String>(
                archSmell.name.orEmpty(),
                archSmell.ud.orEmpty(),
                archSmell.hl.orEmpty(),
                archSmell.cd.orEmpty(),
                archSmellExists)
        for (issueRuleKey in issueRuleKeys) {
            val sonarIssues = relatedIssues.count { it.ruleKey == issueRuleKey }
            row.add(sonarIssues.toString())
        }
        rows.add(row)
    }

    FileWriter(outputFile).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(rows.map { it.toTypedArray() })
    }

    println("MAS and sonar issues saved to $outputFile")
    println("Merging took ${(System.currentTimeMillis()-startTime)/1000.0} seconds")
}

/**
 * Merges together extracted csv files for projects.
 */
fun mergeExtractedCsvFiles(projectKeys: List<String>, csvFilename: String) {
    println("Merging $csvFilename")
    // extract all column names occurring in files
    val columnNames = mutableSetOf<String>()
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
private fun findArchitectureSmellFile(projectKey: String, fileName: String): File {
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
fun mapFaultFileCommit(issueFile: String, faultFile: String, commitFile: String, outFile: String) {
    val commitBeans = CsvToBeanBuilder<GitCommits>(FileReader(File(commitFile)))
            .withType(GitCommits::class.java).build().parse()
            .map { it as GitCommits }
    val faultBeans = CsvToBeanBuilder<JiraFaults>(FileReader(File(faultFile)))
            .withType(JiraFaults::class.java).build().parse()
            .map { it as JiraFaults }
    val issueBeans = CsvToBeanBuilder<SonarIssues>(FileReader(File(issueFile)))
            .withType(SonarIssues::class.java).build().parse()
            .map { it as SonarIssues }

    val issueRuleKeys = sortedSetOf<String>()
    issueBeans.mapTo(issueRuleKeys) { it.ruleKey.orEmpty() }
    val header = mutableListOf<String>("jira-key","git-hash","git-sonar-date","sonar-component","sonar-debt")
    for (ruleKey in issueRuleKeys) {
        header.add(ruleKey + "-closed")
        header.add(ruleKey + "-opened")
        header.add(ruleKey)
    }
    val rows = mutableListOf<Array<String>>(header.toTypedArray())
    for (fault in faultBeans) {
        val commits = getRelatedCommits(fault, commitBeans)
        for (commit in commits) {
            val changedFiles = commit.getChangedFilesList().filter { it.endsWith(".java") }
            for (file in changedFiles) {
                val issues = issueBeans.filter { it.component == file }
                //val issuesClosedBefore = issues.filter { it.updateDate.orEmpty() < commit.sonarDate.orEmpty() }
                //val issuesOpenedAfter = issues.filter { it.creationDate.orEmpty() > commit.sonarDate.orEmpty() }
                val issuesOpenedAt = issues.filter { it.creationDate.orEmpty() == commit.sonarDate.orEmpty() }
                val issuesClosedAt = issues.filter { it.updateDate.orEmpty() == commit.sonarDate.orEmpty() }
                val issuesActiveAt = issues.filter { it.creationDate.orEmpty() <= commit.sonarDate.orEmpty() && (it.updateDate.orEmpty().isEmpty() || it.updateDate.orEmpty() > commit.sonarDate.orEmpty()) }
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
        csvWriter.writeAll(rows)
    }
}

fun  getRelatedCommits(fault: JiraFaults, commitBeans: List<GitCommits>): List<GitCommits> {
    return commitBeans.filter {
        val commitMessage = it.message.orEmpty().toLowerCase()
        val faultKey = fault.jiraKey.toString().toLowerCase()
        commitMessage.contains("(\\W$faultKey\\W|^$faultKey\\W|\\W$faultKey\$)".toRegex())
    }
}
