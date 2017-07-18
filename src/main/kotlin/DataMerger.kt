import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import com.opencsv.bean.CsvToBeanBuilder
import java.io.File
import java.io.FileReader
import java.io.FileWriter

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