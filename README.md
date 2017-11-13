# Sonarqube Issue Extractor
Extracts:
* Issues from a Sonarqube instance
* Faults from a Jira instance
* Commits from a git repository
## Prerequisites
To extract issues, faults and commits:
* Java 8

To calculate correlations:
* R
## Usage

* Run the jar file in your file explorer  
(from the command line: `java -jar csv-from-sonar-0.1.0-jar-with-dependencies.jar`)
![User interface](readme-projects-explained.png?raw=true)



* Specify link to git repository, to extract commits (e.g. `https://github.com/apache/ambari.git`):  
![User interface](readme-edit-git.png?raw=true)



* Specify link to jira project, to extract faults (e.g. `https://issues.apache.org/jira/projects/AMBARI`):  
![User interface](readme-edit-jira.png?raw=true)

## Compiling
Clone the project and run maven command `mvn clean package` to build the jar file yourself.