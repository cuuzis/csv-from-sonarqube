package gui

import getProjectsContainingString
import getStringFromUrl
import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.layout.GridPane
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
        textServer.textProperty().addListener({ _, _, newServerString ->
            mainGui.runGuiTask(GetProjectListTask(newServerString))
        })
        textServer.textProperty().set("http://sonar.inf.unibz.it")
        listProjects.setPrefSize(150.0, 120.0)
        listProjects.selectionModel.selectionMode = SelectionMode.MULTIPLE
        listProjects.selectionModel.selectedItemProperty().addListener({ _, oldValue, newValue ->
            println("Selection changed from $oldValue to $newValue")
        })
        val grid = GridPane()
        grid.hgap = 10.0
        grid.vgap = 10.0
        grid.padding = Insets(0.0, 10.0, 0.0, 10.0)
        grid.add(labelServer, 0, 1)
        grid.add(textServer, 1, 1)
        grid.add(labelProjects, 0, 2)
        grid.add(listProjects, 1, 2)
        this.content = grid
        this.isClosable = false
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
            val result = getProjectsContainingString("")
            updateMessage("Retrieved ${result.size} projects from $sonarInstance")
            return result
        } catch (e: UnknownHostException) {
            updateMessage("Host $sonarInstance not found")
        }
        return listOf<String>()
    }

    /**
     * Adds the retrieved project list to GUI
     */
    override fun succeeded() {
        @Suppress("UNCHECKED_CAST")
        val projectsOnServer: List<String> = value as List<String>
        listProjects.items.addAll(projectsOnServer)
    }
}