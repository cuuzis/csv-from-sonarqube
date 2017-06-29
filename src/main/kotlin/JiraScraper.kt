import org.json.simple.JSONArray
import org.json.simple.JSONObject
import java.io.BufferedWriter
import java.io.FileWriter

/*
Saves all issues for a given project:
issueKey, openDate, closeDate
 */
fun saveJiraIssues() {
    val fileName = "jira-issues.csv"
    val projectKey = "CLI"

    BufferedWriter(FileWriter(fileName)).use { bw ->
        val header = "key,creation_date,resolution_date,priority"
        bw.write(header)
        bw.newLine()

        var startAt = 0
        do {
            val issueQuery = "https://issues.apache.org/jira/rest/api/2/search?jql=" +
                    "project=$projectKey" +
                    "&fields=created,resolutiondate,priority" + //assignee,creator,reporter,priority.name
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
                val priorityObject = fieldsObject["priority"] as JSONObject
                val priority = priorityObject["name"].toString()

                val row = mutableListOf<String>(key, creationDate, resolutionDate, priority)
                bw.write(separatedByCommas(row))
                bw.newLine()
            }
            startAt += maxResults
        } while (startAt < total)
        println("Data saved to $fileName")
    }
}