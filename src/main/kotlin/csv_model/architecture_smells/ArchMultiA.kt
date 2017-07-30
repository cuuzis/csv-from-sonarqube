package csv_model.architecture_smells

import com.opencsv.bean.CsvBindByName

class ArchMultiA(
        @CsvBindByName(column = "Name") val name: String? = null,
        @CsvBindByName(column = "ud") val ud: String? = null,
        @CsvBindByName(column = "hl") val hl: String? = null,
        @CsvBindByName(column = "cd") val cd: String? = null)