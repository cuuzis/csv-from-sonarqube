import gui.MainGui.Companion.logger
import sonarqube.SonarProject
import java.io.File

/**
 * Runs 'Rscript.exe rFile' in the specified folder
 */
fun  runRscript(executable: String, rFile: File, folder: File) {
    logger.info("Running '${rFile.name}' on " + folder.name.split(File.separatorChar).last())
    val startTime = System.currentTimeMillis()
    val scriptFile = rFile.copyTo(File(folder, rFile.name), overwrite = true)
    // TODO: change to platform independent call:
    val pb = ProcessBuilder(executable, scriptFile.name)
            .directory(folder)
            .redirectErrorStream(true)
            .inheritIO()
    val process = pb.start()
    val returnCode = process.waitFor()
    scriptFile.delete()
    if (returnCode != 0)
        throw Exception("Rscript execution returned $returnCode")
    logger.info("R script '${rFile.name}' on ${folder.name.split(File.separatorChar).last()}" +
            " done in ${(System.currentTimeMillis() - startTime)/1000.0} seconds")
}

/**
 * Saves correlation between fault history and commit history
 */
fun saveHistoryCorrelation(sonarProject: SonarProject, rScriptExecutable: String): String {
    runRscript(rScriptExecutable, File("history-correlation-commits.R"), File(sonarProject.getProjectFolder()))
    return "correlation-commits.csv"
}