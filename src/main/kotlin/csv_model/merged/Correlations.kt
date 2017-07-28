package csv_model.merged

import com.opencsv.bean.CsvBindByName

class Correlations(
        @CsvBindByName(column = "projectName") val projectName: String? = null,
        @CsvBindByName(column = "issueName") val issueName: String? = null,
        @CsvBindByName(column = "mannWhitneyPvalue") val mannWhitneyPvalue: String? = null,
        @CsvBindByName(column = "shapiroWilkPvalue") val shapiroWilkPvalue: String? = null,
        @CsvBindByName(column = "kendallPvalue") val kendallPvalue: String? = null,
        @CsvBindByName(column = "kendallTau") val kendallTau: String? = null,
        @CsvBindByName(column = "pearsonPvalue") val pearsonPvalue: String? = null,
        @CsvBindByName(column = "pearsonCor") val pearsonCor: String? = null)