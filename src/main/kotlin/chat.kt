import java.net.ServerSocket
import java.net.Socket
import java.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

const val PORT = 2022
const val MAX_CONNECTIONS = 2

fun main() {
    SocketChatServer.init(PORT)
}

class AtomicString(var value: String = "") {
    val lock = ReentrantLock()
    fun get(): String =
        lock.withLock { return value }

    fun set(newValue: String) =
        lock.withLock { value = newValue }

    fun append(toAppend: String) =
        lock.withLock { value += toAppend }
}

/*
fun testAtomicString() {
    val sharedString = AtomicString()
    var i = 0
    while (i++ < 100) {
        Thread {
            sharedString.append("A")
        }.also { it.start() }
    }
    println("String length: ${sharedString.get().length}")
}
*/

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
        val clientUsername = AtomicString()

        val isConnected = AtomicBoolean(true)

        // Thread to read input from the client
        Thread { 
            try {
                clientSocket.getInputStream().use { input ->
                    while (true) {
                        val userInput = input.bufferedReader().readLine()
                        val userName = clientUsername.get()
                        if (userName == "") {
                            // Set username
                            clientUsername.set(userInput)
                            continue
                        } else {
                            messages.add(Message(author = userName, content = userInput))
                        }
                    }
                }
            } catch (e: Exception) {
                isConnected.set(false)
            }
        }.also { it.start() }
        // Thread to send messages to the client
        Thread {
            try {
                clientSocket.getOutputStream().use { output ->
                    var lastMessageIndex = 0
                    output.write("Enter your username: ".toByteArray())
                    while (true) {
                        // Wait until user enters username
                        if (clientUsername.get() == "")
                            continue

                        // If there is a new message
                        if (lastMessageIndex < messages.size) {
                            // Update client with all the missing messages
                            while (lastMessageIndex < messages.size) {
                                if (messages[lastMessageIndex].author != clientUsername.get()) {
                                    output.write(" > (${messages[lastMessageIndex].author}) : ${messages[lastMessageIndex].content}\n".toByteArray())
                                }
                                lastMessageIndex++
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                isConnected.set(false)
            }
        }.also { it.start() }

        while (isConnected.get());

        // Disconnected
        logger.warn("Client has disconnected: ${clientSocket.inetAddress}")
        semaphore.release()
    }
}
