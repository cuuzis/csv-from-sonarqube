package csv_model.extracted

import com.opencsv.bean.CsvBindByName

class SonarIssues(
        @CsvBindByName(column = "creationDate") val creationDate: String? = null,
        @CsvBindByName(column = "updateDate") val updateDate: String? = null,
        @CsvBindByName(column = "closeDate") val closeDate: String? = null,
        @CsvBindByName(column = "type") val type: String? = null,
        @CsvBindByName(column = "rule") val ruleKey: String? = null,
        @CsvBindByName(column = "component") val component: String? = null,
        @CsvBindByName(column = "severity") val severity: String? = null,
        @CsvBindByName(column = "project") val project: String? = null,
        @CsvBindByName(column = "startLine") val startLine: String? = null,
        @CsvBindByName(column = "endLine") val endLine: String? = null,
        @CsvBindByName(column = "resolution") val resolution: String? = null,
        @CsvBindByName(column = "status") val status: String? = null,
        @CsvBindByName(column = "message") val message: String? = null,
        @CsvBindByName(column = "effort") val effort: Int? = null,
        @CsvBindByName(column = "debt") val debt: String? = null,
        @CsvBindByName(column = "author") val author: String? = null)