package gui

import sonarqube.getProjectsContainingString
import sonarqube.getStringFromUrl
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import java.net.UnknownHostException
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.layout.Priority
import sonarqube.SonarProject


private val tableProjects = TableView<SonarProject>()

/**
 * GUI Sonarqube issue/measure extraction
 */
class SonarqubeTab(private val mainGui: MainGui) : Tab("Sonarqube") {

    private val textServer = TextField()

    init {
        val rows = VBox()
        addServerRow(rows)
        addProjectsRow(rows)
        this.content = rows
        this.isClosable = false
    }

    private fun addServerRow(rows: VBox) {
        val labelServer = Label("Sonarqube server:")
        textServer.textProperty().addListener({ _, _, newServerString ->
            mainGui.runGuiTask(GetProjectListTask(newServerString))
        })
        textServer.textProperty().set("http://sonar.inf.unibz.it")
        val serverRow = HBox(labelServer, textServer)
        serverRow.spacing = 10.0
        serverRow.padding = Insets(10.0, 10.0, 10.0, 10.0)
        serverRow.alignment = Pos.CENTER_LEFT
        HBox.setHgrow(textServer, Priority.SOMETIMES)
        rows.children.add(serverRow)
    }

    private fun addProjectsRow(rows: VBox) {
        val labelProjects = Label("Projects on server:")
        val keyCol: TableColumn<SonarProject, SonarProject> = TableColumn("key")
        keyCol.cellValueFactory = PropertyValueFactory<SonarProject, SonarProject>("key")
        val nameCol: TableColumn<SonarProject, SonarProject> = TableColumn("name")
        nameCol.cellValueFactory = PropertyValueFactory<SonarProject, SonarProject>("name")

        tableProjects.columns.addAll(keyCol, nameCol)
        tableProjects.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY

        val projectsRow = HBox(labelProjects, tableProjects)
        projectsRow.spacing = 10.0
        projectsRow.padding = Insets(10.0, 10.0, 10.0, 10.0)
        projectsRow.alignment = Pos.CENTER_LEFT
        HBox.setHgrow(tableProjects, Priority.SOMETIMES)
        rows.children.add(projectsRow)
    }
}

/**
 * Queries projects available on Sonarqube server
 */
class GetProjectListTask(private val sonarInstance: String) : GuiTask() {

    override fun call(): List<SonarProject> {
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
        return listOf<SonarProject>()
    }

    /**
     * Adds the retrieved project list to GUI
     */
    override fun succeeded() {
        @Suppress("UNCHECKED_CAST")
        val projectsOnServer: List<SonarProject> = value as List<SonarProject>
        tableProjects.items.clear()
        tableProjects.items.addAll(projectsOnServer)
    }
}