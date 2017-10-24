package gui

import getProjectsContainingString
import getStringFromUrl
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import java.net.UnknownHostException




private val listProjects = ListView<String>()

/**
 * GUI Sonarqube issue/measure extraction
 */
class SonarqubeTab(private val mainGui: MainGui) : Tab("Sonarqube") {


    private val labelServer = Label("Sonarqube server:")
    private val textServer = TextField()
    private val labelProjects = Label("Projects on server:")

    init {
        val rows = VBox()
        addServerRow(rows)
        addProjectsRow(rows)
        this.content = rows
        this.isClosable = false
    }

    private fun addServerRow(rows: VBox) {
        textServer.textProperty().addListener({ _, _, newServerString ->
            mainGui.runGuiTask(GetProjectListTask(newServerString))
        })
        textServer.textProperty().set("http://sonar.inf.unibz.it")
        val serverRow = HBox(labelServer, textServer)
        serverRow.spacing = 10.0
        serverRow.padding = Insets(10.0, 10.0, 10.0, 10.0)
        serverRow.alignment = Pos.CENTER_LEFT
        rows.children.add(serverRow)
    }

    private fun addProjectsRow(rows: VBox) {
        val projectsRow = HBox(labelProjects, listProjects)
        projectsRow.spacing = 10.0
        projectsRow.padding = Insets(10.0, 10.0, 10.0, 10.0)
        projectsRow.alignment = Pos.CENTER_LEFT
        rows.children.add(projectsRow)
    }
}

/**
 * Queries projects available on Sonarqube server
 */
class GetProjectListTask(private val sonarInstance: String) : GuiTask() {

    override fun call(): List<String> {
        super.call()
        updateMessage("Getting Sonarqube project list")
        try {
            getStringFromUrl(sonarInstance)
            val result = getProjectsContainingString(sonarInstance, "")
            updateMessage("Retrieved ${result.size} projects from $sonarInstance")
            return result
        } catch (e: UnknownHostException) {
            if (!isCancelled) {
                updateMessage("Host $sonarInstance not found")
            }
        }
        return listOf<String>()
    }

    /**
     * Adds the retrieved project list to GUI
     */
    override fun succeeded() {
        @Suppress("UNCHECKED_CAST")
        val projectsOnServer: List<String> = value as List<String>
        listProjects.items.clear()
        listProjects.items.addAll(projectsOnServer)
    }
}