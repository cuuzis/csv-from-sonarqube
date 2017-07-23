import com.opencsv.CSVWriter
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


val parser = JSONParser()
val sonarInstance = "http://sonar.inf.unibz.it"

private val MAX_URL_LENGTH = 2000
private val MAX_ELASTICSEARCH_RESULTS = 10000

fun main(args: Array<String>) {


    //val metricKeys = getMetricKeys()
    //val ruleKeys = getRuleKeys()


    //save current csv for QC projects
    //val projectKeys = getProjectsContainingString("QC -")//QC - aspectj, QC - jboss, QC - jtopen
    //println("projects: ${projectKeys.size}")
    //saveCurrentMeasuresAndIssues("current-measures-and-issues.csv", projectKeys, metricKeys, ruleKeys)


    /*
    //save history csv for "org.apache:commons-cli"
    val projectKey = "org.apache:commons-cli"
    saveNonemptyPastMeasures("nonempty-past-measures.txt", projectKey, metricKeys)
    val usefulMetricKeys = readListFromFile("nonempty-past-measures.txt")
    saveMeasureHistory("measures.csv", projectKey, usefulMetricKeys)
    saveIssues("issues.csv", projectKey, "CLOSED,OPEN", ruleKeys)
    mergeMeasuresWithIssues("measures.csv", "issues.csv", "measures-and-issues.csv")


    saveJiraIssues("jira-issues.csv", "CLI")
    saveGitCommits("git-commits.csv", "https://github.com/apache/commons-cli.git")

*/
    //projectKey.replace("\\W".toRegex(),"-") + ".csv"
    mergeFaultsAndSmells("git-commits.csv","jira-issues.csv", "issues.csv", "faults-and-smells.csv")


    //val jiraIssues = readListFromFile("jira-issues.csv").map{it.split(",")}
    //val gitCommits = readListFromFile("git-commits.csv").map{it.split(",")}

    //val measuresAndIssues = readListFromFile("measures-and-issues.csv").map{it.split(",")}



    //val csvFile = File("git-commits.csv")
    /*val reader = CSVReader(FileReader(csvFile))
    val all = reader.readAll()
    for (smth in all.subList(0,10).map{ it.toList() })
        println(smth)*/

    //https://youtrack.jetbrains.com/issue/KT-5464

    //val reader = FileReader(csvFile)

    //val beans: List<Any?> = CsvToBeanBuilder<csv_model.GitCommits>(FileReader("git-commits.csv"))
    //        .withType(csv_model.GitCommits::class.java).build().parse()

    /*for (bean in beans.subList(0,10).map { it as csv_model.GitCommits }) {
        println(bean.message)
        println(bean.sonarDate)
    }*/

    //val writer = FileWriter("yourfile.csv")
    //val beanToCsv = StatefulBeanToCsvBuilder<csv_model.GitCommits>(writer).build()


    //@Suppress("UNCHECKED_CAST")
    //(beanToCsv as StatefulBeanToCsv<List<csv_model.GitCommits>>).write(beans as List<csv_model.GitCommits>)
    //beanToCsv.write(beans as List<Any?>)

    //beans.forEach { bean -> beanToCsv.write(bean) }
    //writer.close()


    /*
    val resultFile = projectKey.replace("\\W".toRegex(),"-") + ".csv"
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
private fun saveCurrentMeasuresAndIssues(fileName: String, projectKeys: List<String>, metricKeys: List<String>, ruleKeys: List<String>) {
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
        saveIssues("current-issues.csv", projectKey, "OPEN", ruleKeys)

        //read issues from file
        val issueCount = mutableMapOf<String, Int>()
        val issueCSV = readListFromFile("current-issues.csv")
        for (line in issueCSV.subList(1, issueCSV.size)) {
            val ruleKey = line.split(",")[2]
            issueKeys.add(ruleKey)
            val previousCount = issueCount.getOrDefault(ruleKey, 0)
            issueCount[ruleKey] = previousCount + 1
        }
        allProjectIssues.add(issueCount)
    }
    BufferedWriter(FileWriter(fileName)).use { bw ->
        //header
        val sortedMeasureKeys = measureKeys.toSortedSet()
        val sortedIssueKeys = issueKeys.toSortedSet()
        bw.write(sortedMeasureKeys.joinToString(","))
        bw.write(",")
        bw.write(sortedIssueKeys.joinToString(","))
        bw.newLine()

        //rows
        for ((idx, measureValues) in allProjectMeasures.withIndex()) {
            val issueValues = allProjectIssues[idx]

            val rowMeasures = sortedMeasureKeys.map { measureValues[it] ?: "null" }
            val rowIssues = sortedIssueKeys.map { issueValues[it] ?: 0 }
            bw.write(rowMeasures.joinToString(","))
            bw.write(",")
            bw.write(rowIssues.joinToString(","))
            bw.newLine()
        }
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

/*
Returns all metrics available on the server
 */
private fun getMetricKeys(): List<String> {
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

    val header = listOf("creation-date", "update-date", "rule", "component")
    val rows = mutableListOf<List<String>>()
    rows.add(header)

    for (splitKeys in splitIntoBatches(ruleKeys, 40)) {
        saveIssueRows(projectKey, statuses, splitKeys, rows)
    }

    FileWriter(fileName).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(rows.map { it.toTypedArray() })
        println("Sonarqube issues saved to $fileName")
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

/*
Get a list of rule keys for language java
 */
private fun getRuleKeys(): List<String> {
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
    println("\nSending 'GET' request to URL : " + queryURL)
    val url = URL(queryURL)
    val con = url.openConnection() as HttpURLConnection
    con.requestMethod = "GET"
    val responseCode = con.responseCode
    println("Response Code : " + responseCode)

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
