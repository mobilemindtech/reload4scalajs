package br.com.mobilemind.pagereload4s.plugin

import br.com.mobilemind.pagereload4s.core.{FileWatcher, Server, ServerConfigs, Watcher}
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import sbt.*
import sbt.Keys.*

import java.io.File
import scala.language.postfixOps


object PageReload4sPlugin extends AutoPlugin {
  override def requires = ScalaJSPlugin
  override def trigger = allRequirements

  object autoImport {

    case class ReloadConfig(
                             /**
                              * Path to watch to reload on change
                              */
                             pathWatchToReload: File | Null = null,

                            /**
                             * Watch target folder to copy artifacts on changes
                             */
                            watchDefaultTargetToCopy: Boolean = true,

                            /**
                             * Destination path to copy artifacts
                             */
                            copyJsToPath: File | Null = null,

                            /**
                             * Copy js to a specific file, example /path/js/main.js
                             */
                            copyJsToFile: File | Null = null,

                            /**
                             * Public folder to serve
                             */
                            publicFolder: File | Null = null,

                             /**
                              * Debug mode
                              */
                            debug: Boolean = false,

                            /**
                             * Delay to send reload notification to websocket client
                             */
                            notificationDelay: Long = 1000,

                             /**
                              * Server port
                              */
                            serverPort: Int = 10101,

                             /**
                              * File extensions to watch
                              */
                            extensions: Seq[String] = Seq("js", "map", "css", "jpg", "jpeg", "png", "ico", "html"),

                            /**
                             * URL to reload on change, default is http://localhost:10101
                             */
                            reloadURL: String | Null = null)

    val pagereload = SettingKey[ReloadConfig]("pagereload", "ReloadConfig settings")

    // tasks
    val pagereloadServe = taskKey[Unit]("Start http server")
    val pagereloadWatch = taskKey[Unit]("Start file watcher")
    val pagereloadStart = taskKey[Unit]("Start the reload plugin")
    val pagereloadStop = taskKey[Unit]("Stop the reload plugin")
    val pagereloadPerformCopy = taskKey[Unit]("Perform js copy")

  }

  import autoImport.*

  override lazy val projectSettings: Seq[Setting[?]] = Seq(
    pagereload := ReloadConfig(),

    fastOptJS / pagereloadPerformCopy := pagereloadPerformCopy.triggeredBy(Compile / fastOptJS).value,
    fullOptJS / pagereloadPerformCopy := pagereloadPerformCopy.triggeredBy(Compile / fullOptJS).value,

    pagereloadPerformCopy := Def.uncached {
      pagereload.value match
        case config @ ReloadConfig(copyJsToFile = copyTo: File) =>
          val logger = streams.value.log
          val src = (Compile / scalaJSLinkedFile).value.data
          val isJsFileName = copyTo.getCanonicalPath.endsWith(".js")
          val fileName = if isJsFileName then copyTo.name else src.name
          val destPath = if isJsFileName then copyTo.getParentFile else copyTo

          logger.info(s"Copying artifacts [js,map] from ${src.getParent} to [${destPath.getCanonicalPath}]")

          IO.copy(
            Seq(
              (src, destPath / fileName),
              (file(src.getCanonicalPath + ".map"), destPath / (fileName + ".map"))
            ),
            CopyOptions(
              overwrite = true,
              preserveLastModified = true,
              preserveExecutable = true
            )
          )

          Watcher.notify(config.notificationDelay, logger)
    },

    pagereloadServe := Def.uncached {
      val config = pagereload.value
      val logger = streams.value
      Server.start(
        logger.log,
        ServerConfigs(
          config.serverPort,
          Option(config.publicFolder),
          Option(config.reloadURL)
        ))
    },

    pagereloadWatch := Def.uncached {
      pagereload.value match
        case config@ReloadConfig(pathWatchToReload = watchPath: File) =>

          val logger = streams.value.log

          FileWatcher.setLogger(logger)

          val pathWatchToCopy =
            if config.watchDefaultTargetToCopy
            then
              val targetName = s"${name.value}-fastopt"
              val target = new File((Compile / crossTarget).value, targetName)
              Some(target)
            else None

          Watcher.start(
            extensions = config.extensions.toList,
            debug = config.debug,
            logger = logger,
            pathWatchToReload = Option(watchPath),
            pathWatchToCopy = pathWatchToCopy,
            pathToCopy = Option(config.copyJsToPath),
            notifyOnChange = true,
            notificationDelay = config.notificationDelay)

    },

    /*
    Compile / compile := Def.uncached {
      val compileValue = (Compile / compile).value
      val streamValue = streams.value
      val logger = streamValue.log)
     FileWatcher.setLogger(logger)
      Server.setLogger(logger)
      val watchingDist = 
        livereloadPublic.value.isDefined && livereloadWatchPublic.value.getOrElse(true)
      if(!watchingDist) Server.notify(logger)
      compileValue
    },*/
    (Global / onUnload) := {
      val previousUnload = (Global / onUnload).value
      previousUnload.compose { state =>
        val extracted = Project.extract(state)
        val (nextState, _) = extracted.runTask(pagereloadStop, state)
        nextState
      }
    },

    pagereloadStart := pagereloadServe.dependsOn(Def.task(pagereloadWatch.value)).value,

    pagereloadStop := Def.uncached {
      val logger = streams.value.log
      FileWatcher.setLogger(logger)
      Server.setLogger(logger)
      logger.info("Stop server")
      FileWatcher.stop()
      logger.info("Stop file watch")
      Server.stop()
    }
  )
}