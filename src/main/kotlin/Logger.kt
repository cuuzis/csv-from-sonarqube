import javafx.scene.control.TextArea
import org.slf4j.Logger
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Extension function that appends error message to log TextArea
 */
fun Logger.error(logTextArea: TextArea, message: String, exception: Throwable) {
    logTextArea.appendText(message + System.lineSeparator() + exception.getStackTraceString())
    error(message, exception)
}

/**
 * Extension function that appends error message to log TextArea
 */
fun Logger.error(logTextArea: TextArea, message: String) {
    logTextArea.appendText(message + System.lineSeparator())
    error(message)
}

/**
 * Extension function that appends info message to log TextArea
 */
fun Logger.info(logTextArea: TextArea, message: String) {
    logTextArea.appendText(message + System.lineSeparator())
    info(message)
}

/**
 * Extension function to get string from stack trace
 */
private fun Throwable.getStackTraceString(): String {
    val sw = StringWriter()
    this.printStackTrace(PrintWriter(sw))
    return sw.toString()
}