import com.opencsv.CSVWriter
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import sonarqube.SonarProject
import sonarqube.getStringFromUrl
import java.io.File
import java.io.FileWriter
import java.net.URLEncoder

private val parser = JSONParser()

/**
 * Downloads and saves project's jira issues (faults)
 */
fun saveJiraIssues(sonarProject: SonarProject): String {
    val projectJiraKey = extractJiraKeyFromLink(sonarProject.getJiraLink())
    val jiraInstance = extractJiraInstanceFromLink(sonarProject.getJiraLink())


    val header = listOf("key", "creation-date", "resolution-date", "resolution", "type", "priority", "fixversions",
            "open-issues", "closed-issues")
    val rows = mutableListOf<List<String>>()
    rows.add(header)

    var startAt = 0
    do {
        val jqlString = ("project=\"$projectJiraKey\"" +
                " AND status in (Resolved,Closed)" +
                " AND issuetype=Bug" +
                " AND resolution=Fixed" +
                " ORDER BY created ASC")
        val issueQuery = "$jiraInstance/rest/api/2/search" +
                "?jql=" + URLEncoder.encode(jqlString, "UTF-8") +
                "&fields=created,resolutiondate,resolution,issuetype,priority,fixVersions" + //assignee,creator,reporter,priority.name
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

            val fixVersionsArray = fieldsObject["fixVersions"] as JSONArray
            val fixVersions = fixVersionsArray.filterIsInstance<JSONObject>()
                    .map { it["name"] }
                    .joinToString(";")


            val row = mutableListOf<String>(key, creationDate, resolutionDate, resolution, type, priority, fixVersions)
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
    val fileName = sonarProject.getProjectFolder() + File.separatorChar + "jira-faults.csv"
    FileWriter(fileName).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(rows2.map { it.toTypedArray() })
        println("Jira issue data saved to $fileName")
    }
    return fileName
}

/**
 * Returns project key part from a link
 */
private fun extractJiraKeyFromLink(jiraLink: String): String {
    return jiraLink.split("/").last()
}

/**
 * Returns domain/instance part from a link
 */
private fun extractJiraInstanceFromLink(jiraLink: String): String {
    return jiraLink
            .split("/projects").first()
            .split("/browse").first()
}