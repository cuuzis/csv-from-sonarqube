package gui

import javafx.collections.ListChangeListener
import javafx.event.EventHandler
import javafx.geometry.Orientation
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import java.net.UnknownHostException
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.control.cell.TextFieldTableCell
import javafx.scene.layout.Priority
import saveGitCommits
import saveJiraIssues
import sonarqube.*


private val tableProjects = TableView<SonarProject>()

/**
 * GUI Sonarqube issue/measure extraction
 */
class SonarqubeTab(private val mainGui: MainGui) : Tab("Sonarqube") {

    private val serverTextField = TextField()
    private val saveCommitsButton = Button("Save git commits")
    private val saveFaultsButton = Button("Save jira faults")

    init {
        val rows = VBox()
        addServerRow(rows)
        addProjectsRow(rows)
        addSeparator(rows, "SonarQube")
        addSonarqubeRow(rows)
        addSeparator(rows, "Git & Jira")
        addGitAndJiraRow(rows)
        rows.children.add(HBoxRow())
        this.content = rows
        this.isClosable = false
    }

    private fun addServerRow(rows: VBox) {
        val labelServer = Label("Sonarqube server:")
        serverTextField.textProperty().addListener({ _, _, newServerString ->
            mainGui.runGuiTask(GetProjectListTask(newServerString))
        })
        serverTextField.textProperty().set("http://sonar.inf.unibz.it")
        val serverRow = HBoxRow(labelServer, serverTextField)
        HBox.setHgrow(serverTextField, Priority.SOMETIMES)
        rows.children.add(serverRow)
    }

    private fun addProjectsRow(rows: VBox) {
        val labelProjects = Label("Projects on server:")

        val keyCol: TableColumn<SonarProject, SonarProject> = TableColumn("key")
        keyCol.cellValueFactory = PropertyValueFactory<SonarProject, SonarProject>("key")

        val nameCol: TableColumn<SonarProject, SonarProject> = TableColumn("name")
        nameCol.cellValueFactory = PropertyValueFactory<SonarProject, SonarProject>("name")

        val gitLinkCol: TableColumn<SonarProject, String> = TableColumn("git link")
        gitLinkCol.cellValueFactory = PropertyValueFactory<SonarProject, String>("gitLink")
        gitLinkCol.cellFactory = TextFieldTableCell.forTableColumn()
        gitLinkCol.onEditCommit = EventHandler<TableColumn.CellEditEvent<SonarProject, String>> {
            val selectedProject = it.tableView.items[it.tablePosition.row]
            selectedProject.setGitLink(it.newValue)
            saveCommitsButton.isDisable = selectedProject.getGitLink() == ""
        }

        val jiraLinkCol: TableColumn<SonarProject, String> = TableColumn("jira link")
        jiraLinkCol.cellValueFactory = PropertyValueFactory<SonarProject, String>("jiraLink")
        jiraLinkCol.cellFactory = TextFieldTableCell.forTableColumn()
        jiraLinkCol.onEditCommit = EventHandler<TableColumn.CellEditEvent<SonarProject, String>> {
            val selectedProject = it.tableView.items[it.tablePosition.row]
            selectedProject.setJiraLink(it.newValue)
            saveFaultsButton.isDisable = selectedProject.getJiraLink() == ""
        }

        tableProjects.isEditable = true
        tableProjects.columns.addAll(keyCol, nameCol, gitLinkCol, jiraLinkCol)
        tableProjects.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        tableProjects.selectionModel.selectionMode = SelectionMode.MULTIPLE
        tableProjects.selectionModel.selectedItems.addListener { _: ListChangeListener.Change<*> ->
            saveCommitsButton.isDisable = tableProjects.selectionModel.selectedItems.any { it.getGitLink() == "" }
            saveFaultsButton.isDisable = tableProjects.selectionModel.selectedItems.any { it.getJiraLink() == "" }
        }

        val projectsRow = HBoxRow(labelProjects, tableProjects)
        HBox.setHgrow(tableProjects, Priority.SOMETIMES)
        rows.children.add(projectsRow)
    }

    private fun addSonarqubeRow(rows: VBox) {
        val exportIssuesButton = Button("Save issues")
        exportIssuesButton.setOnAction {
            val selectedProjects = tableProjects.selectionModel.selectedItems
            if (selectedProjects.isEmpty()) {
                alertNoProjectSelected()
            } else {
                selectedProjects.forEach {
                    mainGui.runGuiTask(ExportIssuesTask(it))
                }
            }
        }
        val exportMeasureHistoryButton = Button("Save measures")
        exportMeasureHistoryButton.setOnAction {
            val selectedProjects = tableProjects.selectionModel.selectedItems
            if (selectedProjects.isEmpty()) {
                alertNoProjectSelected()
            } else {
                selectedProjects.forEach {
                    mainGui.runGuiTask(ExportMeasureHistoryTask(it))
                }
            }
        }
        val exportMeasuresButton = Button("Save current measures")
        exportMeasuresButton.setOnAction {
            val selectedProjects = tableProjects.selectionModel.selectedItems
            if (selectedProjects.isEmpty()) {
                alertNoProjectSelected()
            } else {
                selectedProjects.forEach {
                    mainGui.runGuiTask(ExportMeasuresTask(it))
                }
            }
        }
        val exportRow = HBoxRow(exportIssuesButton, exportMeasureHistoryButton, exportMeasuresButton)
        rows.children.add(exportRow)
    }

    private fun addSeparator(rows: VBox, label: String) {
        val separatorBefore = Separator(Orientation.HORIZONTAL)
        val separatorAfter = Separator(Orientation.HORIZONTAL)
        HBox.setHgrow(separatorBefore, Priority.SOMETIMES)
        HBox.setHgrow(separatorAfter, Priority.SOMETIMES)
        val separatorRow = HBoxRow(separatorBefore, Label(label), separatorAfter)
        rows.children.add(separatorRow)
    }

    private fun addGitAndJiraRow(rows: VBox) {
        saveCommitsButton.isDisable = true
        saveCommitsButton.setOnAction {
            val selectedProjects = tableProjects.selectionModel.selectedItems
            selectedProjects.forEach {
                mainGui.runGuiTask(ExportCommitsTask(it))
            }
        }

        saveFaultsButton.isDisable = true
        saveFaultsButton.setOnAction {
            val selectedProjects = tableProjects.selectionModel.selectedItems
            selectedProjects.forEach {
                mainGui.runGuiTask(ExportFaultsTask(it))
            }
        }

        val exportRow = HBoxRow(saveCommitsButton, saveFaultsButton)
        rows.children.add(exportRow)
    }

    private fun alertNoProjectSelected() {
        val alert = Alert(Alert.AlertType.INFORMATION, "Please select a project")
        alert.headerText = null
        alert.showAndWait()
    }
}

/**
 * Queries projects available on Sonarqube server
 */
class GetProjectListTask(private val serverAddress: String) : GuiTask() {

    override fun call(): SonarServer {
        super.call()
        updateMessage("Getting Sonarqube project list")
        val sonarServer = SonarServer(serverAddress)
        try {
            getStringFromUrl(serverAddress)
            getProjectsContainingString(sonarServer, "")
            updateMessage("Retrieved ${sonarServer.projects.size} projects from $serverAddress")
        } catch (e: UnknownHostException) {
            if (!isCancelled) {
                updateMessage("Host $serverAddress not found")
            }
        }
        return sonarServer
    }

    /**
     * Adds the retrieved project list to GUI
     */
    override fun succeeded() {
        val sonarServer: SonarServer = value as SonarServer
        tableProjects.items.clear()
        tableProjects.items.addAll(sonarServer.projects)
    }
}

/**
 * Saves current issues for project
 */
class ExportIssuesTask(private val sonarProject: SonarProject) : GuiTask() {

    override fun call() {
        super.call()
        updateMessage("Exporting issues for ${sonarProject.getName()} (${sonarProject.getKey()})")
        val savedFile = saveIssues(sonarProject, "OPEN")
        updateMessage("Current issues saved to $savedFile")
    }
}

/**
 * Saves measure history for project
 */
class ExportMeasureHistoryTask(private val sonarProject: SonarProject) : GuiTask() {

    override fun call() {
        super.call()
        updateMessage("Exporting measure history for ${sonarProject.getName()} (${sonarProject.getKey()})")
        val savedFile = saveMeasureHistory(sonarProject)
        updateMessage("Measure history saved to $savedFile")
    }
}

/**
 * Saves current measures for project
 */
class ExportMeasuresTask(private val sonarProject: SonarProject) : GuiTask() {

    override fun call() {
        super.call()
        updateMessage("Exporting current measures for ${sonarProject.getName()} (${sonarProject.getKey()})")
        val savedFile = saveMeasures(sonarProject)
        updateMessage("Current measures saved to $savedFile")
    }
}

/**
 * Saves jira faults for project
 */
class ExportFaultsTask(private val sonarProject: SonarProject) : GuiTask() {

    override fun call() {
        super.call()
        updateMessage("Exporting jira faults for ${sonarProject.getName()} (${sonarProject.getKey()})")
        val savedFile = saveJiraIssues(sonarProject)
        updateMessage("Jira faults saved to $savedFile")
    }

}

/**
 * Saves git commits for project
 */
class ExportCommitsTask(private val sonarProject: SonarProject) : GuiTask() {

    override fun call() {
        super.call()
        updateMessage("Exporting git commits for ${sonarProject.getName()} (${sonarProject.getKey()})")
        val savedFile = saveGitCommits(sonarProject)
        updateMessage("Git commits saved to $savedFile")
    }

}