package gui

import javafx.scene.control.*
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import sonarqube.*
import java.net.UnknownHostException

private val tableProjects = TableView<SonarProject>()

/**
 * GUI to log performed actions
 */
class LogTab(logTextArea: TextArea) : Tab("Log") {

    init {
        this.content = logTextArea
        this.isClosable = false
    }
}