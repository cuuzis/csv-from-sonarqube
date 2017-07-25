package csv_model.extracted

import com.opencsv.bean.CsvBindByName

class JiraFaults(
        @CsvBindByName(column = "key") val jiraKey: String? = null,
        @CsvBindByName(column = "creation-date") val creationDate: String? = null,
        @CsvBindByName(column = "resolution-date") val resolutionDate: String? = null,
        @CsvBindByName(column = "resolution") val resolution: String? = null,
        @CsvBindByName(column = "type") val type: String? = null,
        @CsvBindByName(column = "priority") val priority: String? = null,
        @CsvBindByName(column = "open-issues") val openIssues: String? = null,
        @CsvBindByName(column = "closed-issues") val closedIssues: String? = null)