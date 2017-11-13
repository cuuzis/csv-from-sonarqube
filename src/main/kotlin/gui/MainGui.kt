package gui

import javafx.application.Application
import javafx.scene.Scene
import javafx.stage.Stage
import javafx.concurrent.Task
import javafx.scene.control.*
import javafx.scene.control.ProgressBar
import javafx.scene.layout.*
import javafx.scene.paint.Color
import org.slf4j.LoggerFactory



/**
 * GUI main view
 */
class MainGui : Application() {

    var stage: Stage? = null

    companion object {
        var currentTask: Task<Any>? = null
        val taskProgressBar = ProgressBar(0.0)
        val taskStopButton = Button("Stop")
        val taskStatusLabel = Label("")

        val logTextArea = TextArea()
        val logger = LoggerFactory.getLogger(MainGui::class.java)!!


        @JvmStatic
        fun main(args: Array<String>) {
            Application.launch(MainGui::class.java)
        }
    }

    override fun start(primaryStage: Stage) {

        val rows = VBox()
        addMainContent(rows)
        addStatusBar(rows)

        primaryStage.title = "Sonarqube issue extractor"
        primaryStage.scene = Scene(rows, 1000.0, 700.0)
        primaryStage.show()
        stage = primaryStage
    }

    private fun addMainContent(rows: VBox) {
        val tabPane = TabPane(
                SonarqubeTab(this),
                LogTab(logTextArea))
        rows.children.add(tabPane)
        VBox.setVgrow(tabPane, Priority.ALWAYS)
    }

    private fun addStatusBar(rows: VBox) {
        taskStopButton.setOnAction {
            currentTask?.cancel()
        }
        taskStopButton.isDisable = true
        val taskStatusRow = HBoxRow(taskStopButton, taskProgressBar)
        taskStatusRow.border = Border(BorderStroke(
                Color.LIGHTGRAY,
                BorderStrokeStyle.SOLID,
                CornerRadii.EMPTY,
                BorderWidths(1.0,0.0,0.0,0.0)))
        rows.children.add(taskStatusRow)
        rows.children.add(taskStatusLabel)
    }

    /**
     * Launches background task on the GUI
     */
    internal fun runGuiTask(newTask: GuiTask) {
        currentTask?.cancel()
        taskProgressBar.progressProperty().bind(newTask.progressProperty())
        taskStatusLabel.textProperty().bind(newTask.messageProperty())
        currentTask = newTask
        Thread(currentTask).start()
    }
}

