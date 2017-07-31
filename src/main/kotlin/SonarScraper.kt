import com.opencsv.CSVWriter
import com.opencsv.bean.CsvToBeanBuilder
import csv_model.extracted.SonarIssues
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.io.FileReader
import java.io.FileWriter
import java.io.File
import com.opencsv.CSVReader
import csv_model.merged.Correlations


val parser = JSONParser()
val sonarInstance = "http://sonar.inf.unibz.it"
val workDir = "extraction" + File.separatorChar

private val MAX_URL_LENGTH = 2000
private val MAX_ELASTICSEARCH_RESULTS = 10000

fun main(args: Array<String>) {
    val startTime = System.currentTimeMillis()
    //val metricKeys = getMetricKeys()
    //val ruleKeys = getRuleKeys()

    //val projectKeys = getProjectsContainingString("QC -")//QC - aspectj, QC - jboss, QC - jtopen

    /*
    // extract issues, takes long
    for (projectKey in projectKeys) {
        val folderStr = getProjectFolder(projectKey)
        makeEmptyFolder(folderStr)
        saveIssues(folderStr + "current-issues.csv", projectKey, "OPEN", ruleKeys)
    }
    */
    /*
    // map sonar issues to arch cycle smells, takes ~2h30m
    for (projectKey in projectKeys) {
        val folderStr = getProjectFolder(projectKey)
        val archCycleSmellFile = findArchitectureSmellFile(projectKey,"classCyclesShapeTable.csv")
        mergeArchitectureAndCodeIssues(
                outputByClass = folderStr + "cycles-issues-by-class.csv",
                outputByCycle = folderStr + "cycles-issues-by-cycle.csv",
                issueFile = folderStr + "current-issues.csv",
                cyclicDependencyFile = archCycleSmellFile)
    }
    */
    /*
    // map sonar issues to arch MAS smells, takes ~40m
    for (projectKey in projectKeys) {
        val folderStr = getProjectFolder(projectKey)
        val archMasFile = findArchitectureSmellFile(projectKey,"mas.csv")
        mergeArchMasAndCodeIssues(
                outputFile = folderStr + "mas-issues-by-package.csv",
                issueFile = folderStr + "current-issues.csv",
                masFile = archMasFile)
    }
    */
    /*
    // calculate correlations, takes ~30m
    projectKeys.parallelStream().forEach { projectKey ->
        val folder = File(getProjectFolder(projectKey))
        runRscript(File("correlation-cycle-size.R"), folder)
        runRscript(File("correlation-cycle-classes.R"), folder)
        runRscript(File("correlation-cycle-exists.R"), folder)
        runRscript(File("correlation-mas-ud.R"), folder)
        runRscript(File("correlation-mas-hl.R"), folder)
        runRscript(File("correlation-mas-cd.R"), folder)
        runRscript(File("correlation-mas-exists.R"), folder)
    }
    */
    /*
    // merge correlation values from projects (~1s)
    mergeExtractedSameCsvFiles(projectKeys, "correlation-cycle-size.csv")
    mergeExtractedSameCsvFiles(projectKeys, "correlation-cycle-classes.csv")
    mergeExtractedSameCsvFiles(projectKeys, "correlation-cycle-exists.csv")
    mergeExtractedSameCsvFiles(projectKeys, "correlation-mas-ud.csv")
    mergeExtractedSameCsvFiles(projectKeys, "correlation-mas-hl.csv")
    mergeExtractedSameCsvFiles(projectKeys, "correlation-mas-cd.csv")
    mergeExtractedSameCsvFiles(projectKeys, "correlation-mas-exists.csv")
    */
    findUsefulCorrelations(outFile = "by-project-summary.csv", csvFile = "by-project-correlation-cycle-exists.csv")
    /*
    // merge architecture smells for projects (~30s)...
    mergeExtractedCsvFiles(projectKeys, "cycles-issues-by-class.csv")
    mergeExtractedCsvFiles(projectKeys, "cycles-issues-by-cycle.csv")
    mergeExtractedCsvFiles(projectKeys, "mas-issues-by-package.csv")
    */
    /*
    // ...and calculate correlations, takes very long time (188 columns, for each: ~2min cycle, ~7min mas):
    runRscript(File("correlation-cycle-size.R"), File(workDir))
    runRscript(File("correlation-cycle-classes.R"), File(workDir))
    runRscript(File("correlation-cycle-exists.R"), File(workDir))
    runRscript(File("correlation-mas-ud.R"), File(workDir))
    runRscript(File("correlation-mas-hl.R"), File(workDir))
    runRscript(File("correlation-mas-cd.R"), File(workDir))
    runRscript(File("correlation-mas-exists.R"), File(workDir))
    */
    //running in parallel, mas file is shrunk:
    /*val rFileList = listOf(
            "correlation-cycle-size.R",
            "correlation-cycle-classes.R",
            "correlation-cycle-exists.R",
            "correlation-mas-ud.R",
            "correlation-mas-hl.R",
            "correlation-mas-cd.R",
            "correlation-mas-exists.R")
    rFileList.parallelStream().forEach { rFile -> runRscript(File(rFile), File(workDir)) }
    */
//    R script 'correlation-mas-ud.R' on extraction done in 3569.168 seconds
//    R script 'correlation-mas-hl.R' on extraction done in 3725.133 seconds
//    R script 'correlation-mas-cd.R' on extraction done in 4281.505 seconds
//    R script 'correlation-mas-exists.R' on extraction done in 4369.807 seconds (72 minutes)
//    R script 'correlation-cycle-size.R' on extraction done in 20509.04 seconds
//    R script 'correlation-cycle-classes.R' on extraction done in 20626.744 seconds
//    R script 'correlation-cycle-exists.R' on extraction done in 20758.265 seconds
//    Execution completed in 20758.312 seconds (345 minutes = 6h)

    /*
    //save history csv for "org.apache:commons-cli"
    val projectKey = "org.apache:commons-cli"
    val folderStr = getProjectFolder(projectKey)
    makeEmptyFolder(folderStr)
    saveNonemptyPastMeasures(folderStr + "nonempty-past-measures.txt", projectKey, metricKeys)
    val usefulMetricKeys = readListFromFile(folderStr + "nonempty-past-measures.txt")
    saveMeasureHistory(fileName = folderStr + "measures.csv", projectKey = projectKey, metricKeys = usefulMetricKeys)
    saveIssues(fileName = folderStr + "issues.csv", projectKey = projectKey, statuses = "CLOSED,OPEN", ruleKeys = ruleKeys)
    mergeMeasuresWithIssues(measuresFile = folderStr + "measures.csv", issuesFile = folderStr + "issues.csv", combinedFile = folderStr + "measures-and-issues.csv")

    saveJiraIssues(folderStr + "jira-issues.csv", "CLI")
    saveGitCommits(folderStr + "git-commits.csv", "https://github.com/apache/commons-cli.git")

    //mergeFaultsAndSmells("git-commits.csv","jira-issues.csv", "issues.csv", "faults-and-smells.csv")
*/

    println("Execution completed in ${(System.currentTimeMillis()-startTime)/1000.0} seconds (${(System.currentTimeMillis() - startTime)/60000} minutes)")
}

fun findUsefulCorrelations(outFile: String, csvFile: String) {
    println("Finding useful correlations")
    val correlationBeans = CsvToBeanBuilder<Correlations>(FileReader(workDir + csvFile))
            .withType(Correlations::class.java).build().parse()
            .map { it as Correlations }
    val issueKeys = mutableSetOf<String>()
    correlationBeans.mapTo(issueKeys) { it.issueName.orEmpty() }
    val rows = mutableListOf<Array<String>>()
    for (issue in issueKeys) {
        val infectedProjects = correlationBeans.filter { it.issueName == issue }
        val measurableOccurrences = infectedProjects.filter { it.kendallPvalue?.toDoubleOrNull() != null && it.kendallPvalue.toDouble() < 0.05 }
        val correlation05Occurrences = measurableOccurrences.filter { it.kendallTau?.toDoubleOrNull() != null && it.kendallTau.toDouble() > 0.5 }
        val correlation06Occurrences = measurableOccurrences.count { it.kendallTau?.toDoubleOrNull() != null && it.kendallTau.toDouble() > 0.6 }
        rows.add(arrayOf(
                csvFile.removePrefix("by-project-correlation-").removeSuffix(".csv"),
                issue,
                infectedProjects.count().toString(),
                measurableOccurrences.count().toString(),
                correlation05Occurrences.count().toString(),
                correlation06Occurrences.toString(),
                correlation05Occurrences.joinToString(";") { it.project.orEmpty() }
                ))
    }
    FileWriter(workDir + outFile).use { fw ->
        val csvWriter = CSVWriter(fw)
        val header = arrayOf("measure", "issueName", "infectedProjects", "pvalue005", "correlation05", "correlation06", "projectsWithCorrelation")
        csvWriter.writeNext(header)
        csvWriter.writeAll(rows
                .sortedWith(compareBy({ it[5] }, { it[4] }, { it[3] }, { it[2] } ))
                .reversed())
    }
    println("Significant correlations saved to ${workDir + outFile}")
}

/**
 * Merges together extracted csv files for projects.
 */
private fun mergeExtractedCsvFiles(projectKeys: List<String>, csvFilename: String) {
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
private fun mergeExtractedSameCsvFiles(projectKeys: List<String>, csvFilename: String) {
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
private fun makeEmptyFolder(directoryStr: String) {
    val folder = File(directoryStr)
    if (folder.exists()) {
        if (!folder.deleteRecursively())
            throw Exception("Could not delete ${folder.name} directory")
    }
    if (!folder.mkdirs())
        throw Exception("Could not create ${folder.name} directory")
}

/**
 * Runs 'Rscript.exe rFile' in the specified folder
 */
fun  runRscript(rFile: File, folder: File) {
    println("Running '${rFile.name}' on " + folder.name.split(File.separatorChar).last())
    val startTime = System.currentTimeMillis()
    val scriptFile = rFile.copyTo(File(folder, rFile.name), overwrite = true)
    val pb = ProcessBuilder("C:\\Program Files\\R\\R-3.3.3\\bin\\x64\\Rscript.exe", scriptFile.name)
            .directory(folder)
            .redirectErrorStream(true)
            .inheritIO()
    val process = pb.start()
    val returnCode = process.waitFor()
    scriptFile.delete()
    if (returnCode != 0)
        throw Exception("Rscript.exe execution returned $returnCode")
    println("R script '${rFile.name}' on ${folder.name.split(File.separatorChar).last()}" +
            " done in ${(System.currentTimeMillis() - startTime)/1000.0} seconds")
}

fun  findArchitectureSmellFile(projectKey: String, fileName: String): File {
    val architectureRoot = File("architecture-smells-arcan/")
    val architectureFolder = architectureRoot.listFiles().find {
        it.toString().toLowerCase().startsWith(
                architectureRoot.name + File.separatorChar + projectKey.removePrefix("QC:").toLowerCase())
    }!!
    return architectureFolder.listFiles().find { it.name == fileName}!!
}

fun getProjectFolder(projectKey: String): String {
    return workDir + projectKey.replace("\\W".toRegex(),"-") + File.separatorChar
}

/*
* Parses Instant to sonarqube-format datetime
*/
fun getSonarDateFromInstant(date: Instant): String {
    val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    val result = formatter.format(date)
            .removeRange(22, 23) // removes colon from time zone (to post timestamp to sonarqube analysis)
    return result
}

/*
* Parses sonarqube-format datetime to an Instant
*/
fun  getInstantFromSonarDate(sonarDate: String): Instant {
    val dateStr = sonarDate.substring(0, 22) + ":" + sonarDate.substring(22)
    return OffsetDateTime.parse(dateStr).toInstant()
}

/*
Returns a list of project keys containing a string
 */
fun  getProjectsContainingString(partOfName: String): List<String> {
    println("Requesting keys for projects containing '$partOfName'")
    val query = "http://sonar.inf.unibz.it/api/components/search" +
            "?qualifiers=TRK" +
            "&ps=1000" +
            "&q=${URLEncoder.encode(partOfName, "UTF-8")}"
    val response = getStringFromUrl(query)
    val mainObject = parser.parse(response) as JSONObject
    val componentArray = mainObject["components"] as JSONArray
    return componentArray.filterIsInstance<JSONObject>().map { it["key"].toString() }
}

/*
Saves in a .csv file all of the current measures and issues for given projects
 */
// TODO: split into measures for key only
private fun saveCurrentMeasuresAndIssues(projectKeys: List<String>, metricKeys: List<String>, ruleKeys: List<String>) {
    val measureKeys = mutableSetOf<String>()
    val issueKeys = mutableSetOf<String>()
    val allProjectMeasures = mutableListOf<Map<String,String>>()
    val allProjectIssues = mutableListOf<Map<String,Int>>()
    for (projectKey in projectKeys) {
        //get measures
        val measureValues = mutableMapOf<String, String>()
        val metricKeysLeft = metricKeys.toMutableList()
        measureKeys.add("_project")
        measureValues.put("_project", projectKey)
        val measureQuery = "$sonarInstance/api/measures/component" +
                "?componentKey=$projectKey" +
                "&metricKeys="
        while (!metricKeysLeft.isEmpty()) {
            var query = measureQuery
            while (!metricKeysLeft.isEmpty() && (query.length + metricKeysLeft.first().length < MAX_URL_LENGTH)) {
                if (query == measureQuery)
                    query += metricKeysLeft.removeAt(0)
                else
                    query += "," + metricKeysLeft.removeAt(0)
            }
            val measureResult = getStringFromUrl(query)
            val mainObject = parser.parse(measureResult) as JSONObject
            val componentObject = mainObject["component"] as JSONObject
            val measureArray = componentObject["measures"] as JSONArray
            for (metricObject in measureArray.filterIsInstance<JSONObject>()) {
                val measureKey = metricObject["metric"].toString()
                val measureValue = metricObject["value"].toString().replace(",", ";")
                measureKeys.add(measureKey)
                measureValues.put(measureKey, measureValue)
            }
        }
        allProjectMeasures.add(measureValues)

        //save issues to file
        val issuesFolderName = projectKey.replace("\\W".toRegex(),"-")
        saveIssues("$issuesFolderName/current-issues.csv", projectKey, "OPEN", ruleKeys)

        //read issues from file and count them
        val issueCount = mutableMapOf<String, Int>()
        val issueBeans = CsvToBeanBuilder<SonarIssues>(FileReader(File("$issuesFolderName/current-issues.csv")))
                .withType(SonarIssues::class.java).build().parse()
                .map { it as SonarIssues }
        for (issueBean in issueBeans) {
            val ruleKey = issueBean.ruleKey.orEmpty()
            issueKeys.add(ruleKey)
            val previousCount = issueCount.getOrDefault(ruleKey, 0)
            issueCount[ruleKey] = previousCount + 1
        }
        allProjectIssues.add(issueCount)
    }

    // summary for all projects
    val fileName = "current-measures-and-issues.csv"
    //header
    val sortedMeasureKeys = measureKeys.toSortedSet()
    val sortedIssueKeys = issueKeys.toSortedSet()
    val header = sortedMeasureKeys.toList() + sortedIssueKeys.toList()
    val rows = mutableListOf<List<String>>()
    rows.add(header)

    //rows
    for ((idx, measureValues) in allProjectMeasures.withIndex()) {
        val issueValues = allProjectIssues[idx]
        val rowMeasures = sortedMeasureKeys.map { measureValues[it] ?: "null" }
        val rowIssues = sortedIssueKeys.map { issueValues[it] ?: 0 }
        rows.add(rowMeasures + rowIssues.map { it.toString() })
    }

    FileWriter(fileName).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(rows.map { it.toTypedArray() })
    }
    println("Measures and issues saved to $fileName")
}


/*
Tests which past measures contain nonempty values for a given project.
Stores the result in file.
 */
private fun saveNonemptyPastMeasures(fileName: String, projectKey: String, metricKeys: List<String>) {
    val usefulMeasures = mutableListOf<String>()
    val measureQuery = "$sonarInstance/api/measures/search_history" +
            "?component=$projectKey" +
            "&metrics="
    val metricKeysLeft = metricKeys.toMutableList()
    while (!metricKeysLeft.isEmpty()) {
        var query = measureQuery
        while (!metricKeysLeft.isEmpty() && (query.length + metricKeysLeft.first().length < MAX_URL_LENGTH)) {
            if (query == measureQuery)
                query += metricKeysLeft.removeAt(0)
            else
                query += "," + metricKeysLeft.removeAt(0)
        }
        val measureResult = getStringFromUrl(query)
        val measureObject = parser.parse(measureResult) as JSONObject
        val pagingObject = measureObject["paging"] as JSONObject
        val measureCount = Integer.parseInt(pagingObject["total"].toString())
        println("Measures found: $measureCount")
        val measureArray = measureObject["measures"] as JSONArray
        for (metricObject in measureArray.filterIsInstance<JSONObject>()) {
            val measureKey = metricObject["metric"].toString()
            val measureHistory = metricObject["history"] as JSONArray
            if (measureHistory.isEmpty())
                println("$measureKey : empty")
            else {
                println("$measureKey : values=${measureHistory.size}")
                if (measureHistory.size > 1) {// && measureKey != "quality_profiles" && measureKey != "quality_gate_details") {
                    /*if (measureKey == "quality_gate_details") {
                        usefulMeasures.add("$measureKey-op")
                        usefulMeasures.add("$measureKey-actual")
                        usefulMeasures.add("$measureKey-period")
                        usefulMeasures.add("$measureKey-metric")
                        usefulMeasures.add("$measureKey-level")
                        usefulMeasures.add("$measureKey-error")
                        usefulMeasures.add("$measureKey-warning")
                    }*/
                    usefulMeasures.add(measureKey)
                }
            }
        }
    }
    println("Nonempty past measures: ${usefulMeasures.size}")

    BufferedWriter(FileWriter(fileName)).use { bw ->
        for (key in usefulMeasures) {
            bw.write(key)
            bw.newLine()
        }
    }
}

/**
 * Returns all metrics available on the server
 */
private fun getMetricKeys(): List<String> {
    println("Requesting metric keys")
    val metricsQuery = "$sonarInstance/api/metrics/search?ps=1000"
    val metricsResult = getStringFromUrl(metricsQuery)
    val metricsObject = parser.parse(metricsResult) as JSONObject
    val metricsCount = Integer.parseInt(metricsObject["total"].toString())
    println("Metrics found: $metricsCount")
    val metricsKeys = mutableListOf<String>()
    val metricsArray = metricsObject["metrics"] as JSONArray
    metricsArray.filterIsInstance<JSONObject>().mapTo(metricsKeys) { it["key"].toString() }
    println(metricsKeys)
    assert(metricsKeys.size == metricsCount)
    return metricsKeys
}

/*
Saves past measures measures for a project in a .csv file
 */
private fun saveMeasureHistory(fileName: String, projectKey: String, metricKeys: List<String>) {
    val measureQuery = "$sonarInstance/api/measures/search_history" +
            "?component=" + projectKey +
            "&ps=1000" +
            "&metrics=" + metricKeys.joinToString(",")

    val measureResult = getStringFromUrl(measureQuery)
    val measureObject = parser.parse(measureResult) as JSONObject
    val measureArray = measureObject["measures"] as JSONArray
    val measureMap = sortedMapOf<String, Array<String>>()

    for (metricObject in measureArray.filterIsInstance<JSONObject>()) {
        val measureKey = metricObject["metric"].toString()
        val measureHistory = metricObject["history"] as JSONArray
        for (measureEntry in measureHistory.filterIsInstance<JSONObject>()) {
            val date = getInstantFromSonarDate( measureEntry["date"].toString() ).toString()
            val value = measureEntry["value"].toString()
            measureMap.putIfAbsent(date, Array(metricKeys.size, init = { _ -> "0" }))
            val valueArray = measureMap[date]
            valueArray!![metricKeys.indexOf(measureKey)] = value
        }
    }

    // save data to file
    val header = listOf<String>("measure-date") + metricKeys
    val rows = mutableListOf<List<String>>()
    rows.add(header)
    for ((key, values) in measureMap) {
        rows.add((listOf(key) + values))
    }
    FileWriter(fileName).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(rows.map { it.toTypedArray() })
        println("Sonarqube measures saved to $fileName")
    }
}

/*
Saves issue history for a project in a .csv file
 */
private fun saveIssues(fileName: String, projectKey: String, statuses: String, ruleKeys: List<String>) {
    print("Extracting issues for " + projectKey)
    val startTime = System.currentTimeMillis()
    val header = listOf("creation-date", "update-date", "rule", "component")
    val rows = mutableListOf<List<String>>()
    rows.add(header)

    for (splitKeys in splitIntoBatches(ruleKeys, 40)) {
        saveIssueRows(projectKey, statuses, splitKeys, rows)
    }

    FileWriter(fileName).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(rows.map { it.toTypedArray() })
        println("Sonarqube issues saved to $fileName, extraction took ${(System.currentTimeMillis()-startTime)/1000.0} seconds")
    }
}

private fun saveIssueRows(componentKey: String, statuses: String, ruleKeys: List<String>, rows: MutableList<List<String>>) {
    val pageSize = 500
    var currentPage = 1
    var issuesQuery = "$sonarInstance/api/issues/search" +
            "?componentKeys=$componentKey" +
            "&s=CREATION_DATE" +
            "&statuses=$statuses" +
            "&rules=${ruleKeys.joinToString(",")}" +
            "&ps=$pageSize" +
            "&p=$currentPage"
    val sonarResult = getStringFromUrl(issuesQuery)
    var mainObject = parser.parse(sonarResult) as JSONObject
    val totalIssues = Integer.valueOf(mainObject["total"].toString())
    if (totalIssues > MAX_ELASTICSEARCH_RESULTS && ruleKeys.size > 1) {
        // if result size is too big, split it
        for (splitKeys in splitIntoBatches(ruleKeys, ruleKeys.size/2)) {
            saveIssueRows(componentKey, statuses, splitKeys, rows)
        }
    } else {
        if (totalIssues > MAX_ELASTICSEARCH_RESULTS)
            println("WARNING: only $MAX_ELASTICSEARCH_RESULTS of $totalIssues returned for ${ruleKeys.first()} in $componentKey")
            //TODO:split by date
        // save results
        //var issuesArray = mainObject["issues"] as JSONArray
        //var issuesArraySize = issuesArray.size
        //println("Saving $issuesArraySize/${mainObject["total"]}")
        while (true) {
            // save row data
            val issuesArray = mainObject["issues"] as JSONArray
            for (issueObject in issuesArray.filterIsInstance<JSONObject>()) {
                val creationDateSonar = issueObject["creationDate"].toString()
                val creationDate = getInstantFromSonarDate(creationDateSonar).toString()
                val updateDateSonar = issueObject["updateDate"].toString()
                val updateDate =
                        if (updateDateSonar != "")
                            getInstantFromSonarDate(updateDateSonar).toString()
                        else
                            ""
                val rule = issueObject["rule"].toString()
                val component = issueObject["component"].toString()
                val classname = component.replaceFirst((componentKey + ":").toRegex(), "")
                val status = issueObject["status"].toString()
                val closedDate =
                        if (status == "CLOSED")
                            updateDate
                        else
                            ""
                rows.add(mutableListOf<String>(creationDate, closedDate, rule, classname))
            }
            // get next page
            if (currentPage * pageSize >= totalIssues || currentPage >= 20)
                break
            currentPage++
            issuesQuery = "$sonarInstance/api/issues/search" +
                    "?componentKeys=$componentKey" +
                    "&s=CREATION_DATE" +
                    "&statuses=$statuses" +
                    "&rules=${ruleKeys.joinToString(",")}" +
                    "&ps=$pageSize" +
                    "&p=$currentPage"
            val result = getStringFromUrl(issuesQuery)
            mainObject = parser.parse(result) as JSONObject
        }
    }
}

/**
 * Splits a list into many smaller lists.
 * @param batchSize maximum size for the smaller lists
 */
fun splitIntoBatches(list: List<String>, batchSize: Int): List<List<String>> {
    val result = mutableListOf<List<String>>()
    var start = 0
    var end = batchSize
    while (start < list.size) {
        if (end > list.size)
            end = list.size
        result.add(list.subList(start, end))
        start = end
        end += batchSize
    }
    return result
}

/**
 * Get a list of rule keys for java language
 */
private fun getRuleKeys(): List<String> {
    println("Requesting rule keys for java")
    val result = mutableListOf<String>()
    val pageSize = 500
    var page = 0
    do {
        page++
        val query = "http://sonar.inf.unibz.it/api/rules/search" +
                "?ps=$pageSize" +
                "&p=$page" +
                "&f=lang" +
                "&languages=java"//"&languages=web"
        val response = getStringFromUrl(query)
        val mainObject = parser.parse(response) as JSONObject
        val ruleArray = mainObject["rules"] as JSONArray
        result.addAll(
                ruleArray.filterIsInstance<JSONObject>().map { it["key"].toString() })
        val lastItem = page * pageSize
    } while (Integer.parseInt(mainObject["total"].toString()) > lastItem)
    return result
}

/*
Parses an URL request as a string
 */
fun getStringFromUrl(queryURL: String): String {
    assert(queryURL.length <= MAX_URL_LENGTH) // URLS over 2000 are not supported
    println("Sending 'GET' request to URL: " + queryURL)
    val url = URL(queryURL)
    val con = url.openConnection() as HttpURLConnection
    con.requestMethod = "GET"
    val responseCode = con.responseCode
    if (responseCode != 200)
        throw Exception("Response Code : " + responseCode)

    val `in` = BufferedReader(InputStreamReader(con.inputStream))
    val stringBuilder = StringBuilder()
    do {
        val inputLine = `in`.readLine()
        if (inputLine != null) {
            stringBuilder.append(inputLine)
        }
    } while (inputLine != null)
    return stringBuilder.toString()
}

/*
Reads each line from file into a string list
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
