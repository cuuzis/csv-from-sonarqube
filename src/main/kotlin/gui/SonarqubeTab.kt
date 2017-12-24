package gui

import groupByFile
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
import mapFaultFileCommit
import saveGitCommits
import saveHistoryCorrelation
import saveJiraIssues
import saveSummary
import sonarqube.*
import java.util.prefs.Preferences
import javafx.stage.FileChooser


private val prefs = Preferences.userRoot().node("Sonarqube-csv-extractor-prefs")
private val prefsRScript = "rscript-directory"
private val prefsSonarServer = "sonarqube-server"

private val tableProjects = TableView<SonarProject>()
private val rScriptTextField = TextField(prefs.get(prefsRScript,"C:\\Program Files\\R\\R-3.3.3\\bin\\x64\\Rscript.exe"))

/**
 * GUI Sonarqube issue/measure extraction
 */
class SonarqubeTab(private val mainGui: MainGui) : Tab("Sonarqube") {

    private val serverTextField = TextField()
    private val saveCommitsButton = Button("Save git commits")
    private val saveFaultsButton = Button("Save jira faults")
    private val saveMappingButton = Button("Map faults & commits")
    private val saveCorrelationsButton = Button("Calculate correlations")
    private val saveSummaryButton = Button("Get summary")

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
            prefs.put(prefsSonarServer, newServerString)
            mainGui.runGuiTask(GetProjectListTask(newServerString))
        })
        serverTextField.textProperty().set(prefs.get(prefsSonarServer, "http://sonar.inf.unibz.it"))
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
            tableProjects.selectionModel.selectedItems.forEach { it.loadServerLinks()  }
            saveCommitsButton.isDisable = tableProjects.selectionModel.selectedItems.any { it.getGitLink() == "" }
            saveFaultsButton.isDisable = tableProjects.selectionModel.selectedItems.any { it.getJiraLink() == "" }
            saveMappingButton.isDisable = tableProjects.selectionModel.selectedItems.any { !it.isDataExtracted() }
            saveCorrelationsButton.isDisable = tableProjects.selectionModel.selectedItems.any { !it.isDataMapped() }
            saveSummaryButton.isDisable = tableProjects.selectionModel.selectedItems.any { !it.isDataMapped() }
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

        saveMappingButton.isDisable = true
        saveMappingButton.setOnAction {
            val selectedProjects = tableProjects.selectionModel.selectedItems
            selectedProjects.forEach {
                mainGui.runGuiTask(SaveMappingTask(it))
            }
        }

        saveCorrelationsButton.isDisable = true
        saveCorrelationsButton.setOnAction {
            val selectedProjects = tableProjects.selectionModel.selectedItems
            selectedProjects.forEach {
                mainGui.runGuiTask(SaveCorrelationsTask(it))
            }
        }

        saveSummaryButton.isDisable = true
        saveSummaryButton.setOnAction {
            val selectedProjects = tableProjects.selectionModel.selectedItems
            mainGui.runGuiTask(SaveSummaryTask(selectedProjects))
        }

        val exportRow = HBoxRow(saveCommitsButton, saveFaultsButton, saveMappingButton, saveCorrelationsButton, saveSummaryButton)
        rows.children.add(exportRow)

        // configuration for RScript
        val rScriptLocationLabel = Label("Rscript location (for correlations):")
        rScriptTextField.textProperty().addListener({ _, _, newRScriptDirectory ->
            prefs.put(prefsRScript, newRScriptDirectory)
        })
        val rScriptSelectButton = Button("Select")
        rScriptSelectButton.setOnAction {
            val fileChooser = FileChooser()
            fileChooser.title = "Select rscript executable"
            val file = fileChooser.showOpenDialog(mainGui.stage)
            if (file != null) {
                rScriptTextField.textProperty().set(file.path)
            }
        }
        val configRow = HBoxRow(rScriptLocationLabel, rScriptTextField, rScriptSelectButton)
        HBox.setHgrow(rScriptTextField, Priority.SOMETIMES)
        rows.children.add(configRow)
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
            getProjectsOnServer(sonarServer)
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
 * Saves issues for project
 */
class ExportIssuesTask(private val sonarProject: SonarProject) : GuiTask() {

    override fun call() {
        super.call()
        updateMessage("Exporting issues for ${sonarProject.getName()} (${sonarProject.getKey()})")
        val savedFile = saveIssues(sonarProject, "OPEN,CLOSED")
        updateMessage("Issues saved to $savedFile")
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
 * Maps commits-fault-file mapping for a project. Requires
 */
class SaveMappingTask(private val sonarProject: SonarProject) : GuiTask() {

    override fun call() {
        super.call()
        updateMessage("Mapping commits & faults for ${sonarProject.getName()} (${sonarProject.getKey()})")
        val groupedByCommits = mapFaultFileCommit(sonarProject)
        updateMessage("Mapped data grouped by commits saved to $groupedByCommits")
        val groupedByFiles = groupByFile(sonarProject)
        updateMessage("Mapped data grouped by files saved to $groupedByFiles")
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

/**
 * Saves correlations for project
 */
class SaveCorrelationsTask(private val sonarProject: SonarProject) : GuiTask() {

    override fun call() {
        super.call()
        updateMessage("Saving correlations for ${sonarProject.getName()} (${sonarProject.getKey()})")
        val savedFile = saveHistoryCorrelation(sonarProject, rScriptTextField.text)
        updateMessage("Correlations saved to $savedFile")
    }

}

/**
 * Saves correlations for project
 */
class SaveSummaryTask(private val sonarProjects: List<SonarProject>) : GuiTask() {

    override fun call() {
        super.call()
        updateMessage("Saving summary for ${sonarProjects.size} projects")
        val savedFile = saveSummary(sonarProjects)
        updateMessage("Summary saved to $savedFile")
    }

}