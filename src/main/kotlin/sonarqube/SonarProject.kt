package sonarqube

import javafx.beans.property.SimpleStringProperty
import java.io.File

class SonarProject constructor(val sonarServer: SonarServer, key: String, name: String, gitLink: String, jiraLink: String) {

    private val key: SimpleStringProperty = SimpleStringProperty(key)
    private val name: SimpleStringProperty = SimpleStringProperty(name)
    private var gitLink: SimpleStringProperty = SimpleStringProperty(gitLink)
    private var jiraLink: SimpleStringProperty = SimpleStringProperty(jiraLink)

    init {
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
        this.gitLink.set(gitLink)
    }

    /**
     * Getter for TableColumn "jiraLink"
     */
    fun getJiraLink(): String {
        return jiraLink.get()
    }

    fun setJiraLink(gitLink: String) {
        this.jiraLink.set(gitLink)
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