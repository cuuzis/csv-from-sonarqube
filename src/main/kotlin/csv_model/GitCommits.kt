package csv_model

import com.opencsv.bean.CsvBindByName

class GitCommits(
        @CsvBindByName(column = "git-date-original") val originalDate: String? = null,
        @CsvBindByName(column = "git-date-sonarqube") val sonarDate: String? = null,
        @CsvBindByName(column = "git-hash") val hash: String? = null,
        @CsvBindByName(column = "git-message") val message: String? = null,
        @CsvBindByName(column = "git-committer") val committer: String? = null,
        @CsvBindByName(column = "git-total-committers") val totalCommitters: Int = 0)