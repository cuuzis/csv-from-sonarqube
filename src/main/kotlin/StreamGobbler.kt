import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Consumes process output in order to keep it from hanging
 */
class StreamGobbler(private val inputStream: InputStream) : Runnable {
    private var thread: Thread? = null

    //init { println("Creating thread $inputStream") }

    override fun run() {
        //println("Running thread $inputStream")
        try {
            val reader = BufferedReader(InputStreamReader(inputStream))
            val allText = reader.use(BufferedReader::readText)
            print(allText)
        } catch (e: InterruptedException) {
            println("Thread $inputStream interrupted.")
        }
        //println("Thread $inputStream exiting.")
    }

    fun start() {
        //println("Starting thread $inputStream")
        if (thread == null) {
            thread = Thread(this, inputStream.toString())
            thread!!.start()
        }
    }
}