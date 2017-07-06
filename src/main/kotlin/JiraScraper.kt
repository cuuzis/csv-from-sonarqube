import org.json.simple.JSONArray
import org.json.simple.JSONObject
import java.io.BufferedWriter
import java.io.FileWriter

/*
Saves all issues for a given project
 */
fun saveJiraIssues(fileName: String, projectKey: String) {

    BufferedWriter(FileWriter(fileName)).use { bw ->
        val header = "key,creation-date,resolution-date,resolution,type,priority"
        bw.write(header)
        bw.newLine()

        var startAt = 0
        do {
            val issueQuery = "https://issues.apache.org/jira/rest/api/2/search" +
                    "?jql=project=$projectKey AND status in (Resolved,Closed)".replace(" ", "%20") +
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
                bw.write(row.joinToString(","))
                bw.newLine()
            }
            startAt += maxResults
        } while (startAt < total)
        println("Data saved to $fileName")
    }
}