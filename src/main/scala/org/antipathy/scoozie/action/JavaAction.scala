package org.antipathy.scoozie.action

import com.typesafe.config.Config
import org.antipathy.scoozie.action.prepare.Prepare
import org.antipathy.scoozie.builder.{ConfigurationBuilder, HoconConstants, MonadBuilder, PrepareBuilder}
import org.antipathy.scoozie.configuration._
import org.antipathy.scoozie.exception.ConfigurationMissingException

import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.xml.Elem

/**
  * Oozie Java action definition
  * @param name the name of the action
  * @param mainClass the main class of the java job
  * @param javaJar the location of the java jar
  * @param javaOptions options for the java job
  * @param commandLineArgs command line arguments for the java job
  * @param files files to include with the application
  * @param captureOutput capture output from this action
  * @param jobXmlOption optional job.xml path
  * @param configuration additional config for this action
  * @param yarnConfig Yarn configuration for this action
  * @param prepareOption an optional prepare stage for the action
  */
final class JavaAction(override val name: String,
                       mainClass: String,
                       javaJar: String,
                       javaOptions: String,
                       commandLineArgs: Seq[String],
                       files: Seq[String],
                       captureOutput: Boolean,
                       override val jobXmlOption: Option[String],
                       override val configuration: Configuration,
                       yarnConfig: YarnConfig,
                       override val prepareOption: Option[Prepare])
    extends Action
    with HasPrepare
    with HasConfig
    with HasJobXml {

  private val mainClassProperty = formatProperty(s"${name}_mainClass")
  private val javaJarProperty = formatProperty(s"${name}_javaJar")
  private val javaOptionsProperty = formatProperty(s"${name}_javaOptions")
  private val commandLineArgsProperties =
    buildSequenceProperties(name, "commandLineArg", commandLineArgs)
  private val filesProperties = buildSequenceProperties(name, "files", files)

  /**
    * Get the Oozie properties for this object
    */
  override def properties: Map[String, String] =
    Map(mainClassProperty -> mainClass, javaJarProperty -> javaJar, javaOptionsProperty -> javaOptions) ++
    commandLineArgsProperties ++
    prepareProperties ++
    filesProperties ++
    configurationProperties.properties ++ jobXmlProperty

  /**
    * The XML namespace for an action element
    */
  override val xmlns: Option[String] = None

  /**
    * The XML for this node
    */
  override def toXML: Elem =
    <java>
        {yarnConfig.jobTrackerXML}
        {yarnConfig.nameNodeXML}
        {prepareXML}
        {jobXml}
        {configXML}
        <main-class>{mainClassProperty}</main-class>
        <java-opts>{javaOptionsProperty}</java-opts>
        {commandLineArgsProperties.keys.map(Arg(_).toXML)}
        {filesProperties.keys.map(File(_).toXML)}
        <file>{javaJarProperty}</file>
        { if (captureOutput) {
            <capture-output />
          }
        }
      </java>
}

/**
  * Companion object
  */
object JavaAction {

  /**
    * Create a new instance of this action
    */
  def apply(name: String,
            mainClass: String,
            javaJar: String,
            javaOptions: String,
            commandLineArgs: Seq[String],
            files: Seq[String],
            captureOutput: Boolean,
            jobXmlOption: Option[String],
            configuration: Configuration,
            yarnConfig: YarnConfig,
            prepareOption: Option[Prepare])(implicit credentialsOption: Option[Credentials]): Node =
    Node(
      new JavaAction(name,
                     mainClass,
                     javaJar,
                     javaOptions,
                     commandLineArgs,
                     files,
                     captureOutput,
                     jobXmlOption,
                     configuration,
                     yarnConfig,
                     prepareOption)
    )

  /**
    * Create a new instance of this action from a configuration
    */
  def apply(config: Config, yarnConfig: YarnConfig)(implicit credentials: Option[Credentials]): Node =
    MonadBuilder.tryOperation[Node] { () =>
      JavaAction(name = config.getString(HoconConstants.name),
                 mainClass = config.getString(HoconConstants.mainClass),
                 javaJar = config.getString(HoconConstants.javaJar),
                 javaOptions = config.getString(HoconConstants.javaOptions),
                 commandLineArgs = Seq(config.getStringList(HoconConstants.commandLineArguments).asScala: _*),
                 files = Seq(config.getStringList(HoconConstants.files).asScala: _*),
                 captureOutput = ConfigurationBuilder.optionalBoolean(config, HoconConstants.captureOutput),
                 jobXmlOption = ConfigurationBuilder.optionalString(config, HoconConstants.jobXml),
                 configuration = ConfigurationBuilder.buildConfiguration(config),
                 yarnConfig,
                 prepareOption = PrepareBuilder.build(config))
    } { s: String =>
      new ConfigurationMissingException(s"$s in ${config.getString(HoconConstants.name)}")
    }
}
