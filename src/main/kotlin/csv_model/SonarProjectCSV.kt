package csv_model

import com.opencsv.bean.CsvBindByName

data class SonarProjectCSV(
        @CsvBindByName(column = "project-key") val projectKey: String? = null,
        @CsvBindByName(column = "git-link") val gitLink: String? = null,
        @CsvBindByName(column = "jira-link") val jiraLink: String? = null )