import java.net.ServerSocket
import java.net.Socket
import java.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.Semaphore

const val PORT = 2022
const val MAX_CONNECTIONS = 2

fun main() {
    SocketChatServer.init(PORT)
}

data class Message(val author: String, val content: String)

class SocketChatServer(private val port: Int) {
    private val semaphore = Semaphore(MAX_CONNECTIONS)
    private val serverSocket = ServerSocket(port)
    private val messages = Collections.synchronizedList(mutableListOf<Message>())

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(SocketChatServer::class.java)
        fun init(port: Int) {
            val socketChat = SocketChatServer(port)
            socketChat.handleConnections()
        }
    }

    private fun runWithExceptionSafety(block: () -> Unit) {
        try {
            block()
        } catch(e: IOException) {
            logger.warn("A Client has disconnected: ${e.message}")
        } finally {
            semaphore.release()
        }
    }

    fun handleConnections() {
        logger.info("Listening on port $port")
        while (true) {
            semaphore.acquire()
            val clientSocket = serverSocket.accept()
            logger.info("New client has connected: ${clientSocket.inetAddress}")
            Thread {
                handleClient(clientSocket)
            }.also { it.start() }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        // Holds the socket client username value 
        val clientUsername = Collections.synchronizedList(mutableListOf<String?>(null))

        // Thread to read input from the client
        Thread { 
            runWithExceptionSafety {
                clientSocket.getInputStream().use { input ->
                    while (true) {
                        val userInput = input.bufferedReader().readLine()
                        if (clientUsername[0] == null) {
                            // Set username
                            clientUsername[0] = userInput
                            continue
                        }
                        messages.add(Message(author = clientUsername[0]!!, content = userInput))

                    }
                }
            }

        }.also { it.start() }
        // Thread to send messages to the client
        Thread {
            runWithExceptionSafety {
                clientSocket.getOutputStream().use { output ->
                    var lastMessageIndex = 0
                    output.write("Enter your username: ".toByteArray())
                    while (true) {
                        // Wait until user enters username
                        if (clientUsername[0] == null)
                            continue

                        // If there is a new message
                        if (lastMessageIndex < messages.size) {
                            // Update client with all the missing messages
                            while (lastMessageIndex < messages.size) {
                                if (messages[lastMessageIndex].author != clientUsername[0]) {
                                    output.write(" > (${messages[lastMessageIndex].author}) : ${messages[lastMessageIndex].content}\n".toByteArray())
                                }
                                lastMessageIndex++
                            }
                        }
                    }
                }
            }
        }.also { it.start() }
    }
}
