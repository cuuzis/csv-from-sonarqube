import org.json.simple.JSONArray
import org.json.simple.JSONObject
import java.io.BufferedWriter
import java.io.FileWriter
import java.net.URLEncoder

/*
Saves all issues for a given project
 */
fun saveJiraIssues(fileName: String, projectKey: String) {

    val rows = mutableListOf<List<String>>()
    var startAt = 0
    do {
        val jqlString = ("project=$projectKey" +
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

            val priorityObject = fieldsObject["priority"] as JSONObject
            val priority = priorityObject["name"].toString()

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



    BufferedWriter(FileWriter(fileName)).use { bw ->
        val header = "key,creation-date,resolution-date,resolution,type,priority,open-issues,closed-issues"
        bw.write(header)
        bw.newLine()

        for(row in rows) {
            val issueKey = row[0]
            bw.write((row + openIssuesAtKey[issueKey] + closedIssuesAtKey[issueKey]).joinToString(","))
            bw.newLine()
        }
        //bw.write(row.joinToString(","))
        //bw.newLine()

        println("Data saved to $fileName")
    }
}