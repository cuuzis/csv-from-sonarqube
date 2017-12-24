import gui.MainGui.Companion.logger
import sonarqube.SonarProject
import java.io.*


/**
 * Runs 'Rscript.exe history-correlation-commits.R' in the specified folder
 */
fun  runRscript(executable: String, sonarProject: SonarProject) {
    val startTime = System.currentTimeMillis()
    // copies history-correlation-commits.R script file from resources
    val scriptName = "history-correlation-commits.R"

    val resourcePath = ".." + File.separatorChar + scriptName
    val inputStream = sonarProject.javaClass.getResourceAsStream(resourcePath)

    val scriptFile = File(sonarProject.getProjectFolder() + File.separatorChar + scriptName)
    val outputStream = FileOutputStream(scriptFile)

    val bytes = ByteArray(1024)
    var read = inputStream.read(bytes)
    while (read != -1) {
        outputStream.write(bytes, 0, read)
        read = inputStream.read(bytes)
    }

    // executes Rscript
    val pb = ProcessBuilder(executable, scriptFile.name)
            .directory(File(sonarProject.getProjectFolder()))
            .redirectErrorStream(true)
    val process = pb.start()
    val returnCode = process.waitFor()
    scriptFile.delete()
    if (returnCode != 0)
        throw Exception("Rscript execution returned $returnCode")
    logger.info("R script ${scriptFile.name} done in ${(System.currentTimeMillis() - startTime)/1000.0} seconds")
}

/**
 * Saves correlation between fault history and commit history
 */
fun saveHistoryCorrelation(sonarProject: SonarProject, rScriptExecutable: String): String {
    runRscript(rScriptExecutable, sonarProject)
    return "correlation-commits.csv"
}