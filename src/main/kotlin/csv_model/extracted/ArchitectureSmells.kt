package csv_model.extracted

import com.opencsv.bean.CsvBindByName

class ArchitectureSmells(
        @CsvBindByName(column = "IdCycle") val cycleId: String? = null,
        @CsvBindByName(column = "ElementList") val elementList: String? = null) {

    fun getComponentList(): List<String> {
        val unformattedComponents = elementList?.split(",") ?: listOf()
        return unformattedComponents.map { it.substringBefore("\$",it).replace(".","/") + ".java" }
    }
}