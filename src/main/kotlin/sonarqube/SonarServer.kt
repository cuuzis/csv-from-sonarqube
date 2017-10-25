package sonarqube

class SonarServer(val serverAddress: String) {

    val projects = mutableListOf<SonarProject>()
    private val ruleKeys = mutableListOf<String>()

    init {
        sonarInstanceToRemove = serverAddress
    }

    /**
     * Returns rules available on server for Java
     */
    fun getRuleKeys(): List<String> {
        if (ruleKeys.isEmpty()) {
            ruleKeys.addAll(sonarqube.getRuleKeys(serverAddress))
        }
        return ruleKeys
    }
}