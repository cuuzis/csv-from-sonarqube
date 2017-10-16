import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import com.opencsv.bean.CsvToBeanBuilder
import csv_model.architecture_smells.ArchMultiA
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.math.BigDecimal

//val sonarInstance = "http://localhost:9000"
val sonarInstance = "http://sonar.inf.unibz.it"
val workDir = "extraction" + File.separatorChar


fun main(args: Array<String>) {
    val startTime = System.currentTimeMillis()

    //summariseMasByPackages()
    //summariseCDbyFiles()
    //println("Execution completed in ${(System.currentTimeMillis()-startTime)/1000.0} seconds (${(System.currentTimeMillis() - startTime)/60000} minutes)")
    //return

    //groupByDays("extraction/everything-by-commit-23-hist-projects.csv")
    //addRowDifferences("extraction/everything-by-commit-23-hist-projects.csv")
    //return

    val ruleKeys = getRuleKeys()



    // Qualitas Corpus

    val projectKeys = getProjectsContainingString("QC -")//QC - aspectj, QC - jboss, QC - jtopen
    /*
    println("# of code smell types in projects")
    for (project in projectKeys) {
        val folderStr = getProjectFolder(project)
        val issuesFile = folderStr + "current-issues.csv"
        val issuesLines = File(issuesFile).readLines()
        val issuesHeader = issuesLines[0] //"creation-date","update-date","rule","component","effort"
        val issuesRows = issuesLines.subList(1,issuesLines.size).map { it.split(",") }
        val code_smells = issuesRows
                .filter { it[2].startsWith("\"code_smells:") }
                .groupBy{ it[2] } //grouup by rule
        //print("${project} has ${code_smells.count()} code smells:")
        print("${code_smells.count()},$project,")
        println(code_smells.keys.joinToString(";"))
    }
*/

    //groupSmellsByProjects(projectKeys, "grouped-by-projects.csv")

    /*
    for (sonarKey in projectKeys) {
        val folderStr = getProjectFolder(sonarKey)
        val measuresFile = folderStr + "current-measures.csv"
        saveCurrentMeasures(measuresFile, sonarKey)
    }
    // because of new_security_rating etc. headers are different
    mergeExtractedCsvFiles(projectKeys, "current-measures.csv")
    return
    */

    /*
    // check mann-whitney u-test: delete until return
    val correlationFiles = listOf(
            "by-project-correlation-cycle-size.csv",
            "by-project-correlation-cycle-classes.csv",
            "by-project-correlation-cycle-exists.csv",
            "by-project-correlation-mas-ud.csv",
            "by-project-correlation-mas-hl.csv",
            "by-project-correlation-mas-cd.csv",
            "by-project-correlation-mas-exists.csv"
    )
    val rows = mutableListOf<Array<String>>()
    for (correlationFile in correlationFiles) {
        val correlationBeans = CsvToBeanBuilder<Correlations>(FileReader(workDir + correlationFile))
                .withType(Correlations::class.java).build().parse()
                .map { it as Correlations }
        correlationBeans.filter { it.shapiroWilkPvalue?.toDoubleOrNull() != null && it.shapiroWilkPvalue.toDouble() > 0.05 }
                .forEach { println(it.project + ", " + it.issueName) }
    }
    println("DONE")
    return
*/

    // extract issues from sonarInstance, takes long
    //for (projectKey in projectKeys)
    //    saveIssues("current-issues.csv", projectKey, "OPEN", ruleKeys)

    // map sonar issues to arch cycle smells (~2h30min)
    //mapIssuesToCyclicDependencies(projectKeys, false)
    mapIssuesToCyclicDependencies(projectKeys, true)
    // map sonar issues to arch MAS smells (40min)
    //mapIssuesToMAS(projectKeys, false)
    mapIssuesToMAS(projectKeys, true)

    // calculate correlations (~30min)
    calculateCorrelationsForProjects(projectKeys)
    // merge correlation values from projects (~1s)
    mergeCorrelationsByProject(projectKeys)
    // extracts a summary for the calculated correlations (~90s)
    summariseCorrelations(outFile = "by-project-summary.csv")
    // runs R scripts treating the whole dataset as a single project. Takes at least a night.
    //calculateFullDatasetCorrelation(projectKeys)

    println("Execution completed in ${(System.currentTimeMillis()-startTime)/1000.0} seconds (${(System.currentTimeMillis() - startTime)/60000} minutes)")
    return



    val projectList = mutableListOf<HistoryProject>()

    //done:
    projectList.add(HistoryProject("org.apache:commons-cli", "CLI", "https://github.com/apache/commons-cli.git", false))
    projectList.add(HistoryProject("org.apache:ambari", "AMBARI", "https://github.com/apache/ambari.git", false))
    projectList.add(HistoryProject("org.apache:bcel", "BCEL", "https://github.com/apache/commons-bcel.git", false))
    projectList.add(HistoryProject("org.apache:dbutils", "DBUTILS", "https://github.com/apache/commons-dbutils.git", false))
    projectList.add(HistoryProject("org.apache:deamon", "DAEMON", "https://github.com/apache/commons-daemon.git", false))
    projectList.add(HistoryProject("org.apache:digester", "DIGESTER", "https://github.com/apache/commons-digester.git", false))
    projectList.add(HistoryProject("org.apache:dbcp", "DBCP", "https://github.com/apache/commons-dbcp.git", false))
    projectList.add(HistoryProject("org.apache:commons-exec", "EXEC", "https://github.com/apache/commons-exec.git", false))
    projectList.add(HistoryProject("org.apache:commons-fileupload", "FILEUPLOAD", "https://github.com/apache/commons-fileupload.git", false))
    projectList.add(HistoryProject("org.apache:httpclient", "HTTPCLIENT", "https://github.com/apache/httpcomponents-client.git", false))
    projectList.add(HistoryProject("org.apache:commons-io", "IO", "https://github.com/apache/commons-io.git", false))
    projectList.add(HistoryProject("org.apache:commons-jelly", "JELLY", "https://github.com/apache/commons-jelly.git", false))
    projectList.add(HistoryProject("org.apache:commons-jexl", "JEXL", "https://github.com/apache/commons-jexl.git", false))
    projectList.add(HistoryProject("org.apache:jxpath", "JXPATH", "https://github.com/apache/commons-jxpath.git", false))
    projectList.add(HistoryProject("org.apache:net", "NET", "https://github.com/apache/commons-net.git", false))
    projectList.add(HistoryProject("org.apache:ognl", "OGNL", "https://github.com/apache/commons-ognl.git", false))
    projectList.add(HistoryProject("org.apache:sshd", "SSHD", "https://github.com/apache/mina-sshd.git", false))
    projectList.add(HistoryProject("org.apache:vfs", "VFS", "https://github.com/apache/commons-vfs.git", false))
    projectList.add(HistoryProject("org.apache:validator", "VALIDATOR", "https://github.com/apache/commons-validator.git", false))
    projectList.add(HistoryProject("org.apache:codec", "CODEC", "https://github.com/apache/commons-codec.git", false))
    projectList.add(HistoryProject("org.apache:beanutils", "BEANUTILS", "https://github.com/apache/commons-beanutils.git", false))
    projectList.add(HistoryProject("org.apache:configuration", "CONFIGURATION", "https://github.com/apache/commons-configuration.git", false))
    projectList.add(HistoryProject("org.apache:collections", "COLLECTIONS", "https://github.com/apache/commons-collections.git", false))

    //need extraction
    //projectList.add(HistoryProject("org.apache:hive", "HIVE", "https://github.com/apache/hive.git", false)) java memory overflow
    //projectList.add(HistoryProject("org.apache:lucene-core", "LUCENE", "https://github.com/apache/lucene-solr.git", false)) java memory overflow

    //need analysis:
    //projectList.add(HistoryProject("org.apache:cocoon", "COCOON", "https://github.com/apache/cocoon.git", false))


    //NOK:
    //projectList.add(HistoryProject("org.apache:httpcore", "HTTPCORE", "https://github.com/apache/httpcomponents-core.git", false)) 860/2804 analyses failed
    //projectList.add(HistoryProject("tomcat_QC", "", "https://github.com/apache/tomcat.git", false)) bugzilla issues
    //projectList.add(HistoryProject("jgit_QC", "", "https://github.com/eclipse/jgit.git", false)) bugzilla issues
    //projectList.add(HistoryProject("junit_QC", "", "https://github.com/junit-team/junit4.git", false)) github issues

    /*projectList.add(HistoryProject("bcel", "BCEL", "https://github.com/apache/commons-bcel.git", true))
    projectList.add(HistoryProject("beanutils", "BEANUTILS", "https://github.com/apache/commons-beanutils.git", true))
    projectList.add(HistoryProject("bsf", "BSF", "https://github.com/apache/commons-bsf.git", true))
    projectList.add(HistoryProject("cocoon", "COCOON", "https://github.com/apache/cocoon.git", true))
    projectList.add(HistoryProject("codec", "CODEC", "https://github.com/apache/commons-codec.git", true))
    projectList.add(HistoryProject("collections", "COLLECTIONS", "https://github.com/apache/commons-collections.git", true))
    projectList.add(HistoryProject("configuration", "CONFIGURATION", "https://github.com/apache/commons-configuration.git", true))
    projectList.add(HistoryProject("daemon", "DAEMON", "https://github.com/apache/commons-daemon.git", true))
    projectList.add(HistoryProject("cli", "CLI", "https://github.com/apache/commons-cli.git", true))*/



    //mergeExtractedSameCsvFiles(projectList.map { it.sonarKey }, "correlation-faults.csv")
    //mergeExtractedSameCsvFiles(projectList.map { it.sonarKey }, "fault-file-commit-summary.csv")
    //mergeExtractedCsvFiles(projectList.map { it.sonarKey }, "fault-file-commit.csv")
    //mergeExtractedCsvFiles(projectList.map { it.sonarKey }, "measure-history.csv")




    //merge everthing to each SQ analysis
    for (project in projectList) {

        val folderStr = getProjectFolder(project.sonarKey)
        val issuesFile = folderStr + "sonar-issues.csv"
        val measuresFile = folderStr + "measure-history.csv"
        val jiraFaultFile = folderStr + "jira-faults.csv"
        val gitCommitFile = folderStr + "git-commits.csv"
        val measuresAndIssuesFile = folderStr + "measures-and-issues.csv"
        mergeAllToAnalysis(measuresFile, issuesFile, jiraFaultFile, gitCommitFile, measuresAndIssuesFile)
    }
    mergeExtractedCsvFiles(projectList.map { it.sonarKey }, "measures-and-issues.csv")
    return



    //save measures for project
    for (project in projectList) {
        //projectList.parallelStream().forEach { project ->

        val folderStr = getProjectFolder(project.sonarKey)
        //val issuesFile = folderStr + "sonar-issues.csv"
        //val measuresFile = folderStr + "current-measures.csv"
        //saveCurrentMeasures(measuresFile, project.sonarKey)
    }
    //mergeExtractedSameCsvFiles(projectList.map { it.sonarKey }, "sonar-issues.csv")

    /*
    //count issues: result = 22307 code_smells
    val masFileLines = File("extraction/sonar-issues-23-hist-projects.csv").readLines()
    val header = masFileLines.first().split(",")
    println(header)


    //project creation-date update-date rule component effort
    val rows = masFileLines.subList(1,masFileLines.size).map { it.split(",") }
    val codeSmellCount = rows.count {
        it[3].startsWith("code_smells:")
    }
    println("codeSmellCount: $codeSmellCount")
    val componentsInfected = rows.filter { it[3].startsWith("code_smells:") }
            .groupBy { it[4] } //components, also if name changed
            .count()
    println("componentsInfected: $componentsInfected")
    */


    /*
    println("# of code smell types in projects")
    for (project in projectList) {
        val folderStr = getProjectFolder(project.sonarKey)
        val issuesFile = folderStr + "sonar-issues.csv"
        val issuesLines = File(issuesFile).readLines()
        val issuesHeader = issuesLines[0] //"creation-date","update-date","rule","component","effort"
        val issuesRows = issuesLines.subList(1,issuesLines.size).map { it.split(",") }
        val code_smells = issuesRows
                .filter { it[2].startsWith("\"code_smells:") }
                .groupBy{ it[2] } //grouup by rule
        //print("${project.sonarKey} has ${code_smells.count()} code smells:")
        print("${code_smells.count()},${project.sonarKey},")
        println(code_smells.keys.joinToString(";"))
    }
*/


    return

    for (project in projectList) {
    //projectList.parallelStream().forEach { project ->

        val folderStr = getProjectFolder(project.sonarKey)
        val issuesFile = folderStr + "sonar-issues.csv"
        val measuresFile = folderStr + "measure-history.csv"
        //val measuresAndIssuesFile = folderStr + "measures-and-issues.csv"
        val jiraFaultFile = folderStr + "jira-faults.csv"
        val gitCommitFile = folderStr + "git-commits.csv"
        val faultFileCommitFile = folderStr + "fault-file-commit.csv"
        val faultsAndIssuesFile = folderStr + "faults-and-issues.csv"
        val faultsIssueCountFile = folderStr + "faults-issue-count.csv"

        makeEmptyFolder(folderStr)
        saveIssues(issuesFile, project.sonarKey, "CLOSED,OPEN", ruleKeys)

        saveMeasureHistory(measuresFile, project.sonarKey)
        //mergeAllToAnalysis(measuresFile, issuesFile, measuresAndIssuesFile)

        saveJiraIssues(jiraFaultFile, project.jiraKey)
        saveGitCommits(gitCommitFile, project.gitLink)

        mapFaultFileCommit(issuesFile, jiraFaultFile, gitCommitFile, measuresFile, faultFileCommitFile, project.dailyAnalysis)
        val rFileList = listOf(
                "history-correlation-commits.R",
                "history-correlation-faults.R")
        rFileList.parallelStream().forEach { rFile -> runRscript(File(rFile), File(folderStr)) }
        runRscript(File("history-correlation-commits.R"), File(folderStr))

        mapFaultsToIssues(gitCommitFile, jiraFaultFile, issuesFile, faultsAndIssuesFile)
        groupIssuesByFaults(faultsAndIssuesFile, faultsIssueCountFile)
    }

    println("Execution completed in ${(System.currentTimeMillis()-startTime)/1000.0} seconds (${(System.currentTimeMillis() - startTime)/60000} minutes)")
}
/*
fun groupByDays(fileName: String) {
    val reader = CSVReader(FileReader(File(fileName)))
    val fileLines = reader.readAll()
    val header = fileLines.first()

    // save data to file
    FileWriter(fileName.replace(".csv","-by-days.csv")).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeNext(header + numberCols.map { it + "-diff" })
        csvWriter.writeAll(
                rows.withIndex().map {
                    (originalRowIdx, originalRowValues) -> originalRowValues + numberRows[originalRowIdx].map { it.toString() }.toTypedArray()  })
    }
    println("Measures and issues merged in ${fileName.replace(".csv","-with-diff.csv")}")
}
*/

fun addRowDifferences(fileName: String) {
    val reader = CSVReader(FileReader(File(fileName)))
    val fileLines = reader.readAll()
    val header = fileLines.first()
    val rows = fileLines.subList(1,fileLines.size)
    val notANumberIndexes = mutableListOf<Int>()
    val oldValues = Array<BigDecimal>(header.size, { (BigDecimal(0)) } )
    val diffRows = mutableListOf<Array<BigDecimal>>()
    var projectName = ""
    for (row in rows) {
        if (projectName != row[0]) {
            projectName = row[0]
            oldValues.fill(BigDecimal(0))
        }
        val diffRow = Array<BigDecimal>(header.size, { (BigDecimal(0)) } )
        row.forEachIndexed { index, value ->
            if (!(index in notANumberIndexes)) {
                try {
                    val newValue = BigDecimal(value)
                    diffRow[index] = newValue.minus(oldValues[index])
                    oldValues[index] = newValue
                } catch (e: NumberFormatException) {
                    notANumberIndexes.add(index)
                }
            }
        }
        diffRows.add(diffRow)
    }
    val numberCols = header.filterIndexed { index, _ -> !(index in notANumberIndexes) }
    val numberRows = diffRows.map { it.filterIndexed { index, _ -> !(index in notANumberIndexes) } }
    /*println(numberCols)
    for (diffRow in diffRows) {
        println(diffRow.filterIndexed { index, _ -> !(index in notANumberIndexes) }
                .joinToString(","))
    }*/

    // save data to file
    FileWriter(fileName.replace(".csv","-with-diff.csv")).use { fw ->
        val csvWriter = CSVWriter(fw)
        csvWriter.writeNext(header + numberCols.map { it + "-diff" })
        csvWriter.writeAll(
                rows.withIndex().map {
            (originalRowIdx, originalRowValues) -> originalRowValues + numberRows[originalRowIdx].map { it.toString() }.toTypedArray()  })
    }
    println("Measures and issues merged in ${fileName.replace(".csv","-with-diff.csv")}")
}

fun summariseMasByPackages() {
    val masFileLines = File("extraction/mas-issues-by-package.csv").readLines()
    val header = masFileLines.first().split(",")
    println(header)
    val smellindexes = listOf<Int>(

            header.indexOf("The Dispensables"),
            header.indexOf("The Encapsulators"),
            header.indexOf("The Object-Orientation Abusers"),
            //header.indexOf("code_smells:antisingleton"),  == "The Object-Orientation Abusers"
            //header.indexOf("code_smells:class_data_private"),
            //header.indexOf("code_smells:complex_class"),
            //header.indexOf("code_smells:lazy_class"),
            //header.indexOf("code_smells:long_method"),
            //header.indexOf("code_smells:long_parameter_list"),
            header.indexOf("code_smells:spaghetti_code"),//
            header.indexOf("code_smells:baseclass_abstract"),//
            //header.indexOf("code_smells:many_field_attributes_not_complex"),
            //header.indexOf("code_smells:message_chains"),
            header.indexOf("code_smells:speculative_generality"),//
            header.indexOf("code_smells:refused_parent_bequest"),//
            header.indexOf("code_smells:swiss_army_knife")//
            //header.indexOf("code_smells:large_class")
    )
    println(smellindexes)

    //project	package	ud	hl	cd	ud-hl-cd-exists	The Bloaters	The Dispensables	The Encapsulators	The Object-Orientation Abusers	code_smells:spaghetti_code	squid:AssignmentInSubExpressionCheck	squid:ClassCyclomaticComplexity	squid:ClassVariableVisibilityCheck	squid:CommentedOutCodeLine	squid:EmptyStatementUsageCheck	squid:ForLoopCounterChangedCheck	squid:HiddenFieldCheck	squid:LabelsShouldNotBeUsedCheck	squid:MethodCyclomaticComplexity	squid:MissingDeprecatedCheck	squid:ModifiersOrderCheck	squid:RedundantThrowsDeclarationCheck	squid:RightCurlyBraceStartLineCheck	squid:S00100	squid:S00101	squid:S00105	squid:S00107	squid:S00108	squid:S00112	squid:S00115	squid:S00116	squid:S00117	squid:S00120	squid:S00122	squid:S106	squid:S1066	squid:S1067	squid:S1068	squid:S1118	squid:S1125	squid:S1126	squid:S1132	squid:S1133	squid:S1135	squid:S1141	squid:S1143	squid:S1147	squid:S1148	squid:S1149	squid:S1150	squid:S1151	squid:S1153	squid:S1155	squid:S1158	squid:S1160	squid:S1161	squid:S1163	squid:S1166	squid:S1168	squid:S1170	squid:S1172	squid:S1181	squid:S1185	squid:S1186	squid:S1188	squid:S1191	squid:S1192	squid:S1193	squid:S1197	squid:S1199	squid:S1201	squid:S1206	squid:S1210	squid:S1213	squid:S1214	squid:S1215	squid:S1226	squid:S1244	squid:S1301	squid:S1319	squid:S134	squid:S135	squid:S1444	squid:S1481	squid:S1488	squid:S1598	squid:S1700	squid:S1872	squid:S1905	squid:S1948	squid:S2068	squid:S2094	squid:S2130	squid:S2131	squid:S2153	squid:S2157	squid:S2178	squid:S2184	squid:S2250	squid:S2274	squid:S2276	squid:S2386	squid:SwitchLastCaseIsDefaultCheck	squid:UnusedPrivateMethod	squid:UselessImportCheck	code_smells:baseclass_abstract	code_smells:speculative_generality	squid:ObjectFinalizeOverridenCheck	squid:S1065	squid:S1134	squid:S1145	squid:S1157	squid:S1165	squid:S1171	squid:S1182	squid:S1221	squid:S1223	squid:S128	squid:S1312	squid:S1479	squid:S1596	squid:S1764	squid:S1860	squid:S1862	squid:S2065	squid:S2066	squid:S2160	squid:S2166	squid:S2272	squid:UselessParenthesesCheck	code_smells:refused_parent_bequest	squid:S1190	squid:S1219	squid:S1220	squid:S1452	squid:S1994	squid:S2116	squid:S2134	squid:S2183	squid:S2251	squid:S2388	squid:S2440	squid:S1194	squid:S1314	squid:S2133	code_smells:swiss_army_knife
    val rows = masFileLines.subList(1,masFileLines.size).map { it.split(",") }
    println("project,udArch,hlArch,cdArch,masExists,packagesWithUDandCS,packagesWithHLandCS,packagesWithCDandCS,packagesWithMASandCS,packagesWithCS")
    rows.groupBy { it[0] }
            .forEach {
                val rowValues = it.value
                val udArch = rowValues.sumBy { it[2].toInt() }
                val hlArch = rowValues.sumBy { it[3].toInt() }
                val cdArch = rowValues.sumBy { it[4].toInt() }
                val masArchExists = rowValues.sumBy { it[5].toInt() }
                val packagesWithUDandCS = rowValues.sumBy {
                    var allCS = 0
                    for (idx in smellindexes) { allCS += it[idx].toInt() }
                    if (it[2].toInt() > 0 && allCS > 0)
                        1
                    else
                        0
                }
                val packagesWithHLandCS = rowValues.sumBy {
                    var allCS = 0
                    for (idx in smellindexes) { allCS += it[idx].toInt() }
                    if (it[3].toInt() > 0 && allCS > 0)
                        1
                    else
                        0
                }
                val packagesWithCDandCS = rowValues.sumBy {
                    var allCS = 0
                    for (idx in smellindexes) { allCS += it[idx].toInt() }
                    if (it[4].toInt() > 0 && allCS > 0)
                        1
                    else
                        0
                }
                val packagesWithMASandCS = rowValues.sumBy {
                    var allCS = 0
                    for (idx in smellindexes) { allCS += it[idx].toInt() }
                    if (it[5].toInt() > 0 && allCS > 0)
                        1
                    else
                        0
                }
                val packagesWithCS = rowValues.sumBy {
                    var allCS = 0
                    for (idx in smellindexes) { allCS += it[idx].toInt() }
                    if (allCS > 0)
                        1
                    else
                        0
                }
                /*val anyPackagesInFile = rowValues.sumBy {
                    var allViolatins = 0
                    for (idx in smellindexes) { allViolatins += it[idx].toInt() }
                    if (it[5].toInt() > 0 && allCS > 0)
                        1
                    else
                        0
                }*/
                println("${it.key},$udArch,$hlArch,$cdArch,$masArchExists,$packagesWithUDandCS,$packagesWithHLandCS,$packagesWithCDandCS,$packagesWithMASandCS,$packagesWithCS")
            }

}

fun summariseCDbyFiles() {
    val cdFileLines = File("extraction/overlap-by-class.csv").readLines()
    val header = cdFileLines.first().split(",")
    println(header)

    //project	package	ud	hl	cd	ud-hl-cd-exists	The Bloaters	The Dispensables	The Encapsulators	The Object-Orientation Abusers	code_smells:spaghetti_code	squid:AssignmentInSubExpressionCheck	squid:ClassCyclomaticComplexity	squid:ClassVariableVisibilityCheck	squid:CommentedOutCodeLine	squid:EmptyStatementUsageCheck	squid:ForLoopCounterChangedCheck	squid:HiddenFieldCheck	squid:LabelsShouldNotBeUsedCheck	squid:MethodCyclomaticComplexity	squid:MissingDeprecatedCheck	squid:ModifiersOrderCheck	squid:RedundantThrowsDeclarationCheck	squid:RightCurlyBraceStartLineCheck	squid:S00100	squid:S00101	squid:S00105	squid:S00107	squid:S00108	squid:S00112	squid:S00115	squid:S00116	squid:S00117	squid:S00120	squid:S00122	squid:S106	squid:S1066	squid:S1067	squid:S1068	squid:S1118	squid:S1125	squid:S1126	squid:S1132	squid:S1133	squid:S1135	squid:S1141	squid:S1143	squid:S1147	squid:S1148	squid:S1149	squid:S1150	squid:S1151	squid:S1153	squid:S1155	squid:S1158	squid:S1160	squid:S1161	squid:S1163	squid:S1166	squid:S1168	squid:S1170	squid:S1172	squid:S1181	squid:S1185	squid:S1186	squid:S1188	squid:S1191	squid:S1192	squid:S1193	squid:S1197	squid:S1199	squid:S1201	squid:S1206	squid:S1210	squid:S1213	squid:S1214	squid:S1215	squid:S1226	squid:S1244	squid:S1301	squid:S1319	squid:S134	squid:S135	squid:S1444	squid:S1481	squid:S1488	squid:S1598	squid:S1700	squid:S1872	squid:S1905	squid:S1948	squid:S2068	squid:S2094	squid:S2130	squid:S2131	squid:S2153	squid:S2157	squid:S2178	squid:S2184	squid:S2250	squid:S2274	squid:S2276	squid:S2386	squid:SwitchLastCaseIsDefaultCheck	squid:UnusedPrivateMethod	squid:UselessImportCheck	code_smells:baseclass_abstract	code_smells:speculative_generality	squid:ObjectFinalizeOverridenCheck	squid:S1065	squid:S1134	squid:S1145	squid:S1157	squid:S1165	squid:S1171	squid:S1182	squid:S1221	squid:S1223	squid:S128	squid:S1312	squid:S1479	squid:S1596	squid:S1764	squid:S1860	squid:S1862	squid:S2065	squid:S2066	squid:S2160	squid:S2166	squid:S2272	squid:UselessParenthesesCheck	code_smells:refused_parent_bequest	squid:S1190	squid:S1219	squid:S1220	squid:S1452	squid:S1994	squid:S2116	squid:S2134	squid:S2183	squid:S2251	squid:S2388	squid:S2440	squid:S1194	squid:S1314	squid:S2133	code_smells:swiss_army_knife
    val rows = cdFileLines.subList(1,cdFileLines.size).map { it.split(",") }
    println("project,componentsWithUD,componentsWithUDandCS,componentsWithCS")
    rows.groupBy { it[0] }
            .forEach {
                val rowValues = it.value
                val componentsWithUD = rowValues.sumBy { it[5].toInt() }
                val componentsWithUDandCS = rowValues.sumBy { it[6].toInt() }
                val componentsWithCS = rowValues.sumBy { it[7].toInt() }
                println("${it.key},$componentsWithUD,$componentsWithUDandCS,$componentsWithCS")
            }

}

fun groupSmellsByProjects(projectKeys: List<String>, filename: String) {
    //mergeExtractedCsvFiles(projectKeys, "cycles-issues-by-class.csv")
    //mergeExtractedCsvFiles(projectKeys, "cycles-issues-by-cycle.csv")
    //mergeExtractedCsvFiles(projectKeys, "mas-issues-by-package.csv")

    //add project name to csv2
    /*
    val fileWithProjects = File("extraction/cycles-issues-by-class-taxonomy.csv").readLines()
    val fileWithoutProjects = File("extraction/cycles-issues-by-class-no-taxonomy.csv")
    File("extraction/cycles-issues-by-class-no-taxonomy-2.csv").bufferedWriter().use{ resultFile ->
        fileWithoutProjects.readLines().forEachIndexed { index, line ->
            val lineWithProject = fileWithProjects[index].split(",")[0] + "," + line
            resultFile.write(lineWithProject)
            resultFile.newLine()
        }
    }*/

    println("project,udNum,hlNum,cdNum,{udNum+hlNum+cdNum},packages,udPackages,hlPackages,cdPackages")
    for (project in projectKeys) {
        val archMasFile = findArchitectureSmellFile(project, "mas.csv")
        val archSmellBeans = CsvToBeanBuilder<ArchMultiA>(FileReader(archMasFile))
                .withType(ArchMultiA::class.java).build().parse()
                .map { it as ArchMultiA }
        var udNum = 0
        var hlNum = 0
        var cdNum = 0
        var packages = 0
        var udPackages = 0
        var hlPackages = 0
        var cdPackages = 0

        archSmellBeans.forEach {
            it.ud?.toInt()?.let { value ->  udNum += value }
            it.hl?.toInt()?.let { value ->  hlNum += value }
            it.cd?.toInt()?.let { value ->  cdNum += value }
            it.ud?.toInt()?.let { value ->  if (value > 0) udPackages++ }
            it.hl?.toInt()?.let { value ->  if (value > 0) hlPackages++ }
            it.cd?.toInt()?.let { value ->  if (value > 0) cdPackages++ }
            packages++
        }
        val all = udNum + hlNum + cdNum
        println("$project,$udNum,$hlNum,$cdNum,${udNum+hlNum+cdNum},$packages,$udPackages,$hlPackages,$cdPackages")
    }
    return


    //group by project
    val issueAggregations = mutableMapOf<Int, Aggregations>()
    //val fileWithTaxonomy = File("extraction/cycles-issues-by-class-taxonomy.csv")
    val fileWithoutTaxonmy = File("extraction/cycles-issues-by-class-no-taxonomy.csv")
    File("extraction/cycles-issues-by-class-no-taxonomy-grouped.csv").bufferedWriter().use{ resultFile ->
        //val input = fileWithTaxonomy.readLines()
        val reader = CSVReader(FileReader(fileWithoutTaxonmy))
        val input = reader.readAll()
        //val inputHeader = input[0].split(",")
        val inputHeader = input[0]
        //val rows = input.subList(1, input.size).map { it.split(",") }
        val rows = input.subList(1, input.size)
        val rowsGroupedByProject = rows.groupBy { it[0] }
        val sonarDebtIdx = 3 //arch-cycle-size
        for (idx in sonarDebtIdx..inputHeader.lastIndex) {
            val sum = rowsGroupedByProject.mapValues { it.value.sumBy { it[idx].toInt() } }
            val min = rowsGroupedByProject.mapValues { it.value.minBy { it[idx].toInt() }!![idx].toInt() }
            val max = rowsGroupedByProject.mapValues { it.value.maxBy { it[idx].toInt() }!![idx].toInt() }
            val avg = rowsGroupedByProject.mapValues { it.value.sumBy { it[idx].toInt() } / it.value.size.toDouble() }
            issueAggregations.put(idx, Aggregations(sum = sum, min = min, max = max, avg = avg))
        }

        resultFile.write("project," + inputHeader.asList().subList(sonarDebtIdx, inputHeader.size).joinToString(","))
        resultFile.newLine()
        for (componentKey in rowsGroupedByProject.keys) {
            val row = mutableListOf<String>()
            row.add(componentKey)
            for (idx in sonarDebtIdx..inputHeader.lastIndex) {
                row.add(issueAggregations[idx]!!.sum[componentKey].toString())
                //row.add(issueAggregations[idx]!!.min[componentKey].toString())
                //row.add(issueAggregations[idx]!!.max[componentKey].toString())
                //row.add(issueAggregations[idx]!!.avg[componentKey].toString())
            }
            resultFile.write(row.joinToString(","))
            resultFile.newLine()
        }
    }



}

private class HistoryProject(val sonarKey: String, val jiraKey: String, val gitLink: String, val dailyAnalysis: Boolean)
