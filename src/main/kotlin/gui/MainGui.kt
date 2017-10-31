package gui

import error
import info
import javafx.application.Application
import javafx.scene.Scene
import javafx.stage.Stage
import javafx.concurrent.Task
import javafx.scene.control.*
import javafx.scene.control.ProgressBar
import javafx.scene.layout.*
import javafx.scene.paint.Color
import org.slf4j.LoggerFactory


private var currentTask: Task<Any>? = null
private val taskProgressBar = ProgressBar(0.0)
private val taskStopButton = Button("Stop")
private val taskStatusLabel = Label("")

val logTextArea = TextArea()
val logger = LoggerFactory.getLogger(MainGui::class.java)!!

/**
 * GUI main view
 */
class MainGui : Application() {

    companion object {
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

/**
 * Background task that shows its status on the GUI
 */
abstract class GuiTask() : Task<Any>() {

    /**
     * Invoked when the Task is executed, the call method must be overridden and
     * implemented by subclasses.
     */
    override fun call(): Any? {
        taskStopButton.isDisable = false
        updateProgress(-1, 1)
        return null
    }

    override fun done() {
        super.done()
        logger.info(logTextArea, "${this.javaClass.simpleName} done")
        taskStopButton.isDisable = true
        updateProgress(0,1)
        currentTask = null
    }

    override fun cancelled() {
        logger.info(logTextArea,"${this.javaClass.simpleName} cancelled")
    }

    override fun succeeded() {
        logger.info(logTextArea, "${this.javaClass.simpleName} succeeded")
    }

    override fun failed() {
        updateMessage("${this.javaClass.simpleName} failed: $exception")
        val message = "${this.javaClass.simpleName} failed with the following exception:"
        logger.error(logTextArea, message, exception)
    }

    override fun updateMessage(message: String) {
        super.updateMessage(message)
        logger.info(logTextArea, message)
    }
}
