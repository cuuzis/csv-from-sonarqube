package csv_model.extracted

import com.opencsv.bean.CsvBindByName

class SonarMeasures(
        @CsvBindByName(column = "measure-date") val date: String? = null)