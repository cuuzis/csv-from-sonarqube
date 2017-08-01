import com.opencsv.CSVWriter
import com.opencsv.bean.CsvToBeanBuilder
import csv_model.merged.Correlations
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * Runs R scripts treating the whole data set as a single project.
 *
 * Takes a long time:
 * R script 'correlation-mas-ud.R' on extraction done in 3569.168 seconds
 * R script 'correlation-mas-hl.R' on extraction done in 3725.133 seconds
 * R script 'correlation-mas-cd.R' on extraction done in 4281.505 seconds
 * R script 'correlation-mas-exists.R' on extraction done in 4369.807 seconds (72 minutes)
 * R script 'correlation-cycle-size.R' on extraction done in 20509.04 seconds
 * R script 'correlation-cycle-classes.R' on extraction done in 20626.744 seconds
 * R script 'correlation-cycle-exists.R' on extraction done in 20758.265 seconds
 * Execution completed in 20758.312 seconds (345 minutes = 6h)
 */
fun calculateFullDatasetCorrelation(projectKeys: List<String>) {
    // merge architecture smells for projects (~30s)...
    mergeExtractedCsvFiles(projectKeys, "cycles-issues-by-class.csv")
    mergeExtractedCsvFiles(projectKeys, "cycles-issues-by-cycle.csv")
    mergeExtractedCsvFiles(projectKeys, "mas-issues-by-package.csv")

    // ...and calculate correlations, takes very long time (188 columns, for each: ~2min cycle, ~7min mas):
    val rFileList = listOf(
            "correlation-cycle-size.R",
            "correlation-cycle-classes.R",
            "correlation-cycle-exists.R",
            "correlation-mas-ud.R",
            "correlation-mas-hl.R",
            "correlation-mas-cd.R",
            "correlation-mas-exists.R")
    rFileList.parallelStream().forEach { rFile -> runRscript(File(rFile), File(workDir)) }
}

/**
 * Runs R scripts in project folders
 */
fun calculateCorrelationsForProjects(projectKeys: List<String>) {
    projectKeys.parallelStream().forEach { projectKey ->
        val folder = File(getProjectFolder(projectKey))
        runRscript(File("correlation-cycle-size.R"), folder)
        runRscript(File("correlation-cycle-classes.R"), folder)
        runRscript(File("correlation-cycle-exists.R"), folder)
        runRscript(File("correlation-mas-ud.R"), folder)
        runRscript(File("correlation-mas-hl.R"), folder)
        runRscript(File("correlation-mas-cd.R"), folder)
        runRscript(File("correlation-mas-exists.R"), folder)
    }
}

/**
 * Merges results from R scripts for projects
 */
fun mergeCorrelationsByProject(projectKeys: List<String>) {
    mergeExtractedSameCsvFiles(projectKeys, "correlation-cycle-size.csv")
    mergeExtractedSameCsvFiles(projectKeys, "correlation-cycle-classes.csv")
    mergeExtractedSameCsvFiles(projectKeys, "correlation-cycle-exists.csv")
    mergeExtractedSameCsvFiles(projectKeys, "correlation-mas-ud.csv")
    mergeExtractedSameCsvFiles(projectKeys, "correlation-mas-hl.csv")
    mergeExtractedSameCsvFiles(projectKeys, "correlation-mas-cd.csv")
    mergeExtractedSameCsvFiles(projectKeys, "correlation-mas-exists.csv")
}

/**
 * Extracts useful correlations from correlation files
 */
fun summariseCorrelations(outFile: String) {
    println("Finding significant correlations")
    val correlationFiles = listOf(
            "by-project-correlation-cycle-size.csv",
            "by-project-correlation-cycle-classes.csv",
            "by-project-correlation-cycle-exists.csv",
            "by-project-correlation-mas-ud.csv",
            "by-project-correlation-mas-hl.csv",
            "by-project-correlation-mas-cd.csv",
            "by-project-correlation-mas-exists.csv"
    )
    val rows = mutableListOf<Array<String>>()
    for (correlationFile in correlationFiles) {
        rows.addAll(findUsefulCorrelations(correlationFile))
    }
    FileWriter(workDir + outFile).use { fw ->
        val csvWriter = CSVWriter(fw)
        val header = arrayOf("measure", "issueName", "infectedProjects", "pvalue005", "correlation05", "correlation06", "projectsWithCorrelation")
        csvWriter.writeNext(header)
        csvWriter.writeAll(rows)
    }
    println("Significant correlations saved to ${workDir + outFile}")
}

/**
 * Filters and counts issue correlations, finding rows with kendallPvalue < 0.05 and kendallCorrelationTau > 0.5
 */
private fun findUsefulCorrelations(csvFile: String): List<Array<String>> {
    val correlationBeans = CsvToBeanBuilder<Correlations>(FileReader(workDir + csvFile))
            .withType(Correlations::class.java).build().parse()
            .map { it as Correlations }
    val issueKeys = mutableSetOf<String>()
    correlationBeans.mapTo(issueKeys) { it.issueName.orEmpty() }
    val rows = mutableListOf<Array<String>>()
    for (issue in issueKeys) {
        val infectedProjects = correlationBeans.filter { it.issueName == issue }
        val measurableOccurrences = infectedProjects.filter { it.kendallPvalue?.toDoubleOrNull() != null && it.kendallPvalue.toDouble() < 0.05 }
        val correlation05Occurrences = measurableOccurrences.filter { it.kendallTau?.toDoubleOrNull() != null && it.kendallTau.toDouble() > 0.5 }
        val correlation06Occurrences = measurableOccurrences.count { it.kendallTau?.toDoubleOrNull() != null && it.kendallTau.toDouble() > 0.6 }
        rows.add(arrayOf(
                csvFile.removePrefix("by-project-correlation-").removeSuffix(".csv"),
                issue,
                infectedProjects.count().toString(),
                measurableOccurrences.count().toString(),
                correlation05Occurrences.count().toString(),
                correlation06Occurrences.toString(),
                correlation05Occurrences.joinToString(";") { it.project.orEmpty() }
        ))
    }
    return rows // sorted by:       correlation06      correlation05      pvalue005          infectedProjects
            .sortedWith(compareBy({ it[5].toInt() }, { it[4].toInt() }, { it[3].toInt() }, { it[2].toInt() } ))
            .reversed()
}

/**
 * Runs 'Rscript.exe rFile' in the specified folder
 */
fun  runRscript(rFile: File, folder: File) {
    println("Running '${rFile.name}' on " + folder.name.split(File.separatorChar).last())
    val startTime = System.currentTimeMillis()
    val scriptFile = rFile.copyTo(File(folder, rFile.name), overwrite = true)
    val pb = ProcessBuilder("C:\\Program Files\\R\\R-3.3.3\\bin\\x64\\Rscript.exe", scriptFile.name)
            .directory(folder)
            .redirectErrorStream(true)
            .inheritIO()
    val process = pb.start()
    val returnCode = process.waitFor()
    scriptFile.delete()
    if (returnCode != 0)
        throw Exception("Rscript.exe execution returned $returnCode")
    println("R script '${rFile.name}' on ${folder.name.split(File.separatorChar).last()}" +
            " done in ${(System.currentTimeMillis() - startTime)/1000.0} seconds")
}