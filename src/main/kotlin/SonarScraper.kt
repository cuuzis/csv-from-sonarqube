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


private val parser = JSONParser()
private val MAX_URL_LENGTH = 2000
private val MAX_ELASTICSEARCH_RESULTS = 10000

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

/**
 * Returns a list of project keys containing a string
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

/**
 * Saves past measures for a project in a .csv file
 */
fun saveMeasureHistory(fileName: String, projectKey: String) {
    val metricKeys = getNonemptyMetricKeys(projectKey)
    val measureMap = sortedMapOf<String, Array<String>>()
    val pageSize = 500
    var currentPage = 0
    do {
        currentPage++
        val measureQuery = "$sonarInstance/api/measures/search_history" +
                "?component=" + projectKey +
                "&ps=$pageSize" +
                "&p=$currentPage" +
                "&metrics=" + metricKeys.joinToString(",")

        val measureResult = getStringFromUrl(measureQuery)
        val mainObject = parser.parse(measureResult) as JSONObject
        val pagingObject = mainObject["paging"] as JSONObject
        val measureArray = mainObject["measures"] as JSONArray
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
    } while (pageSize * currentPage < pagingObject["total"].toString().toInt())

    // save data to file
    val header = listOf<String>("measure-date") + metricKeys
    val rows = mutableListOf<List<String>>()
    rows.add(header)
    for ((key, values) in measureMap) {
        rows.add((listOf(key) + values))
    }
    val folderStr = getProjectFolder(projectKey)
    FileWriter(folderStr + fileName).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(rows.map { it.toTypedArray() })
    }
    println("Sonarqube measures saved to ${folderStr + fileName}")
}

/**
 * Tests which past measures contain nonempty values and are therefore useful
 */
private fun getNonemptyMetricKeys(projectKey: String): List<String> {
    val usefulMeasures = mutableListOf<String>()
    val measureQuery = "$sonarInstance/api/measures/search_history" +
            "?component=$projectKey" +
            "&metrics="
    val metricKeysLeft = getMetricKeys().toMutableList()
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
        val measureArray = measureObject["measures"] as JSONArray
        for (metricObject in measureArray.filterIsInstance<JSONObject>()) {
            val measureKey = metricObject["metric"].toString()
            val measureHistory = metricObject["history"] as JSONArray
            if (!measureHistory.isEmpty() && measureHistory.size > 1) {
                usefulMeasures.add(measureKey)
            }
        }
    }
    println("Nonempty are ${usefulMeasures.size} sonarqube measures: $usefulMeasures")
    return usefulMeasures
}

/**
 * Returns all measures available on the server
 */
private fun getMetricKeys(): List<String> {
    val metricsQuery = "$sonarInstance/api/metrics/search?ps=1000"
    val metricsResult = getStringFromUrl(metricsQuery)
    val metricsObject = parser.parse(metricsResult) as JSONObject
    val metricsCount = Integer.parseInt(metricsObject["total"].toString())
    val metricsKeys = mutableListOf<String>()
    val metricsArray = metricsObject["metrics"] as JSONArray
    metricsArray.filterIsInstance<JSONObject>().mapTo(metricsKeys) { it["key"].toString() }
    println("Found $metricsCount sonarqube measures: $metricsKeys")
    assert(metricsKeys.size == metricsCount)
    return metricsKeys
}

/*
Saves issue history for a project in a .csv file in a new empty folder
 */
fun saveIssues(fileName: String, projectKey: String, statuses: String, ruleKeys: List<String>) {
    println("Extracting issues for " + projectKey)
    val startTime = System.currentTimeMillis()
    val folderStr = getProjectFolder(projectKey)
    makeEmptyFolder(folderStr)
    val header = listOf("creation-date", "update-date", "rule", "component", "effort")
    val rows = mutableListOf<List<String>>()
    rows.add(header)

    for (splitKeys in splitIntoBatches(ruleKeys, 40)) {
        saveIssueRows(projectKey, statuses, splitKeys, rows, null, null)
    }

    FileWriter(folderStr + fileName).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(rows.map { it.toTypedArray() })
        println("Sonarqube issues saved to ${folderStr + fileName}, extraction took ${(System.currentTimeMillis()-startTime)/1000.0} seconds")
    }
}

private fun saveIssueRows(componentKey: String, statuses: String, ruleKeys: List<String>, rows: MutableList<List<String>>, createdAt: String?, createdAfter: String?) {
    val pageSize = 500
    var currentPage = 1
    var issuesQuery = "$sonarInstance/api/issues/search" +
            "?componentKeys=$componentKey" +
            "&s=CREATION_DATE" +
            "&statuses=$statuses" +
            "&rules=${ruleKeys.joinToString(",")}" +
            "&ps=$pageSize" +
            "&p=$currentPage"
    if (createdAt != null)
        issuesQuery += "&createdAt=${URLEncoder.encode(createdAt, "UTF-8")}"
    if (createdAfter != null)
        issuesQuery += "&createdAfter=${URLEncoder.encode(createdAfter, "UTF-8")}"
    val sonarResult = getStringFromUrl(issuesQuery)
    var mainObject = parser.parse(sonarResult) as JSONObject
    val totalIssues = Integer.valueOf(mainObject["total"].toString())
    // if result size is too big, split it by rule keys and dates
    if (totalIssues > MAX_ELASTICSEARCH_RESULTS && ruleKeys.size > 1) {
        for (splitKeys in splitIntoBatches(ruleKeys, ruleKeys.size/2)) {
            saveIssueRows(componentKey, statuses, splitKeys, rows, null, null)
        }
    } else if (totalIssues > MAX_ELASTICSEARCH_RESULTS && ruleKeys.size == 1) {
        val issuesArray = mainObject["issues"] as JSONArray
        val firstIssue = issuesArray.filterIsInstance<JSONObject>().first()
        val firstIssueDate = firstIssue["creationDate"].toString()
        val afterFirstIssue = getSonarDateFromInstant(getInstantFromSonarDate(firstIssueDate).plusSeconds(1))
        saveIssueRows(componentKey, statuses, ruleKeys, rows, firstIssueDate, null)
        saveIssueRows(componentKey, statuses, ruleKeys, rows, null, afterFirstIssue)
        // TODO: Limit recursion (Exception in thread "main" java.lang.StackOverflowError for Apache Hive)
    } else {
        if (totalIssues > MAX_ELASTICSEARCH_RESULTS)
            println("WARNING: only $MAX_ELASTICSEARCH_RESULTS of $totalIssues returned for ${ruleKeys.first()} in $componentKey")
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
                val effortMinutes = effortToMinutes(issueObject["effort"].toString())
                val closedDate =
                        if (status == "CLOSED")
                            updateDate
                        else
                            ""
                rows.add(mutableListOf<String>(creationDate, closedDate, rule, classname, effortMinutes))
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
            if (createdAt != null)
                issuesQuery += "&createdAt=${URLEncoder.encode(createdAt, "UTF-8")}"
            if (createdAfter != null)
                issuesQuery += "&createdAfter=${URLEncoder.encode(createdAfter, "UTF-8")}"
            val result = getStringFromUrl(issuesQuery)
            mainObject = parser.parse(result) as JSONObject
        }
    }
}

/**
 * Converts sonarqube effort (e.g. 1h30min or 5min) to minutes
 */
fun effortToMinutes(effortString: String): String {
    try {
        if (effortString == "" || effortString == "null")
            return "0"
        else {
            val hours = Integer.valueOf(effortString.substringBefore("h", "0"))
            val minutes = Integer.valueOf(effortString.substringAfter("h").substringBefore("min", "0"))
            return (hours * 60 + minutes).toString()
        }
    } catch (e: Exception) {
        throw Exception("Cannot parse sonarqube effort to minutes: \"$effortString\"", e)
    }
}

/**
 * Splits a list into many smaller lists.
 * @param batchSize maximum size for the smaller lists
 */
private fun splitIntoBatches(list: List<String>, batchSize: Int): List<List<String>> {
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
fun getRuleKeys(): List<String> {
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