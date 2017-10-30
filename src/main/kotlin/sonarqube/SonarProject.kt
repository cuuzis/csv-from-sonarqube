package sonarqube

import javafx.beans.property.SimpleStringProperty
import java.io.File

class SonarProject constructor(val sonarServer: SonarServer, key: String, name: String) {

    private val key: SimpleStringProperty = SimpleStringProperty(key)
    private val name: SimpleStringProperty = SimpleStringProperty(name)
    private var gitLink: SimpleStringProperty
    private var jiraLink: SimpleStringProperty

    init {
        val storedProjectInfo = getStoredProjectInfo(key)
        if (storedProjectInfo == null) {
            gitLink = SimpleStringProperty("")
            jiraLink = SimpleStringProperty("")
        } else {
            gitLink = SimpleStringProperty(storedProjectInfo.gitLink)
            jiraLink = SimpleStringProperty(storedProjectInfo.jiraLink)
        }
        sonarServer.projects.add(this)
    }

    /**
     * Getter for TableColumn "key"
     */
    fun getKey(): String {
        return key.get()
    }

    /**
     * Getter for TableColumn "name"
     */
    fun getName(): String {
        return name.get()
    }

    /**
     * Getter for TableColumn "gitLink"
     */
    fun getGitLink(): String {
        return gitLink.get()
    }

    fun setGitLink(gitLink: String) {
        updateGitLinkCSV(getKey(), gitLink)
        this.gitLink.set(gitLink)
    }

    /**
     * Getter for TableColumn "jiraLink"
     */
    fun getJiraLink(): String {
        return jiraLink.get()
    }

    fun setJiraLink(jiraLink: String) {
        updateJiraLinkCSV(getKey(), jiraLink)
        this.jiraLink.set(jiraLink)
    }

    /**
     * Replaces characters in project key, which are not valid in a directory name
     */
    private fun getKeyAsFolderName(): String {
        return getKey().replace("\\W".toRegex(),"-")
    }

    /**
     * Returns the data extraction folder for the project.
     * Creates this folder, if it does not exist.
     */
    fun getProjectFolder(): String {
        val folder = File(getKeyAsFolderName())
        if (!folder.exists()) {
            folder.mkdir()
        }
        return folder.name
    }
}