import com.opencsv.bean.CsvBindByName

class MergedIssues(
        @CsvBindByName(column = "git-hash") val gitHash: String? = null,
        @CsvBindByName(column = "git-message") val gitMessage: String? = null,
        @CsvBindByName(column = "git-committer") val gitCommitter: String? = null,
        @CsvBindByName(column = "jira-key") val jiraKey: String? = null,
        @CsvBindByName(column = "sonar-rule-key") val sonarRuleKey: String? = null,
        @CsvBindByName(column = "sonar-creation-date") val sonarCreationDate: String? = null,
        @CsvBindByName(column = "sonar-update-date") val sonarUpdateDate: String? = null,
        @CsvBindByName(column = "sonar-component") val sonarComponent: String? = null)