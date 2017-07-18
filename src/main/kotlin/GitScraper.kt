import com.opencsv.CSVWriter
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.FileWriter
import java.time.Instant
import java.time.temporal.ChronoUnit

fun saveGitCommits(fileName: String, repositoryURL: String) {

    val header = listOf("git-date-original", "git-date-sonarqube", "git-hash", "git-message", "git-committer", "git-total-committers", "git-files-changed")
    val rows = mutableListOf<List<String>>()
    rows.add(header)

    // clone repository into temp folder and access data
    val localPath = File("tmpGitRepo")
    val git = cloneRemoteRepository(repositoryURL, localPath)
    //val git = openLocalRepository(File("tmpGitRepo/.git"))
    try {
        val logEntries = git.log()
                .call()
                .reversed()

        val logDatesRaw = mutableListOf<Instant>()
        logEntries.mapTo(logDatesRaw) { Instant.ofEpochSecond(it.commitTime.toLong()) }
        val logDatesSonarqube = smoothDates(logDatesRaw)

        val totalCommitters = mutableSetOf<String>()
        for ((idx, log) in logEntries.withIndex()) {
            val row = mutableListOf<String>()
            row.add(Instant.ofEpochSecond(log.commitTime.toLong()).toString())
            row.add(logDatesSonarqube[idx].toString())
            row.add(log.name)//hash
            row.add(log.shortMessage)
            row.add(log.committerIdent.emailAddress)
            totalCommitters.add(log.committerIdent.emailAddress)
            row.add(totalCommitters.size.toString())

            rows.add(row)
        }

    } finally {
        git.close()
    }

    // save data to file
    FileWriter(fileName).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeAll(rows.map { it.toTypedArray() })
        println("Git commit data saved to $fileName")
    }

    // delete temp folder
    if (localPath.deleteRecursively())
        println("Deleted '$localPath' successfully")
    else
        println("Could not delete '$localPath'")
}

fun cloneRemoteRepository(repositoryURL: String, directory: File): Git {
    try {
        println("Cloning repository from $repositoryURL\n...")
        val result: Git = Git.cloneRepository()
                .setURI(repositoryURL)
                .setDirectory(directory)
                .call()
        println("Git repository cloned into '${result.repository.directory.parent}'")
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