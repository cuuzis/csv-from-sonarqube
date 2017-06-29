import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.json.simple.parser.ParseException
import java.io.*
import java.net.HttpURLConnection
import java.net.URL


// 1. get all metrics (281)
//
// http://sonar.inf.unibz.it/api/metrics/search

// 2. get component measures (for all metrics? Slow, some, like business_value, are empty)
// cannot be grouped by files like with component_tree: http://sonar.inf.unibz.it/api/measures/component?componentKey=org.apache:commons-cli:src/test/java/org/apache/commons/cli/ParserTestCase.java&metricKeys=complexity,comment_lines
//
// http://sonar.inf.unibz.it/api/measures/search_history?component=org.apache:commons-cli&metrics=business_value,classes,complexity

// 3. build issue history
// when issue is created++
// when issue closes--
// store issue file (group issues by files???)
// http://sonar.inf.unibz.it/api/issues/search?componentKeys=org.apache:commons-cli

// JSON objects as values:
// quality_profile
// quality_gate_details


val parser = JSONParser()
private val MAX_URL_LENGTH = 2000

//ignore args for now
private val projectKey = "org.apache:commons-cli"
val sonarInstance = "http://sonar.inf.unibz.it"

fun main(args: Array<String>) {
    //val fileOut = projectKey.replace("\\.".toRegex(), "-").replace(":".toRegex(), "-") + ".csv"


    try {
        //saveNonemptyMeasures("nonempty_measures.txt")

        //val usefulMetricKeys = readListFromFile("nonempty_measures.txt")
        //saveMeasureHistory(usefulMetricKeys, "measures.csv")

        //saveIssueHistory("issues.csv")

        //mergeMeasuresWithIssues("measures.csv", "issues.csv", "measures-and-issues.csv")

        saveJiraIssues()

    } catch (e: ParseException) {
        println("JSON parsing error")
        e.printStackTrace()
    }
}


private fun saveNonemptyMeasures(fileName: String) {
    val metricKeys = getMetricKeys()
    val usefulMeasures = mutableListOf<String>()
    val measureQuery = "$sonarInstance/api/measures/search_history" +
            "?component=" + projectKey +
            "&metrics="
    while (!metricKeys.isEmpty()) {
        var query = measureQuery
        while (!metricKeys.isEmpty() && (query.length + metricKeys.first().length < MAX_URL_LENGTH)) {
            if (query == measureQuery)
                query += metricKeys.removeAt(0)
            else
                query += "," + metricKeys.removeAt(0)
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
    println("Nonempty measures: ${usefulMeasures.size}")

    BufferedWriter(FileWriter(fileName)).use { bw ->
        for (key in usefulMeasures) {
            bw.write(key)
            bw.newLine()
        }
    }
}

private fun getMetricKeys(): MutableList<String> {
    val metricsQuery = "$sonarInstance/api/metrics/search?ps=1000"
    val metricsResult = getStringFromUrl(metricsQuery)
    val metricsObject = parser.parse(metricsResult) as JSONObject
    val metricsCount = Integer.parseInt(metricsObject["total"].toString())
    println("Metrics found: $metricsCount")
    val metricsKeys = mutableListOf<String>()
    val metricsArray = metricsObject["metrics"] as JSONArray
    for (metricObject in metricsArray.filterIsInstance<JSONObject>()) {
        metricsKeys.add(metricObject["key"].toString())
    }
    println(metricsKeys)
    assert(metricsKeys.size == metricsCount)
    return metricsKeys
}

private fun saveMeasureHistory(metricKeys: List<String>, fileName: String) {
    val measureQuery = "$sonarInstance/api/measures/search_history" +
            "?component=" + projectKey +
            "&ps=1000" +
            "&metrics=" + separatedByCommas(metricKeys)

    val measureResult = getStringFromUrl(measureQuery)
    val measureObject = parser.parse(measureResult) as JSONObject
    val pagingObject = measureObject["paging"] as JSONObject
    val measureCount = Integer.parseInt(pagingObject["total"].toString())
    println("Measures found: $measureCount")
    val measureArray = measureObject["measures"] as JSONArray
    val measureMap = sortedMapOf<String, Array<String>>()

    for (metricObject in measureArray.filterIsInstance<JSONObject>()) {
        val measureKey = metricObject["metric"].toString()
        val measureHistory = metricObject["history"] as JSONArray
        for (measureEntry in measureHistory.filterIsInstance<JSONObject>()) {
            val date = measureEntry["date"].toString()
            val value = measureEntry["value"].toString()
            if (!measureMap.containsKey(date)) {
                measureMap.put(date, Array(metricKeys.size, init = { _ -> "0" }))
            }
            val valueArray = measureMap[date]
            //if (measureKey == "quality_profiles" || measureKey == "quality_gate_details")
            valueArray!![metricKeys.indexOf(measureKey)] = value.replace(",", ";")
        }
    }
    BufferedWriter(FileWriter(fileName)).use { bw ->
        val columns: List<String> = listOf<String>("date") + metricKeys
        bw.write(separatedByCommas(columns))
        bw.newLine()
        for ((key, values) in measureMap) {
            bw.write("$key," + separatedByCommas(values.toList()))
            bw.newLine()
        }
    }
}

private fun saveIssueHistory(fileName: String) {
    BufferedWriter(FileWriter(fileName)).use { bw ->
        val header = "creation_date,update_date,rule,component"
        //println(header)
        bw.write(header)
        bw.newLine()

        var createdAfter = "1900-01-01T01:01:01+0100"
        val pageSize = 500
        do {
            var issuesArraySize: Int
            var currentPage = 1
            do {
                val issuesQuery = "$sonarInstance/api/issues/search" +
                        "?componentKeys=" + projectKey +
                        "&s=CREATION_DATE" +
                        "&statuses=CLOSED,OPEN" +
                        "&createdAfter=${createdAfter.replace("+", "%2B")}" +
                        "&ps=$pageSize" +
                        "&p=$currentPage"
                val sonarResult = getStringFromUrl(issuesQuery)
                val mainObject = parser.parse(sonarResult) as JSONObject

                val issuesArray = mainObject["issues"] as JSONArray
                issuesArraySize = issuesArray.size

                if (issuesArraySize == 0)
                    break

                val firstDateInArray = (issuesArray.first() as JSONObject)["creationDate"].toString()
                val lastDateInArray = (issuesArray.last() as JSONObject)["creationDate"].toString()

                for (issueObject in issuesArray.filterIsInstance<JSONObject>()) {
                    val creationDate = issueObject["creationDate"].toString()
                    val updateDate = issueObject["updateDate"].toString()
                    val rule = issueObject["rule"].toString()
                    val component = issueObject["component"].toString()
                    val classname = component.replaceFirst((projectKey + ":").toRegex(), "")
                    val status = issueObject["status"].toString()

                    createdAfter = creationDate
                    if (creationDate == lastDateInArray && firstDateInArray != lastDateInArray)
                        break

                    val closedDate = if (status == "CLOSED")
                        updateDate
                    else
                        ""
                    val row = mutableListOf<String>(creationDate, closedDate, rule, classname)

                    bw.write(separatedByCommas(row))
                    bw.newLine()
                }
                currentPage++

            } while (firstDateInArray == lastDateInArray && issuesArraySize > 0)

        } while (issuesArraySize > 0)
        println("Data saved to $fileName")
    }
}

private fun mergeMeasuresWithIssues(measuresFile: String, issuesFile: String, combinedFile: String) {
    val ruleKeys = mutableSetOf<String>()
    val issuesByDateOpened = mutableMapOf<String, MutableList<String>>()
    val issuesByDateClosed = mutableMapOf<String, MutableList<String>>()

    val issueCSV = readListFromFile(issuesFile)
    for (line in issueCSV.subList(1, issueCSV.lastIndex)) {
        val creation_date = line.split(",")[0]
        val update_date = line.split(",")[1]
        val ruleKey = line.split(",")[2]
        //val component = line.split(",")[3]
        ruleKeys.add(ruleKey)

        issuesByDateOpened.computeIfAbsent(creation_date, { _ -> mutableListOf() })
                .add(ruleKey)
        issuesByDateClosed.computeIfAbsent(update_date, { _ -> mutableListOf() })
                .add(ruleKey)
    }

    val measureCSV = readListFromFile(measuresFile)

    println("Measure cols: ${measureCSV[0].split(",").size}")
    println("Issue Cols: ${ruleKeys.size}")
    BufferedWriter(FileWriter(combinedFile)).use { bw ->
        bw.write("${measureCSV[0]},${separatedByCommas(ruleKeys.toList())}")
        bw.newLine()


        val currentIssueCount = mutableMapOf<String, String>()
        for (ruleKey in ruleKeys) {
            currentIssueCount[ruleKey] = "0"
        }

        for (line in measureCSV.subList(1, measureCSV.lastIndex)) {
            val measureDate = line.split(",")[0]
            val openedIssues = issuesByDateOpened.getOrDefault(measureDate, mutableListOf())
            for (ruleKey in openedIssues) {
                currentIssueCount[ruleKey] = (Integer.valueOf(currentIssueCount[ruleKey]!!) + 1).toString()
            }

            val closedIssues = issuesByDateClosed.getOrDefault(measureDate, mutableListOf())
            for (ruleKey in closedIssues) {
                currentIssueCount[ruleKey] = (Integer.valueOf(currentIssueCount[ruleKey]!!) - 1).toString()
            }

            bw.write("$line,${separatedByCommas(currentIssueCount.values.toList())}")
            bw.newLine()
        }
    }
}

fun getStringFromUrl(queryURL: String): String {
    assert(queryURL.length <= MAX_URL_LENGTH) // URLS over 2000 are not supported
    println("\nSending 'GET' request to URL : " + queryURL)
    val url = URL(queryURL)
    val con = url.openConnection() as HttpURLConnection
    con.requestMethod = "GET"
    val responseCode = con.responseCode
    println("Response Code : " + responseCode)

    val `in` = BufferedReader(InputStreamReader(con.inputStream))
    //var inputLine: String
    val stringBuilder = StringBuilder()
    do {
        val inputLine = `in`.readLine()
        if (inputLine != null) {
            stringBuilder.append(inputLine)
        }
    } while (inputLine != null)
    return stringBuilder.toString()
}

private fun readListFromFile(filename: String): List<String> {
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

fun separatedByCommas(list: List<String>): String {
    //System.out.println(Arrays.toString(list.toArray()));
    var result = ""
    var separator = ""
    for (elem in list) {
        result += separator + elem
        separator = ","
    }
    return result
}
