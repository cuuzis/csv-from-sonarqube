import com.opencsv.CSVWriter
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.io.FileWriter
import java.net.URLEncoder

private val parser = JSONParser()
/*
Saves all issues for a given project
 */
fun saveJiraIssues(fileName: String, projectJiraKey: String) {

    val header = listOf("key", "creation-date", "resolution-date", "resolution", "type", "priority", "open-issues", "closed-issues")
    val rows = mutableListOf<List<String>>()
    rows.add(header)

    var startAt = 0
    do {
        val jqlString = ("project=$projectJiraKey" +
                " AND status in (Resolved,Closed)" +
                " AND issuetype=Bug" +
                " AND resolution=Fixed" +
                " ORDER BY created ASC")
        val issueQuery = "https://issues.apache.org/jira/rest/api/2/search" +
                "?jql=" + URLEncoder.encode(jqlString, "UTF-8") +
                "&fields=created,resolutiondate,resolution,issuetype,priority" + //assignee,creator,reporter,priority.name
                "&startAt=$startAt"
        val jiraResult = getStringFromUrl(issueQuery)

        val mainObject = parser.parse(jiraResult) as JSONObject
        val total = Integer.valueOf(mainObject["total"].toString())
        val maxResults = Integer.valueOf(mainObject["maxResults"].toString())

        val issuesArray = mainObject["issues"] as JSONArray
        for (issueObject in issuesArray.filterIsInstance<JSONObject>()) {
            val key = issueObject["key"].toString()
            val fieldsObject = issueObject["fields"] as JSONObject
            val creationDate = fieldsObject["created"].toString()
            val resolutionDate = fieldsObject["resolutiondate"].toString()

            val resolutionObject = fieldsObject["resolution"] as JSONObject
            val resolution = resolutionObject["name"].toString()

            val typeObject = fieldsObject["issuetype"] as JSONObject
            val type = typeObject["name"].toString()

            val priorityObject = fieldsObject["priority"].toString()
            val priority =
                    if (priorityObject == "null")
                        "null"
                    else
                        (fieldsObject["priority"] as JSONObject)["name"].toString()

            val row = mutableListOf<String>(key, creationDate, resolutionDate, resolution, type, priority)
            rows.add(row)
        }
        startAt += maxResults
    } while (startAt < total)

    // sorted by open date
    val openIssuesAtKey = mutableMapOf<String, Int>()
    var openIssues = 0
    for (row in rows) {
        openIssues++
        val issueKey = row[0]
        openIssuesAtKey.put(issueKey, openIssues)
    }
    // sorted by resolution date
    val closedIssuesAtKey = mutableMapOf<String, Int>()
    var closedIssues = 0
    for (row in rows.sortedBy{ it[2] }) {
        closedIssues++
        val issueKey = row[0]
        closedIssuesAtKey.put(issueKey, closedIssues)
    }

    val rows2 = listOf(header) + rows.subList(1, rows.size).map { it + openIssuesAtKey[it[0]].toString() + closedIssuesAtKey[it[0]].toString() }

    // save data to file
    FileWriter(fileName).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(rows2.map { it.toTypedArray() })
        println("Jira issue data saved to $fileName")
    }
}