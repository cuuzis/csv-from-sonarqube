package sonarqube

import com.opencsv.CSVWriter
import com.opencsv.bean.CsvToBeanBuilder
import csv_model.extracted.SonarIssues
import csv_model.extracted.SonarMeasures
import gui.MainGui.Companion.logTextArea
import gui.MainGui.Companion.logger
import info
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
 * Returns a list of projects (key, name) containing a string
 */
fun getProjectsOnServer(sonarServer: SonarServer): List<SonarProject> {
    logger.info(logTextArea, "Requesting projects containing ")
    val projectList = mutableListOf<SonarProject>()
    val pageSize = 500
    var currentPage = 0
    do {
        currentPage++
        val query = "${sonarServer.serverAddress}/api/components/search" +
                "?qualifiers=TRK" +
                "&ps=$pageSize" +
                "&p=$currentPage"
        val response = getStringFromUrl(query)
        val mainObject = parser.parse(response) as JSONObject
        val pagingObject = mainObject["paging"] as JSONObject
        val componentArray = mainObject["components"] as JSONArray
        val projectsOnPage = componentArray.filterIsInstance<JSONObject>().map {
            SonarProject(sonarServer, it["key"].toString(), it["name"].toString())
        }
        projectList.addAll(projectsOnPage)
    } while (pageSize * currentPage < pagingObject["total"].toString().toInt())
    return projectList
}

/**
 * Saves current measures for a project in a .csv file
 */
fun saveMeasures(project: SonarProject): String {
    val measureMap = mutableMapOf<String,String>()
    val measureQuery = "${project.sonarServer.serverAddress}/api/measures/component" +
            "?componentKey=${project.getKey()}" +
            "&metricKeys="
    val metricKeysLeft = getMetricKeys(project).toMutableList()
    while (!metricKeysLeft.isEmpty()) {
        var query = measureQuery
        while (!metricKeysLeft.isEmpty() && (query.length + metricKeysLeft.first().length < MAX_URL_LENGTH)) {
            if (query == measureQuery) {
                query += metricKeysLeft.removeAt(0)
            } else {
                query += "," + metricKeysLeft.removeAt(0)
            }
        }
        val measureResult = getStringFromUrl(query)
        val mainObject = parser.parse(measureResult) as JSONObject
        val measureObject = mainObject["component"] as JSONObject
        val measureArray = measureObject["measures"] as JSONArray
        for (metricObject in measureArray.filterIsInstance<JSONObject>()) {
            val measureKey = metricObject["metric"].toString()
            val measureValue = metricObject["value"].toString()
            measureMap.put(measureKey, measureValue)
        }
    }

    // save data to file
    val measureMapOrdered = measureMap.toSortedMap()
    val header = measureMapOrdered.keys
    val row = measureMapOrdered.values
    val fileName = project.getProjectFolder() + "current-measures.csv"
    FileWriter(fileName).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeNext(header.toTypedArray())
        csvWriter.writeNext(row.toTypedArray())
    }
    logger.info(logTextArea, "Sonarqube current measures saved to $fileName")
    return fileName
}

/**
 * Saves past measures for a project in a .csv file
 */
fun saveMeasureHistory(project: SonarProject): String {
    //split into batches to overcome large URL problems
    val metricKeysAll = getNonemptyMetricKeys(project)
    val metricKeyBatches = mutableListOf<MutableList<String>>()
    var batch = 0
    for (metricKey in metricKeysAll) {
        if (metricKeyBatches.getOrNull(batch) == null) {
            metricKeyBatches.add(batch, mutableListOf())
        }
        metricKeyBatches[batch].add(metricKey)
        if (metricKeyBatches[batch].joinToString(",").length + 500 > MAX_URL_LENGTH) {
            batch++
        }
    }

    val measureMap = sortedMapOf<String, Array<String>>()
    for (metricKeys in metricKeyBatches) {
        val pageSize = 500
        var currentPage = 0
        do {
            currentPage++
            val measureQuery = "${project.sonarServer.serverAddress}/api/measures/search_history" +
                    "?component=${project.getKey()}" +
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
                    val date = getInstantFromSonarDate(measureEntry["date"].toString()).toString()
                    val value = measureEntry["value"].toString()
                    measureMap.putIfAbsent(date, Array(metricKeys.size, init = { _ -> "0" }))
                    val valueArray = measureMap[date]
                    valueArray!![metricKeys.indexOf(measureKey)] = value
                }
            }
        } while (pageSize * currentPage < pagingObject["total"].toString().toInt())
    }

    // save data to file
    val header = listOf<String>("measure-date") + metricKeysAll
    val rows = mutableListOf<List<String>>()
    for ((key, values) in measureMap) {
        rows.add((listOf(key) + values))
    }

    val fileName = project.getProjectFolder() + "measure-history.csv"

    // append instead of overwrite
    val rowsToSave =
            if (File(fileName).exists()) {
                val reader = FileReader(fileName)
                val measuresAlreadySaved = CsvToBeanBuilder<SonarMeasures>(reader)
                        .withType(SonarMeasures::class.java).build().parse()
                        .map { (it as SonarMeasures).date }
                rows.filterNot { measuresAlreadySaved.contains(it[0]) } // it[0] == "measure-date"
            } else {
                listOf(header) + rows
            }

    FileWriter(fileName, true).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(rowsToSave.map { it.toTypedArray() })
    }
    logger.info(logTextArea, "Sonarqube measure history saved to $fileName")
    return fileName
}

/**
 * Tests which past measures contain nonempty values and are therefore useful
 */
private fun getNonemptyMetricKeys(project: SonarProject): List<String> {
    val usefulMeasures = mutableListOf<String>()
    val measureQuery = "${project.sonarServer.serverAddress}/api/measures/search_history" +
            "?component=${project.getKey()}" +
            "&metrics="
    val metricKeysLeft = getMetricKeys(project).toMutableList()
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
    logger.info(logTextArea, "Nonempty are ${usefulMeasures.size} sonarqube measures: $usefulMeasures")
    return usefulMeasures
}

/**
 * Returns all measures available on the server
 */
private fun getMetricKeys(project: SonarProject): List<String> {
    val metricsQuery = "${project.sonarServer.serverAddress}/api/metrics/search?ps=500"
    val metricsResult = getStringFromUrl(metricsQuery)
    val metricsObject = parser.parse(metricsResult) as JSONObject
    val metricsCount = Integer.parseInt(metricsObject["total"].toString())
    val metricsKeys = mutableListOf<String>()
    val metricsArray = metricsObject["metrics"] as JSONArray
    metricsArray.filterIsInstance<JSONObject>().mapTo(metricsKeys) { it["key"].toString() }
    logger.info(logTextArea, "Found $metricsCount sonarqube measures: $metricsKeys")
    if (metricsKeys.size != metricsCount)
        throw Exception("Saved ${metricsKeys.size} measure keys, but there are $metricsCount found")
    return metricsKeys
}


/**
 * Saves Sonarqube project's issue history in a .csv file. Returns the name of the file.
 */
fun saveIssues(project: SonarProject, statuses: String): String {
    logger.info(logTextArea, "Extracting issues for ${project.getName()}")
    val startTime = System.currentTimeMillis()

    val rows = mutableListOf<Array<String>>()
    saveIssuesForKeys(project.sonarServer.getRuleKeys(), project, statuses, rows)

    val fileName = project.getProjectFolder() + "sonar-issues.csv"

    // append instead of overwrite
    val rowsToSave: List<Array<String>> =
            if (File(fileName).exists()) {
                val reader = FileReader(fileName)
                val issuesAlreadySaved = CsvToBeanBuilder<SonarIssues>(reader)
                        .withType(SonarIssues::class.java).build().parse()
                        .map { (it as SonarIssues).creationDate }
                rows.filterNot { issuesAlreadySaved.contains(it[0]) } // it[0] == "creation-date"
                        .sortedBy { it[0] } // sorted by "creation-date"
            } else {
                val header = arrayOf("creation-date", "update-date", "rule", "component", "effort")
                listOf(header) + rows.sortedBy { it[0] } // sorted by "creation-date"
            }

    FileWriter(fileName, true).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(rowsToSave)
    }
    logger.info(logTextArea, "Sonarqube issues saved to $fileName, extraction took ${(System.currentTimeMillis()-startTime)/1000.0} seconds")
    return fileName
}

/**
 * Saves issues to rows.
 */
private fun saveIssuesForKeys(ruleKeys: List<String>, project: SonarProject, statuses: String, rows: MutableList<Array<String>>) {
    for (splitKeys in splitIntoBatches(ruleKeys, 20)) {
        var createdAfter = "&createdAfter=${URLEncoder.encode("1900-01-01T01:01:01+0100", "UTF-8")}"
        var issuesLeft = issuesAt(createdAfter, project, statuses, splitKeys)
        if (Integer.valueOf(issuesLeft["total"].toString()) > MAX_ELASTICSEARCH_RESULTS && ruleKeys.size > 1) { // split by rule keys
            for (splitKeysSmaller in splitIntoBatches(splitKeys, ruleKeys.size / 2)) {
                saveIssuesForKeys(splitKeysSmaller, project, statuses, rows)
            }
        } else if (Integer.valueOf(issuesLeft["total"].toString()) > 0) {
            while (Integer.valueOf(issuesLeft["total"].toString()) > MAX_ELASTICSEARCH_RESULTS) { // split by dates
                val issuesArray = issuesLeft["issues"] as JSONArray
                val firstIssue = issuesArray.filterIsInstance<JSONObject>().first()
                val firstIssueDate = firstIssue["creationDate"].toString()
                val createdAt = "&createdAt=${URLEncoder.encode(firstIssueDate, "UTF-8")}"
                saveIssuesAt(createdAt, project, statuses, splitKeys, rows)

                val nextDate = getSonarDateFromInstant(getInstantFromSonarDate(firstIssueDate).plusSeconds(1))
                createdAfter = "&createdAfter=${URLEncoder.encode(nextDate, "UTF-8")}"
                issuesLeft = issuesAt(createdAfter, project, statuses, splitKeys)
            }
            saveIssuesAt(createdAfter, project, statuses, splitKeys, rows)
        }
    }
}

/**
 * Returns first page of issue object for the specified date filter.
 * Returned object contains total issue count and the earliest issues.
 */
private fun issuesAt(dateFilter: String, project: SonarProject, statuses: String, ruleKeys: List<String>): JSONObject {
    val issuesQuery = "${project.sonarServer.serverAddress}/api/issues/search" +
            "?componentKeys=${project.getKey()}" +
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
 * If project contains more than 10 000 issues with the same key and timestamp, only 10 000 are returned. (sonarqube.MAX_ELASTICSEARCH_RESULTS)
 */
private fun saveIssuesAt(dateFilter: String, project: SonarProject, statuses: String, ruleKeys: List<String>, rows: MutableList<Array<String>>) {
    val pageSize = 500
    var page = 0
    do {
        page++
        val issuesQuery = "${project.sonarServer.serverAddress}/api/issues/search" +
                "?componentKeys=${project.getKey()}" +
                "&s=CREATION_DATE" +
                "&statuses=$statuses" +
                "&rules=${ruleKeys.joinToString(",")}" +
                dateFilter +
                "&ps=$pageSize" +
                "&p=$page"
        if (page * pageSize > MAX_ELASTICSEARCH_RESULTS) {
            logger.info(logTextArea, "WARNING: only ${MAX_ELASTICSEARCH_RESULTS} returned for $issuesQuery")
            break
        }
        val sonarResult = getStringFromUrl(issuesQuery)
        val mainObject = parser.parse(sonarResult) as JSONObject
        val totalIssues = Integer.valueOf(mainObject["total"].toString())
        saveIssuesToRows(mainObject, rows, project.getKey())
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
 * Get a list of rule keys
 */
fun getRuleKeys(sonarInstance: String, params: String = ""): List<String> {
    logger.info(logTextArea, "Requesting rule keys")
    val result = mutableListOf<String>()
    val pageSize = 500
    var page = 0
    do {
        page++
        val query = "$sonarInstance/api/rules/search" +
                "?ps=$pageSize" +
                "&p=$page" +
                params
        val response = getStringFromUrl(query)
        val mainObject = parser.parse(response) as JSONObject
        val ruleArray = mainObject["rules"] as JSONArray
        result.addAll(
                ruleArray.filterIsInstance<JSONObject>().map { it["key"].toString() })
        val lastItem = page * pageSize
    } while (Integer.parseInt(mainObject["total"].toString()) > lastItem)
    return result
}

/**
 * Get a list of tags on SQ instance
 */
fun getTags(sonarInstance: String): List<String> {
    logger.info(logTextArea, "Requesting tags")
    val query = "$sonarInstance/api/rules/tags"
    val response = getStringFromUrl(query)
    val mainObject = parser.parse(response) as JSONObject
    val tagArray = mainObject["tags"] as JSONArray
    return tagArray.map { it.toString() }
}

/*
Parses an URL request as a string
 */
fun getStringFromUrl(queryURL: String): String {
    logger.info(logTextArea, "Sending 'GET' request to URL: " + queryURL)
    if (queryURL.length > MAX_URL_LENGTH) {
        throw Exception("URLs longer than $MAX_URL_LENGTH are not supported by most systems")
    }
    val url = URL(queryURL)
    val con = url.openConnection() as HttpURLConnection
    con.requestMethod = "GET"
    val responseCode = con.responseCode
    if (responseCode != 200) {
        throw Exception("Response Code : " + responseCode)
    }
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