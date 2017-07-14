import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.omg.CosNaming.NamingContextExtPackage.StringNameHelper
import java.io.BufferedWriter
import java.io.FileWriter
import java.time.Instant
import java.time.temporal.ChronoUnit


//val localPath = File("tmpGitRepo")

fun saveGitCommits(fileName: String, repositoryURL: String) {
    // clone repository into temp folder
    //val git = cloneRemoteRepository(repositoryURL, File("tmpGitRepo"))
    val git = openLocalRepository(File("tmpGitRepo/.git"))

    val logEntries = git.log()
            .call()
            .reversed()

    val logDatesRaw = mutableListOf<Instant>()
    val logHashes = mutableListOf<String>()
    val logMessagesShort = mutableListOf<String>()
    for(log in logEntries) {
        logDatesRaw.add(Instant.ofEpochSecond(log.commitTime.toLong()))
        logHashes.add(log.name)
        logMessagesShort.add(log.shortMessage)
        //logMessagesFull.add(log.fullMessage)
    }
    val logDatesSonarqube = smoothDates(logDatesRaw)


    git.close()

    BufferedWriter(FileWriter(fileName)).use { bw ->
        val header = "date-original,date-sonarqube,hash,short-message"
        bw.write(header)
        bw.newLine()
        for ((idx, logDateRaw) in logDatesRaw.withIndex()) {
            bw.write(logDateRaw.toString())
            bw.write(",")
            bw.write(logDatesSonarqube[idx].toString())
            bw.write(",")
            bw.write(logHashes[idx])
            bw.write(",")
            bw.write(logMessagesShort[idx].replace(",",";"))
            bw.newLine()
        }
        println("Git commits saved to '$fileName'")
    }

    // delete temp folder
    /*
    if (localPath.deleteRecursively())
        println("Deleted '$localPath' successfully")
    else
        println("Could not delete '$localPath'")*/
}

fun cloneRemoteRepository(repositoryURL: String, directory: File): Git {
    try {
        println("Cloning repository from $repositoryURL\n...")
        val result: Git = Git.cloneRepository()
                .setURI(repositoryURL)
                .setDirectory(directory)
                .call()
        println("Repository cloned into '${result.repository.directory.parent}'")
        return result
    } catch (e: Exception) {
        throw Exception("Could not clone the remote repository", e)
    }
}

fun openLocalRepository(repositoryFile: File): Git {
    val builder: FileRepositoryBuilder = FileRepositoryBuilder()
    try {
        val repository = builder.setGitDir(repositoryFile)
                .readEnvironment()
                .findGitDir()
                .build()
        val result = Git(repository)
        result.log()
                .call() //tests the repository
        return result
    } catch (e: Exception) {
        throw Exception("Could not open the repository $repositoryFile", e)
    }
}

// copy-paste from sonar-history-scanner
/*
Makes sure commit dates are in an increasing order for doing repeated analysis
Increases day offset until the last commit date does not get changed by smoothing
*/
fun smoothDates(logDates: List<Instant>): List<Instant> {
    var daysOffset: Long = 1
    val result = mutableListOf<Instant>()
    while (result.lastOrNull() != logDates.last()) {
        result.clear()
        result.add(logDates.first())
        var previousDate = logDates.first()
        for (currentDate in logDates.subList(1, logDates.size)) {
            if (previousDate >= currentDate || previousDate.plus(daysOffset, ChronoUnit.DAYS) < currentDate)
                previousDate = previousDate.plusSeconds(1)
            else
                previousDate = currentDate
            result.add(previousDate)
        }
        daysOffset++

        // if last date is screwed up
        if (logDates.last() <= logDates.first())
            break
    }
    return result
}