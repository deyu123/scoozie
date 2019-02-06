# Scoozie

[![License][licenseImg]] [licenseLink] [![Codacy][codacyImg2]][codacyLink][![CodeCovImg]][CodCovLink]

Latest `version`s: 

* Scala 2.10:  [![Maven][210mavenImg]][mavenLink]
* Scala 2.11:  [![Maven][211mavenImg]][mavenLink]
* SCala 2.12:  [![Maven][212mavenImg]][mavenLink]

An Oozie artefact builder library for scala.  it was created to allow developers  to quickly generate new Oozie artefacts without copy/pasting xml and avoiding typos in the XML they create.

Scoozie was created with CDH in mind and supports oozie version 4.1.0.

## Usage

Scoozie provides all its functionality via the `Scoozie` object and the following sub-objects are exposed:

* `Actions`: Provides methods for creating Oozie actions.  Mehods for creating the following Oozie actions are provided:
	* DistCP
	* Email
	* FileSystem
	* Hive2
	* Hive
	* Java
	* Pig
	* Shell
	* Spark
	* Sqoop
	* Ssh
	* Sub-Workflow
	* Oozie control nodes (Start, End, Fork, Kill, Decision)


* `FileSystem`: Provides methods for creating file system operations for FileSystem actions and an action's prepare steps:
	* chmod
	* delete
	* mkdir
	* move
	* touchz

* `Prepare`: Provides methods for creating prepare steps for actions

*  `Configuration`: Provides methods for creating credentials, properties and other configuration.

* `Test`: Provides methods for testing and validating workflows

* `Functions`: Provides the following three sub functions:
	* `WorkFlow`: Common oozie workflow functions such as getting the workflow id or the last error message
	* `Basic`: Oozie string and time EL functions
	* `Coordinator`: Oozie co-ordinator time functions (datasets are not currently supported)

In addition workflows and coordinators may be generated via the `Scoozie.workflow` and `Scoozie.coordinator` methods.

### Defining transitions between actions

Each generated action exposes an `okTo` and an `errorTo` function that take the next action as an argument.  Transitions should beging with the `Start` action and end with the `End` or `Kill` action.  

### Authentication
Each action expects an implicit `Option[Credentials]` object for specifying credentials for a workflow.  These can be created via the following methods:

For jobs without credentials:
```
implicit val credentials: Option[Credentials] = Scoozie.Configuration.emptyCredentials
```
For jobs with credentials:
```
  implicit val credentials: Option[Credentials] = Scoozie.Configuration.credentials("hive-credentials","hive", Seq(Property(name = "name", value = "value"))
```


### Testing
Scoozie provides validation of the generated XML against the Oozie XSDs for each of the actions listed above as well as the workflow and co-ordinators (via the `Scoozie.Test.validate(x)` methods.  In addition basic loop checking and testing of workflow transitions can be achived via the `WorkflowTestRunner ` class.

For example:

```
val transitions = {
      val spark = sparkAction okTo End() errorTo kill
      val hive3 = hiveAction3 okTo End() errorTo kill
      val hive2 = hiveAction2 okTo End() errorTo kill
      val decision = Decision("decisionNode", spark, Switch(hive2, "${someVar}"), Switch(hive3, "${someOtherVar}"))
      val hive = hiveAction okTo decision errorTo kill
      Start() okTo hive
    }

    val workflow = Workflow(name = "sampleWorkflow",
                            path = "", //HDFS path
                            transitions = transitions,
                            configuration =
                              Configuration(Seq(Property(name = "workflowprop", value = "workflowpropvalue"))),
                            yarnConfig = yarnConfig)

    val workflowTestRunner = WorkflowTestRunner(workflow, Seq(hiveAction3.name))

    workflowTestRunner.traversalPath should be("start -> hiveAction -> decisionNode -> sparkAction -> end")
```

The transitions are represented as a string with the ` -> ` symbol representing a transistion between two nodes.  No distinction is made between a successful transition or an error transition for this representation.  Forks are represented as 

```
Start -> someFork -> (action1, action2) -> someJoin -> End
```

or ona failed node in a fork

```
Start -> someFork -> (action1, action2) -> kill
```

Further examples can be seen in the `WorkflowTestRunnerSpec` class. This class takes constructor aguments to specify the names of actions to fail, or to use on decision points.

### Exposed traits 
The `GeneratedCoordinator` and `GeneratedWorkflow` traits are exposed for clients of this library to implement and are the suggested method of use.  They expose methods for saving both the generated workflows and their respective properties.  Files generated via these traits will be named:

* `workflow.xml`
* `coordinator.xml`
* `job.properties`

and deposited in the specified folder.

## Job properties
In addition to generating workflows and coordinators, Scoozie will generate their corresponding job properties.  The property names in these files are also generated and follow the pattern: `actionName_propertyType` for example `someJavaAction_mainClass`.


### Example

The code below shows a worked example of a Scoozie client:

```
class TestJob(jobTracker: String, nameNode: String, yarnProperties: Map[String, String])
    extends GeneratedWorkflow
    with GeneratedCoordinator {

  private implicit val credentials: Option[Credentials] = Scoozie.Configuration.emptyCredentials
  
  private val yarnConfig = Scoozie.Configuration.yarnConfiguration(jobTracker, nameNode)
  private val kill = Scoozie.Actions.kill("Workflow failed")

  private val sparkAction = Scoozie.Actions.spark(name = "doASparkThing",
                                                  sparkSettings = "/path/to/spark/settings",
                                                  sparkMasterURL = "masterURL",
                                                  sparkMode = "mode",
                                                  sparkJobName = "JobName",
                                                  mainClass = "org.antipathy.Main",
                                                  sparkJar = "/path/to/jar",
                                                  sparkOptions = "spark options",
                                                  commandLineArgs = Seq(),
                                                  files = Seq(),
                                                  prepareOption = None,
                                                  configuration = Scoozie.Configuration.emptyConfiguration,
                                                  yarnConfig = yarnConfig)

  private val emailAction = Scoozie.Actions.email(name = "alertFailure",
                                                  to = Seq("a@a.com", "b@b.com"),
                                                  subject = "message subject",
                                                  body = "message body")

  private val shellAction = Scoozie.Actions.shell(name = "doAShellThing",
                                                  prepareOption = None,
                                                  scriptName = "script.sh",
                                                  scriptLocation = "/path/to/script.sh",
                                                  commandLineArgs = Seq(),
                                                  envVars = Seq(),
                                                  files = Seq(),
                                                  captureOutput = true,
                                                  configuration = Scoozie.Configuration.emptyConfiguration,
                                                  yarnConfig = yarnConfig)

  private val hiveAction = Scoozie.Actions.hive(name = "doAHiveThing",
                                                hiveSettingsXML = "/path/to/settings.xml",
                                                scriptName = "someScript.hql",
                                                scriptLocation = "/path/to/someScript.hql",
                                                parameters = Seq(),
                                                prepareOption = None,
                                                configuration = Scoozie.Configuration.emptyConfiguration,
                                                yarnConfig = yarnConfig)

  private val javaAction = Scoozie.Actions.java(name = "doAJavaThing",
                                                mainClass = "org.antipathy.Main",
                                                javaJar = "/path/to/jar",
                                                javaOptions = "java options",
                                                commandLineArgs = Seq(),
                                                captureOutput = false,
                                                files = Seq(),
                                                prepareOption =
                                                  Scoozie.Prepare.prepare(Seq(Scoozie.Prepare.delete("/some/path"))),
                                                configuration = Scoozie.Configuration.emptyConfiguration,
                                                yarnConfig = yarnConfig)

  private val start = Scoozie.Actions.start

  private val transitions = {
    val errorMail = emailAction okTo kill errorTo kill
    val mainJoin = Scoozie.Actions.join("mainJoin", Scoozie.Actions.end)
    val java = javaAction okTo mainJoin errorTo errorMail
    val hive = hiveAction okTo mainJoin errorTo errorMail
    val mainFork = Scoozie.Actions.fork("mainFork", Seq(java, hive))
    val shell = shellAction okTo mainFork errorTo errorMail
    val spark = sparkAction okTo mainFork errorTo errorMail
    val decision = Scoozie.Actions.decision("sparkOrShell", spark, Scoozie.Actions.switch(shell, "${someVar}"))
    start okTo decision
  }

  override val workflow: Workflow = Scoozie.workflow(name = "ExampleWorkflow",
                                                     path = "/path/to/workflow.xml", //in HDFS
                                                     transitions = transitions,
                                                     configuration = Scoozie.Configuration.emptyConfiguration,
                                                     yarnConfig = yarnConfig)

  override val coordinator: Coordinator = Scoozie.coordinator(name = "exampleCoordinator",
                                                              frequency = "startFreq",
                                                              start = "start",
                                                              end = "end",
                                                              timezone = "timeZome",
                                                              workflow = workflow,
                                                              configuration =
                                                                Scoozie.Configuration.configuration(yarnProperties))
}
```

The artifacts can be generated from this class via the following methods:

```
testJob.saveCoordinator("/some/path/")

```
 or
 
 ```
 testJob.saveWorkflow("/some/path/")
 ```
 
 As mentioned above, this would save both the xml and the required properties to the specified location.  THe properties generated from this example would be:
 
```
alertFailure_body=message body
alertFailure_subject=message subject
alertFailure_to=a@a.com,b@b.com
doAHiveThing_hiveSettingsXML=/path/to/settings.xml
doAHiveThing_scriptLocation=/path/to/someScript.hql
doAHiveThing_scriptName=someScript.hql
doAJavaThing_javaJar=/path/to/jar
doAJavaThing_javaOptions=java options
doAJavaThing_mainClass=org.antipathy.Main
doAJavaThing_prepare_delete=/some/path
doAShellThing_scriptLocation=/path/to/script.sh
doAShellThing_scriptName=script.sh
doASparkThing_mainClass=org.antipathy.Main
doASparkThing_sparkJar=/path/to/jar
doASparkThing_sparkJobName=JobName
doASparkThing_sparkMasterURL=masterURL
doASparkThing_sparkMode=mode
doASparkThing_sparkOptions=spark options
doASparkThing_sparkSettings=/path/to/spark/settings
exampleCoordinator_property0=value1
exampleCoordinator_property1=value2
jobTracker=yarn
nameNode=nameservice1
```

The transitions from this class would be expressed as 

```
start -> sparkOrShell -> doASparkThing -> mainFork -> (doAJavaThing, doAHiveThing) -> mainJoin -> end
```



## TODO

* Add support for specifying Workflows as Hocon via pureconfig.
* Add the ability to join `GeneratedWorkflow ` classes and combine properties.
* Add functionality to deploy artefacts directly from the library.
* Add support for complex workflows in the `WorkflowTestRunner` class.
* Add support for datasets.

[licenseImg]: https://img.shields.io/github/license/simonjpegg/scoozie.svg
[licenseImg2]: https://img.shields.io/badge/license-Apache%202-blue.svg
[licenseLink]: LICENSE

[codacyImg]: https://img.shields.io/codacy/grade/fdf40afa99a342b093836bfa22871c2d.svg?style=flat
[codacyImg2]: https://api.codacy.com/project/badge/grade/fdf40afa99a342b093836bfa22871c2d
[codacyLink]: https://app.codacy.com/project/SimonJPegg/scoozie/dashboard

[210mavenImg]: https://maven-badges.herokuapp.com/maven-central/org.antipathy/scoozie_2.10/badge.svg
[211mavenImg]: https://maven-badges.herokuapp.com/maven-central/org.antipathy/scoozie_2.11/badge.svg
[212mavenImg]: https://maven-badges.herokuapp.com/maven-central/org.antipathy/scoozie_2.12/badge.svg
[mavenLink]: https://search.maven.org/search?q=scoozie

[CodeCovImg]: https://api.codacy.com/project/badge/Coverage/4c627c7c58834629a0d737db4097a1b0
[CodCovLink]: https://www.codacy.com?utm_source=github.com&utm_medium=referral&utm_content=SimonJPegg/scoozie&utm_campaign=Badge_Coverage


