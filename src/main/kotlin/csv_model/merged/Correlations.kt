package csv_model.merged

import com.opencsv.bean.CsvBindByName

class Correlations(
        @CsvBindByName(column = "project") val project: String? = null,
        @CsvBindByName(column = "measureName") val measureName: String? = null,
        @CsvBindByName(column = "mannWhitneyPvalue") val mannWhitneyPvalue: String? = null,
        @CsvBindByName(column = "shapiroWilkPvalue") val shapiroWilkPvalue: String? = null,
        @CsvBindByName(column = "kendallPvalue") val kendallPvalue: String? = null,
        @CsvBindByName(column = "kendallTau") val kendallTau: String? = null,
        @CsvBindByName(column = "pearsonPvalue") val pearsonPvalue: String? = null,
        @CsvBindByName(column = "pearsonCor") val pearsonCor: String? = null)