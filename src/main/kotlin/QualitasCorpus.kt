import com.opencsv.CSVWriter
import com.opencsv.bean.CsvToBeanBuilder
import csv_model.architecture_smells.ArchClassCycles
import csv_model.architecture_smells.ArchMultiA
import csv_model.extracted.SonarIssues
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * Returns the taxonomy name of a code smell rule. If code smell is not in taxonomy, returns its key.
 */
private fun ruleTaxonomyGroup(ruleKey: String): String {
    when (ruleKey) {
        "code_smells:long_method",
        "code_smells:complex_class",
        "code_smells:long_parameter_list",
        "code_smells:blob_class",
        "code_smells:large_class",
        "code_smells:swiss_army_knife"->
            return "The Bloaters"

        "code_smells:lazy_class",
        "common-java:DuplicatedBlocks",
        "code_smells:speculative_generality",
        "code_smells:many_field_attributes_not_complex" ->
            return "The Dispensables"

        "code_smells:message_chains" ->
            return "The Encapsulators"

        "code_smells:antisingleton",
        "code_smells:baseclass_knows_derived",
        "code_smells:baseclass_abstract",
        "code_smells:class_data_private",
        "code_smells:refused_parent_bequest",
        "code_smells:tradition_breaker" ->
            return "The Object-Orientation Abusers"

        "code_smells:functional_decomposition" ->
            return "The Object-Orientation Avoiders"

        "code_smells:spaghetti_code" ->
            return "The Change Preventers"

        else ->
            return ruleKey
    }
}

private fun mergeIssuesWithCyclicDependencies(outputByClass: String, outputByCycle: String, issueFile: String, cyclicDependencyFile: File, groupByTaxonomy: Boolean) {
    println("Merging cyclic dependencies and sonar issues")
    val startTime = System.currentTimeMillis()
    // read code smells
    var issueBeans = CsvToBeanBuilder<SonarIssues>(FileReader(File(issueFile)))
            .withType(SonarIssues::class.java).build().parse()
            .map { it as SonarIssues }
    if (groupByTaxonomy)
        issueBeans = issueBeans.map {
            SonarIssues(creationDate = it.creationDate,
                    updateDate = it.updateDate,
                    ruleKey = ruleTaxonomyGroup(it.ruleKey.orEmpty()),
                    effort = it.effort,
                    component = it.component)
        }

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
fun mapIssuesToMAS(projectKeys: List<String>, groupByTaxonomy: Boolean) {
    for (projectKey in projectKeys) {
        val folderStr = getProjectFolder(projectKey)
        val archMasFile = findArchitectureSmellFile(projectKey, "mas.csv")
        mergeIssuesWithMAS(
                outputFile = folderStr + "mas-issues-by-package.csv",
                issueFile = folderStr + "current-issues.csv",
                masFile = archMasFile,
                groupByTaxonomy = groupByTaxonomy)
    }
}

private fun mergeIssuesWithMAS(outputFile: String, issueFile: String, masFile: File, groupByTaxonomy: Boolean) {
    println("Merging MAS and sonar issues")
    val startTime = System.currentTimeMillis()
    // read code smells
    var issueBeans = CsvToBeanBuilder<SonarIssues>(FileReader(File(issueFile)))
            .withType(SonarIssues::class.java).build().parse()
            .map { it as SonarIssues }
    if (groupByTaxonomy)
        issueBeans = issueBeans.map {
            SonarIssues(creationDate = it.creationDate,
                    updateDate = it.updateDate,
                    ruleKey = ruleTaxonomyGroup(it.ruleKey.orEmpty()),
                    effort = it.effort,
                    component = it.component)
        }

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
 * Merges architecture and code issues by components
 * Full join, ignores components without problems:
 *   Class | Arch issue | Smell
 *   ----------------------------
 *     A         1          1
 *     B         -          1
 *     C         1          -
 */
fun mapIssuesToCyclicDependencies(projectKeys: List<String>, groupByTaxonomy: Boolean) {
    for (projectKey in projectKeys) {
        val folderStr = getProjectFolder(projectKey)
        val archCycleSmellFile = findArchitectureSmellFile(projectKey, "classCyclesShapeTable.csv")
        mergeIssuesWithCyclicDependencies(
                outputByClass = folderStr + "cycles-issues-by-class.csv",
                outputByCycle = folderStr + "cycles-issues-by-cycle.csv",
                issueFile = folderStr + "current-issues.csv",
                cyclicDependencyFile = archCycleSmellFile,
                groupByTaxonomy = groupByTaxonomy)
    }
}