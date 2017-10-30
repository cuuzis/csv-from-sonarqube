package sonarqube

import com.opencsv.bean.CsvToBeanBuilder
import csv_model.SonarProjectCSV
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import com.opencsv.bean.StatefulBeanToCsvBuilder

private val mappingsFile = "projects.csv"

/**
 * Reads the project key mapping to git & jira from file
 */
private fun loadMapping(): List<SonarProjectCSV> {
    val file = File(mappingsFile)
    if (file.exists()) {
        return CsvToBeanBuilder<SonarProjectCSV>(FileReader(file))
                .withType(SonarProjectCSV::class.java).build().parse()
                .map { it as SonarProjectCSV }
                .map { SonarProjectCSV(it.projectKey.orEmpty(), it.gitLink.orEmpty(), it.jiraLink.orEmpty()) } // remove nulls
    } else {
        return listOf()
    }
}

/**
 * Returns stored project info for a project key
 */
fun getStoredProjectInfo(sonarKey: String): SonarProjectCSV? {
    val projects = loadMapping()
    return projects.find { it.projectKey == sonarKey }
}

/**
 * Saves project's git link to file
 */
fun updateGitLinkCSV(sonarKey: String, gitLink: String) {
    val projects = loadMapping()
    val changedProject = projects.find { it.projectKey == sonarKey }
    val unchangedProjects = projects.filterNot { it == changedProject }
    FileWriter(mappingsFile).use { fw ->
        val beanToCsv = StatefulBeanToCsvBuilder<SonarProjectCSV>(fw).build()
        unchangedProjects.forEach {
            beanToCsv.write(it)
        }
        if (gitLink != "" && changedProject?.jiraLink != "") {
            beanToCsv.write(SonarProjectCSV(sonarKey, gitLink, changedProject?.jiraLink))
        }
    }
}


/**
 * Saves project's jira link to file
 */
fun updateJiraLinkCSV(sonarKey: String, jiraLink: String) {
    val projects = loadMapping()
    val changedProject = projects.find { it.projectKey == sonarKey }
    val unchangedProjects = projects.filterNot { it == changedProject }
    FileWriter(mappingsFile).use { fw ->
        val beanToCsv = StatefulBeanToCsvBuilder<SonarProjectCSV>(fw).build()
        unchangedProjects.forEach {
            beanToCsv.write(it)
        }
        if (jiraLink != "" || changedProject?.jiraLink != "") {
            beanToCsv.write(SonarProjectCSV(sonarKey, changedProject?.gitLink, jiraLink))
        }
    }
}

