package csv_model

import com.opencsv.bean.CsvBindByName

class SonarMeasures(
        @CsvBindByName(column = "measure-date") val date: String? = null)