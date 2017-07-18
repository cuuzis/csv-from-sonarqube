import com.opencsv.bean.CsvBindByName

class SonarIssues {

    @CsvBindByName(column = "creation-date")
    val creationDate: String? = null

    @CsvBindByName(column = "update-date")
    val updateDate: String? = null

    @CsvBindByName(column = "rule")
    val ruleKey: String? = null

    @CsvBindByName(column = "component")
    val component: String? = null

}