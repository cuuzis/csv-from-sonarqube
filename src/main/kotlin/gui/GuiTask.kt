package gui

import error
import gui.MainGui.Companion.currentTask
import gui.MainGui.Companion.logTextArea
import gui.MainGui.Companion.logger
import gui.MainGui.Companion.taskStopButton
import info
import javafx.concurrent.Task

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