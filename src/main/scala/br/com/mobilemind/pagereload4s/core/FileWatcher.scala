package br.com.mobilemind.pagereload4s.core


import java.io.{File, FileWriter, IOException}
import java.nio.file.StandardWatchEventKinds.{ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY}
import java.nio.file.*
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.{Timer, TimerTask}
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}
import scala.collection.mutable
import sbt.util.Logger

import java.nio.file.attribute.BasicFileAttributes

object Watcher {

	private var notificationTimer: Option[Timer] = None

	private def startTimer(delay: Long)(f: => Unit): Unit = {
		if notificationTimer.isEmpty
		then
			val t = new Timer
			t.schedule(new TimerTask {
				override def run(): Unit = {
					f
					notificationTimer = None
				}
			}, delay)
			notificationTimer = Some(t)
	}

	def notify(delay: Long, logger: Logger): Unit = {
		startTimer(delay) {
			WsSession.notify(logger)
		}
	}

	def start(extensions: List[String],
	          debug: Boolean,
	          logger: Logger,
	          pathWatchToReload: Option[File],
	          pathWatchToCopy: Option[File],
	          pathToCopy: Option[File],
	          notifyOnChange: Boolean,
	          notificationDelay: Long): Unit = {

		val reloadWatcher = FileWatcher.create(extensions, debug)
		val copyWatcher = FileWatcher.create(extensions, debug)

		pathWatchToReload match {
			case Some(path) =>
				logger.info(s":: starting watcher in path `${path.getAbsolutePath}` to reload")
				reloadWatcher.start(path) { _ =>
						if notifyOnChange
						then notify(notificationDelay, logger)
				} match {
					case Failure(ex) => logger.error(ex.getMessage)
					case _ => ()
				}
			case _ => ()
		}

		(pathWatchToCopy, pathToCopy) match {
			case (Some(watchPath), Some(copyTo)) =>
				logger.info(s":: starting watcher in path `${watchPath.getAbsolutePath}` to copy to $copyTo")
				copyWatcher.start(watchPath) { changedFile =>
						val to = Paths.get(copyTo.getAbsolutePath, changedFile.getFileName.toString)
						Files.copy(changedFile, to, StandardCopyOption.REPLACE_EXISTING)
				} match {
					case Failure(ex) => logger.error(ex.getMessage)
					case _ => ()
				}
			case _ => ()
		}

	}
}

object FileWatcher {
	private var logger: Option[Logger]  = None
	private val watchers: mutable.ListBuffer[FileWatcher] = new mutable.ListBuffer

	private def log(text: String): Unit = logger.foreach(_.info(text))

	def setLogger(l: Logger): Unit = logger = Some(l)

	def stop(): Unit = {
		logger.foreach(_.info(s"stop [${watchers.length}] watchers"))
		watchers.foreach(_.stop())
		watchers.clear()
	}

	def create(extensions: List[String] = List(), debug: Boolean = false): FileWatcher = {
		new FileWatcher(extensions, debug)
	}
}

class FileWatcher(val extensions: List[String], debug: Boolean = false){

	import FileWatcher.*

	private var timer: Option[Timer] = None
	private var running: Boolean = true
	private val watchKeys: mutable.Map[WatchKey, Path] = mutable.Map.empty

	watchers.append(this)

	def stop(): Unit = {
		running = false
		timer.foreach(_.cancel())
	}

	def start(pathToWatch: File)(onChange: Path => Unit): Try[Unit] = {
			Try(tryCreateIfNeed(pathToWatch)).map: _ =>
				startTimer:
					watch(pathToWatch)(onChange)
	}

	private def startTimer(f: => Unit): Unit = {
		val t = new Timer
		t.schedule(new TimerTask {
			override def run(): Unit = f
		}, 0)
		timer = Some(t)
	}

	private def watch(fromPath: File)(onChange: Path => Unit): Unit = {
		val watcher = FileSystems.getDefault.newWatchService

		Files.walkFileTree(Path.of(fromPath.getAbsolutePath), new SimpleFileVisitor[Path] {
			override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
				val key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
				if debug then logger.foreach(_.info(s"watch dir: $dir"))
				watchKeys += (key -> dir)
				FileVisitResult.CONTINUE
			}
		})

		try
			watchLoop(watcher, onChange)
		finally
			watcher.close()

		if (debug)
			logWriter(fromPath, s"file watch done to listen: ${fromPath.getAbsolutePath}")

	}

	private def watchLoop(watcher: WatchService, onChange: Path => Unit): Unit = {
		while running do

			val key = watcher.take()

			if running
			then
				val dir = watchKeys(key)
				val events = key.pollEvents().toArray
				for event <- events do
					val ev = event.asInstanceOf[WatchEvent[Path]]
					val fileName = ev.context().toFile.getName
					val ok = extensions.exists(ext => fileName.endsWith(s".$ext"))

					if debug
					then
						logWriter(dir.toFile, s"file changed: ${fileName}, extensions ${extensions.mkString(",")}, check: $ok")

					if ok
					then
						onChange(Path.of(dir.toString, fileName))

				if !key.reset()
				then
					watchKeys.remove(key)
					running = watchKeys.nonEmpty
					if debug
					then
						logger.foreach(_.info(s"watch running: $running"))

	}

	private def tryCreateIfNeed(target: File) =
		if (!target.exists() && !target.mkdirs())
			Failure(new IOException(s"can't create path ${target.getAbsolutePath}"))
		else Success(true)

	private def logWriter(path: File, text: String): Unit = {
		val target = new File("./target")
		val logFile = new File(target, s"livereload.log")
		val writer = new FileWriter(logFile, true)
		writer.write(s"${LocalDateTime.now().toString}: $text\n")
		writer.flush()
		writer.close()
	}
}
