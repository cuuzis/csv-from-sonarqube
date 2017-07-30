package csv_model.architecture_smells

import com.opencsv.bean.CsvBindByName

class ArchClassCycles(
        @CsvBindByName(column = "IdCycle") val cycleId: String? = null,
        @CsvBindByName(column = "numVertices") val numVertices: String? = null,
        @CsvBindByName(column = "ElementList") val elementList: String? = null) {

    fun getComponentList(): List<String> {
        val unformattedComponents = elementList?.split(",") ?: listOf()
        return unformattedComponents.map { it.substringBefore("\$",it).replace(".","/") + ".java" }
    }
}