import gui.MainGui.Companion.logger
import sonarqube.SonarProject
import java.io.File

/**
 * Runs 'Rscript.exe rFile' in the specified folder
 */
fun  runRscript(rFile: File, folder: File) {
    logger.info("Running '${rFile.name}' on " + folder.name.split(File.separatorChar).last())
    val startTime = System.currentTimeMillis()
    val scriptFile = rFile.copyTo(File(folder, rFile.name), overwrite = true)
    // TODO: change to platform independent call:
    val pb = ProcessBuilder("C:\\Program Files\\R\\R-3.3.3\\bin\\x64\\Rscript.exe", scriptFile.name)
            .directory(folder)
            .redirectErrorStream(true)
            .inheritIO()
    val process = pb.start()
    val returnCode = process.waitFor()
    scriptFile.delete()
    if (returnCode != 0)
        throw Exception("Rscript.exe execution returned $returnCode")
    logger.info("R script '${rFile.name}' on ${folder.name.split(File.separatorChar).last()}" +
            " done in ${(System.currentTimeMillis() - startTime)/1000.0} seconds")
}

/**
 * Saves correlation between fault history and commit history
 */
fun saveHistoryCorrelation(sonarProject: SonarProject): String {
    runRscript(File("history-correlation-commits.R"), File(sonarProject.getProjectFolder()))
    return "correlation-commits.csv"
}