package gui

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.stage.Stage
import javafx.geometry.Insets
import javafx.scene.layout.GridPane
import javafx.concurrent.Task
import javafx.scene.control.*
import javafx.scene.control.ProgressBar


class SonarGui : Application() {

    private var currentTask: Task<Void>? = null
    private val taskProgressBar = ProgressBar(0.0)
    private val taskStopButton = Button("Stop")
    private val taskStatusLabel = Label("")

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Application.launch(SonarGui::class.java)
        }
    }

    override fun start(primaryStage: Stage) {
        primaryStage.title = "Issue extractor"

        taskStopButton.setOnAction {
            println("Stopped")
            currentTask?.cancel()
        }

        val labelServer = Label("Sonarqube server:")
        val textServer = TextField("http://sonar.inf.unibz.it")
        textServer.textProperty().addListener({ _, oldValue, newValue ->
            println("textfield changed from $oldValue to $newValue")
            //getProjectList(newValue)
        })


        val labelProjects = Label("Projects on server:")
        val listProjects = ListView<String>()
        listProjects.items.addAll("aaa", "bbb", "ccc", "ddd", "e", "f", "g", "h", "ii")
        listProjects.setPrefSize(120.0, 120.0)
        listProjects.selectionModel.selectionMode = SelectionMode.MULTIPLE
        listProjects.selectionModel.selectedItemProperty().addListener({ _, oldValue, newValue ->
            println("Selection changed from $oldValue to $newValue")
            fakeTask()
            //getProjectList(newValue)
        })


        val grid = GridPane()
        grid.hgap = 10.0
        grid.vgap = 10.0
        grid.padding = Insets(0.0, 10.0, 0.0, 10.0)
        grid.add(labelServer, 0, 1)
        grid.add(textServer, 1, 1)
        grid.add(labelProjects, 0, 2)
        grid.add(listProjects, 1, 2)
        grid.add(taskStopButton, 0, 3)
        grid.add(taskProgressBar, 1, 3)
        grid.add(taskStatusLabel, 0, 4)


        val root = StackPane()
        root.children.add(grid)
        primaryStage.scene = Scene(root, 600.0, 500.0)
        primaryStage.show()

    }

    private fun fakeTask() {
        currentTask?.cancel()
        val newTask = object : Task<Void>() {
            override fun call(): Void? {
                taskStopButton.isDisable = false
                updateMessage("Working...")
                updateProgress(-1, 1)

                Thread.sleep(3000)
                val max = 100
                for (i in 1..max) {
                    Thread.sleep(50)
                    updateProgress(i.toLong(), max.toLong())
                    updateMessage("Iteration $i")
                    println("Iteration $i")
                }
                return null
            }

            override fun done() {
                println("done")
                taskStopButton.isDisable = true
                if (progress < 0) {
                    updateProgress(0, 1)
                }
                currentTask = null
            }

            override fun cancelled() {
                updateMessage("Cancelled")
            }

            override fun succeeded() {
                updateMessage("Succeeded")
            }

        }

        taskProgressBar.progressProperty().bind(newTask.progressProperty())
        taskStatusLabel.textProperty().bind(newTask.messageProperty())
        currentTask = newTask
        Thread(currentTask).start()
    }
}