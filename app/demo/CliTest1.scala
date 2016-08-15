package demo

import org.apache.commons.cli.{Option => CliOption, MissingOptionException, HelpFormatter, DefaultParser, Options}
import scala.collection.JavaConversions._
/**
  * Created by cookeem on 16/7/20.
  */
object CliTest1 extends App {

  val options = new Options()
  options.addOption("a", "all", false, "do not hide entries starting with .")
  options.addOption("A", "almost-all", false, "do not list implied . and ..")
  options.addOption("b", "escape", false, "print octal escapes for nongraphic characters")
  options.addOption(CliOption.builder("bs").longOpt("block-size")
    .desc("use SIZE-byte blocks")
    .hasArg()
    .required()
    .argName("SIZE")
    .build())
  options.addOption(CliOption.builder("as").longOpt("args")
    .desc("use multiple args")
    .hasArgs
    .argName("ARG")
    .build())
  try {
    val parser = new DefaultParser()
    val cmd = parser.parse(options, args)
    println("cmd.getOptions")
    cmd.getOptions.foreach{opt =>
      println(opt.getOpt,opt.getLongOpt,opt.getDescription,opt.getArgName,opt.getValue)
      if (opt.getValues != null) {
        println(opt.getValues.mkString(","))
      }
    }
    println(s"${cmd.hasOption("a")}")
    println(s"${cmd.getOptionValue("bs")}")
  } catch {
    case e: MissingOptionException =>
      val formatter = new HelpFormatter()
      formatter.printHelp( "This is a command line usages test:", options)
    case e: Throwable =>
      println(s"args error: ${e.getMessage}, ${e.getCause}")
  }
}
