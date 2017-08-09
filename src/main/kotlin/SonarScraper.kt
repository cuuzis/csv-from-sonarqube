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
    val query = "$sonarInstance/api/components/search" +
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
    if (metricsKeys.size != metricsCount)
        throw Exception("Saved ${metricsKeys.size} measure keys, but there are $metricsCount found")
    return metricsKeys
}

/**
 * Saves issue history for a project in a .csv file.
 */
fun saveIssues(fileName: String, componentKey: String, statuses: String, ruleKeys: List<String>) {
    println("Extracting issues for " + componentKey)
    val startTime = System.currentTimeMillis()

    val header = arrayOf("creation-date", "update-date", "rule", "component", "effort")
    val rows = mutableListOf<Array<String>>()
    saveIssuesForKeys(ruleKeys, componentKey, statuses, rows)

    FileWriter(fileName).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeNext(header)
        csvWriter.writeAll(rows.sortedBy { it[0] }) // sorted by "creation-date"
    }
    println("Sonarqube issues saved to $fileName, extraction took ${(System.currentTimeMillis()-startTime)/1000.0} seconds")
}

/**
 * Saves issues to rows.
 */
private fun saveIssuesForKeys(ruleKeys: List<String>, componentKey: String, statuses: String, rows: MutableList<Array<String>>) {
    for (splitKeys in splitIntoBatches(ruleKeys, 20)) {
        var createdAfter = "&createdAfter=${URLEncoder.encode("1900-01-01T01:01:01+0100", "UTF-8")}"
        var issuesLeft = issuesAt(createdAfter, componentKey, statuses, splitKeys)
        if (Integer.valueOf(issuesLeft["total"].toString()) > MAX_ELASTICSEARCH_RESULTS && ruleKeys.size > 1) { // split by rule keys
            for (splitKeysSmaller in splitIntoBatches(splitKeys, ruleKeys.size/2)) {
                saveIssuesForKeys(splitKeysSmaller, componentKey, statuses, rows)
            }
        } else if (Integer.valueOf(issuesLeft["total"].toString()) > 0) {
            while (Integer.valueOf(issuesLeft["total"].toString()) > MAX_ELASTICSEARCH_RESULTS) { // split by dates
                val issuesArray = issuesLeft["issues"] as JSONArray
                val firstIssue = issuesArray.filterIsInstance<JSONObject>().first()
                val firstIssueDate = firstIssue["creationDate"].toString()
                val createdAt = "&createdAt=${URLEncoder.encode(firstIssueDate, "UTF-8")}"
                saveIssuesAt(createdAt, componentKey, statuses, splitKeys, rows)

                val nextDate = getSonarDateFromInstant(getInstantFromSonarDate(firstIssueDate).plusSeconds(1))
                createdAfter = "&createdAfter=${URLEncoder.encode(nextDate, "UTF-8")}"
                issuesLeft = issuesAt(createdAfter, componentKey, statuses, splitKeys)
            }
            saveIssuesAt(createdAfter, componentKey, statuses, splitKeys, rows)
        }
    }
}

/**
 * Returns first page of issue object for the specified date filter.
 * Returned object contains total issue count and the earliest issues.
 */
private fun issuesAt(dateFilter: String, componentKey: String, statuses: String, ruleKeys: List<String>): JSONObject {
    val issuesQuery = "$sonarInstance/api/issues/search" +
            "?componentKeys=$componentKey" +
            "&s=CREATION_DATE" +
            "&statuses=$statuses" +
            "&rules=${ruleKeys.joinToString(",")}" +
            dateFilter
    val sonarResult = getStringFromUrl(issuesQuery)
    val mainObject = parser.parse(sonarResult) as JSONObject
    return mainObject
}

/**
 * Saves to rows all issues created within the specified datetime filter.
 * If project contains more than 10 000 issues with the same key and timestamp, only 10 000 are returned. (MAX_ELASTICSEARCH_RESULTS)
 */
private fun saveIssuesAt(dateFilter: String, componentKey: String, statuses: String, ruleKeys: List<String>, rows: MutableList<Array<String>>) {
    val pageSize = 500
    var page = 0
    do {
        page++
        val issuesQuery = "$sonarInstance/api/issues/search" +
                "?componentKeys=$componentKey" +
                "&s=CREATION_DATE" +
                "&statuses=$statuses" +
                "&rules=${ruleKeys.joinToString(",")}" +
                dateFilter +
                "&ps=$pageSize" +
                "&p=$page"
        if (page * pageSize > MAX_ELASTICSEARCH_RESULTS) {
            println("WARNING: only $MAX_ELASTICSEARCH_RESULTS returned for $issuesQuery")
            break
        }
        val sonarResult = getStringFromUrl(issuesQuery)
        val mainObject = parser.parse(sonarResult) as JSONObject
        val totalIssues = Integer.valueOf(mainObject["total"].toString())
        saveIssuesToRows(mainObject, rows, componentKey)
    } while (page * pageSize < totalIssues)
}

/**
 * Parses sonarqube JSON object and saves it to csv rows.
 */
private fun saveIssuesToRows(mainObject: JSONObject, rows: MutableList<Array<String>>, componentKey: String) {
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
        rows.add(arrayOf(creationDate, closedDate, rule, classname, effortMinutes))
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
            val days = Integer.valueOf(effortString.substringBefore("d","0"))
            val hoursMinutesStr = effortString.substringAfter("d")
            val hours = Integer.valueOf(hoursMinutesStr.substringBefore("h", "0"))
            val minutesStr = hoursMinutesStr.substringAfter("h")
            val minutes = Integer.valueOf(minutesStr.substringBefore("min", "0"))
            return (days*8*60 + hours*60 + minutes).toString()
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
        val query = "$sonarInstance/api/rules/search" +
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
    if (queryURL.length > MAX_URL_LENGTH)
        throw Exception("URLs longer than $MAX_URL_LENGTH are not supported by most systems")
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