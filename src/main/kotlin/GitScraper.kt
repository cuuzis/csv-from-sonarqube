import com.opencsv.CSVWriter
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.FileWriter
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.eclipse.jgit.treewalk.CanonicalTreeParser

private val emptyTreeHash = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"

fun saveGitCommits(fileName: String, repositoryURL: String) {
    println("Saving git commits")
    val header = listOf(
            "git-date-original",
            "git-date-sonarqube",
            "git-hash",
            "git-message",
            "git-committer",
            "git-total-committers",
            //"git-files-in-project",
            "git-changed-files")
    val rows = mutableListOf<List<String>>()
    rows.add(header)

    // clone repository into temp folder and access data
    val checkoutFolder = fileName.removeSuffix(fileName.split(File.separatorChar).last())
    val localPath = File(checkoutFolder + "tmpGitRepo")
    val git = cloneRemoteRepository(repositoryURL, localPath)
    //val git = openLocalRepository(File(checkoutFolder + "tmpGitRepo/.git"))
    try {
        val logEntries = git.log()
                .call()
                .reversed()

        val logDatesRaw = mutableListOf<Instant>()
        logEntries.mapTo(logDatesRaw) { Instant.ofEpochSecond(it.commitTime.toLong()) }
        val logDatesSonarqube = smoothDates(logDatesRaw)

        val totalCommitters = mutableSetOf<String>()
        var oldHash = emptyTreeHash
        for ((idx, log) in logEntries.withIndex()) {
            val row = mutableListOf<String>()
            row.add(Instant.ofEpochSecond(log.commitTime.toLong()).toString())
            row.add(logDatesSonarqube[idx].toString())
            row.add(log.name)//hash
            row.add(log.shortMessage)
            row.add(log.committerIdent.emailAddress)
            totalCommitters.add(log.committerIdent.emailAddress)
            row.add(totalCommitters.size.toString())
            //row.add(getFilesDiff(emptyTreeHash, log.name, git).joinToString(";")) // files in project
            row.add(getFilesDiff(oldHash, log.name, git).joinToString(";"))
            rows.add(row)
            oldHash = log.name
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

/**
 * Returns a list of files modified within this revision.
 */
fun getFilesDiff(oldHash: String, newHash: String, git: Git): List<String> {
    try {
        val oldId = git.repository.resolve("$oldHash^{tree}")
        val newId = git.repository.resolve("$newHash^{tree}")
        val reader = git.repository.newObjectReader()
        val oldTreeIter = CanonicalTreeParser()
        oldTreeIter.reset(reader, oldId)
        val newTreeIter = CanonicalTreeParser()
        newTreeIter.reset(reader, newId)
        val diffs = git.diff()
                .setNewTree(newTreeIter)
                .setOldTree(oldTreeIter)
                .call()
        return diffs.map { it.newPath }.filterNot { it == "/dev/null" }

    } catch (error: MissingObjectException) {
        if (oldHash == emptyTreeHash)
            return listOf()
        else
            throw error
    }
}

fun cloneRemoteRepository(repositoryURL: String, directory: File): Git {
    try {
        println("Cloning repository from $repositoryURL ..")
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