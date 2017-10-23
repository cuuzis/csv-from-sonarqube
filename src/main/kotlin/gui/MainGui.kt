package gui

import javafx.application.Application
import javafx.scene.Scene
import javafx.stage.Stage
import javafx.geometry.Insets
import javafx.scene.layout.GridPane
import javafx.concurrent.Task
import javafx.scene.control.*
import javafx.scene.control.ProgressBar


private var currentTask: Task<Any>? = null
private val taskProgressBar = ProgressBar(0.0)
private val taskStopButton = Button("Stop")
private val taskStatusLabel = Label("")

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
        primaryStage.title = "Sonarqube issue extractor"
        taskStopButton.setOnAction {
            println("Task topped")
            currentTask?.cancel()
        }
        taskStopButton.isDisable = true

        val tabPane = TabPane(
                SonarqubeTab(this),
                Tab("Github"),
                Tab("Jira"),
                Tab("Log"))
        val grid = GridPane()
        grid.hgap = 10.0
        grid.vgap = 10.0
        grid.padding = Insets(0.0, 10.0, 0.0, 10.0)
        grid.add(tabPane, 0, 0, 3, 5)
        grid.add(taskStopButton, 0, 6)
        grid.add(taskProgressBar, 1, 6)
        grid.add(taskStatusLabel, 0, 7, 3, 1)

        //val root = StackPane()
        //root.children.add(grid)
        primaryStage.scene = Scene(grid, 600.0, 500.0)
        primaryStage.show()
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
        println("${this.javaClass.simpleName} done")
        taskStopButton.isDisable = true
        updateProgress(0,1)
        currentTask = null
    }

    override fun cancelled() {
        println("${this.javaClass.simpleName} cancelled")
    }

    override fun succeeded() {
        println("${this.javaClass.simpleName} succeeded")
    }

    override fun failed() {
        updateMessage("${this.javaClass.simpleName} failed: $exception")
        System.err.println("${this.javaClass.simpleName} failed with the following exception:")
        exception.printStackTrace(System.err)
    }

    override fun updateMessage(message: String?) {
        super.updateMessage(message)
        println(message)
    }
}