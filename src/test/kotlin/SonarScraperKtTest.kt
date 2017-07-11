import org.junit.Test

import org.junit.Assert.*

class SonarScraperKtTest {
    @Test
    fun removeSubComponents() {

        val input = listOf<String>("src/files1", "src/files2", "src/files1.java", "src/files1/too-deep.java", "src/files2/too-deep.java")
        val output = listOf<String>("src/files1", "src/files2", "src/files1.java")
        assert(output == removeSubComponents(input))
    }

}