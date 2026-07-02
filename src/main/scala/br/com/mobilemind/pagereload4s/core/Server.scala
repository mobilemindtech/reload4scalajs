package br.com.mobilemind.pagereload4s.core

import io.undertow.Undertow
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.websockets.WebSocketConnectionCallback
import io.undertow.websockets.core.*
import io.undertow.websockets.spi.WebSocketHttpExchange
import sbt.util.Logger
import java.io.{BufferedReader, File, FileReader, InputStreamReader}
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*
import scala.util.{Success, Using}


case class ServerConfigs(port: Int, publicFolder: Option[File], reloadUrl: Option[String])
case class Asset(data: String, contentType: String, status: Int = 200)

// --- GERENCIAMENTO DE SESSÕES WEBSOCKET NATIVO DO UNDERTOW ---
object WsSession extends WebSocketConnectionCallback {

	private val sessions = ConcurrentHashMap.newKeySet[WebSocketChannel]().asScala

	def count: Int = sessions.size

	override def onConnect(exchange: WebSocketHttpExchange, channel: WebSocketChannel): Unit = {
		sessions.add(channel)
		WebSockets.sendText(createEvent("alive"), channel, null)

		// Configura o Listener de mensagens igual ao comportamento do seu WsActor
		channel.getReceiveSetter.set(new AbstractReceiveListener {
			override def onFullTextMessage(wsChannel: WebSocketChannel, message: BufferedTextMessage): Unit = {
				message.getData match {
					case "" =>
						WebSockets.sendClose(1000, "Goodbye", wsChannel, null)
						sessions.remove(wsChannel)
					case data =>
						WebSockets.sendText(s"userName $data", wsChannel, null)
				}
			}

			override def onClose(wsChannel: WebSocketChannel, channel: StreamSourceFrameChannel): Unit = {
				sessions.remove(wsChannel)
				super.onClose(wsChannel, channel)
			}
		})
		channel.resumeReceives()
	}

	def notify(logger: Logger): Unit = {
		// Verificação ATIVA: Filtra e remove canais que fecharam silenciosamente
		
		sessions.filterInPlace(_.isOpen)

		sessions.foreach { channel =>
			WebSockets.sendText(createEvent("reload"), channel, null)
			WebSockets.sendClose(1000, "reload", channel, null)
		}
		sessions.clear()
	}

	private def createEvent(s: String): String = s"""{"event": "$s"}"""

	def stop(): Unit = { sessions.foreach(_.close()); sessions.clear() }
}

// --- ROTERADOR HTTP PRINCIPAL (HANDLER) ---
class AppController(publicFolder: => Option[File], reloadUrl: Option[String]) extends HttpHandler {

	override def handleRequest(exchange: HttpServerExchange): Unit = {
		// Se for o Handshake do WebSocket na rota /ws, deixa o Undertow Handshake Handler assumir
		(exchange.getRequestMethod.toString, exchange.getRequestPath) match
			case (_, "/ws") => ()
			case ("GET", "/healthcheck") => sendResponse(exchange, "live reload is alive", 200, "text/plain")
			case ("GET", "/") => index(exchange)
			//case ("GET", "/dist") => sendResponse(exchange, publicFolder.map(_.getAbsolutePath).getOrElse("empty"), 200, "text/plain")
			case ("GET", "/demo") => demo(exchange)
			case ("GET", p) if p.startsWith("/js/livereload.js") => livereloadJS(exchange)
			case ("GET", p) if p.startsWith("/assets") => assets(exchange, p)
			case ("GET", _) => sendResponse(exchange, "Not Found", 404, "text/plain")
			case _ => sendResponse(exchange, "Method Not Allowed", 405, "text/plain")
	}

	private def index(exchange: HttpServerExchange): Unit = {
		val resp = publicFolder match {
			case Some(file) => readIndexHtml(exchange, file).getOrElse("index.html not found")
			case _          => "live reload is alive"
		}
		sendResponse(exchange, resp, 200, "text/html")
	}

	private def assets(exchange: HttpServerExchange, fullPath: String): Unit = {
		publicFolder match {
			case Some(f) =>
				val assetParts = fullPath.stripPrefix("/assets")
				val Asset(data, contentType, status) = readAsset(f, assetParts)
				sendResponse(exchange, data, status, contentType)
			case _ => sendResponse(exchange, "not found", 404, "text/plain")
		}
	}

	private def demo(exchange: HttpServerExchange): Unit = {
		val stream = getClass.getResourceAsStream("/public/html/index.html")
		if (stream != null) {
			Using(new BufferedReader(new InputStreamReader(stream))) {
				_.lines().toList.asScala.mkString("\n") } match {
					case Success(html) => sendResponse(exchange, html, 200, "text/html")
					case _             => sendResponse(exchange, "Internal Error", 500, "text/plain")
				}
		} else sendResponse(exchange, "Demo Not Found", 404, "text/plain")
	}

	private def livereloadJS(exchange: HttpServerExchange): Unit = {
		val stream = getClass.getResourceAsStream("/public/js/livereload.js")
		if (stream != null) {
			Using(new BufferedReader(new InputStreamReader(stream))) { reader =>
				val host = exchange.getHostAndPort
				val port = host.split(":").lastOption.getOrElse("10101")
				reader.lines().toList.asScala
					.map(_.replace("__PORT__", port))
					.map(_.replace("__RELOAD_URL__", reloadUrl.getOrElse(s"http://$host")))
					.mkString("\n")
			} match {
				case Success(js) => sendResponse(exchange, js, 200, "text/javascript")
				case _           => sendResponse(exchange, "Internal Error", 500, "text/plain")
			}
		} else sendResponse(exchange, "not found", 404, "text/plain")
	}

	private def readIndexHtml(exchange: HttpServerExchange, path: File): Option[String] = {
		val indexFile = new File(path, "index.html")
		if (indexFile.exists()) {
			Using(new BufferedReader(new FileReader(indexFile))) { reader =>
				val html = reader.lines().toList.asScala.mkString("\n")
				if (html.contains("/livereload.js")) html
				else {
					val host = exchange.getHostAndPort
					val jsUrl = reloadUrl.getOrElse(s"http://$host/js/livereload.js")
					html.replace("</body>", s"""<script src="$jsUrl"></script>\n</body>""")
				}
			}.toOption
		} else None
	}

	private def readAsset(assetPath: File, assetParts: String): Asset = {
		val file = new File(assetPath, s"/assets/$assetParts")
		if (file.exists()) {
			val ext = file.getName.split("\\.").toList.lastOption.getOrElse("")
			val contentType = MimeTypes.values.getOrElse(s".$ext", MimeTypes.defaultMimeType)
			Using(new BufferedReader(new FileReader(file))) { reader =>
				Asset(reader.lines().toList.asScala.mkString("\n"), contentType)
			}.getOrElse(Asset("Error reading asset", "text/plain", 500))
		} else Asset(s"file not found", "text/plain", 404)
	}

	private def sendResponse(exchange: HttpServerExchange, body: String, status: Int, contentType: String): Unit = {
		exchange.setStatusCode(status)
		exchange.getResponseHeaders.put(io.undertow.util.Headers.CONTENT_TYPE, contentType)
		exchange.getResponseSender.send(body)
	}
}

// --- INSTANCIAÇÃO DO SERVIDOR ---
object Server {
	import io.undertow.Handlers.{path, websocket}

	private var logger: Option[Logger] = None
	private var undertowServer: Option[Undertow] = None
	private var configs: Option[ServerConfigs] = None

	def setLogger(customLogger: Logger): Unit = logger = Some(customLogger)

	def start(customLogger: Logger, conf: ServerConfigs): Unit = {
		logger = Some(customLogger)
		configs = Some(conf)
		val port = conf.port

		val httpHandler = new AppController(
			configs.flatMap(_.publicFolder),
			configs.flatMap(_.reloadUrl)
		)

		// Monta a árvore de rotas combinando HTTP normal com a rota do WebSocket nativo do Undertow
		val rootHandler = path()
			.addPrefixPath("/", httpHandler)
			.addPrefixPath("/ws", websocket(WsSession))

		val server = Undertow.builder()
			.addHttpListener(port, "0.0.0.0")
			.setIoThreads(Runtime.getRuntime.availableProcessors() * 2)
			.setHandler(rootHandler)
			.build()

		server.start()
		undertowServer = Some(server)
		logger.foreach(_.info(s"[Reload for ScalaJS] Undertow Server running on http://localhost:$port"))
	}

	def stop(): Unit = {
		logger.foreach(_.info(s"stop [${WsSession.count}] WS sessions"))
		WsSession.stop()
		undertowServer.foreach(_.stop())
	}

	def notify(customLogger: Logger): Unit = WsSession.notify(customLogger)
}