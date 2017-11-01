import javafx.scene.control.TextArea
import org.slf4j.Logger
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

private val dateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(Locale.UK)
                .withZone(ZoneId.systemDefault())

/**
 * Extension function that appends error message to log TextArea
 */
fun Logger.error(logTextArea: TextArea, message: String, exception: Throwable) {
    val timeStamp = dateTimeFormatter.format(Instant.now())
    logTextArea.appendText("[$timeStamp] $message" + System.lineSeparator() + exception.getStackTraceString())
    error(message, exception)
}

/**
 * Extension function that appends error message to log TextArea
 */
fun Logger.error(logTextArea: TextArea, message: String) {
    val timeStamp = dateTimeFormatter.format(Instant.now())
    logTextArea.appendText("[$timeStamp] $message" + System.lineSeparator())
    error(message)
}

/**
 * Extension function that appends info message to log TextArea
 */
fun Logger.info(logTextArea: TextArea, message: String) {
    val timeStamp = dateTimeFormatter.format(Instant.now())
    logTextArea.appendText("[$timeStamp] $message" + System.lineSeparator())
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