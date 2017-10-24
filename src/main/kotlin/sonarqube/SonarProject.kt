package sonarqube

import javafx.beans.property.SimpleStringProperty

class SonarProject constructor(key: String, name: String) {

    private val key: SimpleStringProperty = SimpleStringProperty(key)
    private val name: SimpleStringProperty = SimpleStringProperty(name)

    /**
     * Getter for TableColumn "key"
     */
    fun getKey(): String {
        return key.get()
    }

    /**
     * Getter for TableColumn "name"
     */
    fun getName(): String {
        return name.get()
    }
}