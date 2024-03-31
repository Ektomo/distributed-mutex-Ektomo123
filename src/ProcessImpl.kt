package mutex

import java.lang.Integer.max
import java.util.*

/**
 * Distributed mutual exclusion implementation.
 * All functions are called from the single main thread.
 *
 * @author Горбунов Иван //
 */
class ProcessImpl(private val env: Environment) : Process {
    private var counter = 0
    private var ackCounter = 0
    private var waitingForAck = mutableSetOf<Int>()
    private val requestsQueue = PriorityQueue<Pair<Int, Int>>(compareBy({ it.first }, { it.second }))

    private fun incrementCounter() {
        counter++
    }

    private fun broadcastRequest() {
        for (i in 1..env.nProcesses) {
            if (i != env.processId) {
                env.send(i) {
                    writeEnum(Type.REQ)
                    writeInt(counter)
                }
            }
        }
    }

    private fun checkAndEnterCriticalSection() {
        if (waitingForAck.isEmpty() && (requestsQueue.isEmpty() || requestsQueue.peek().second == env.processId)) {
            env.locked()
            requestsQueue.poll()
        }
    }

    private fun sendDeferredReplies() {
        while (requestsQueue.isNotEmpty()) {
            val (_, theirId) = requestsQueue.poll()
            env.send(theirId) {
                writeEnum(Type.OK)
            }
        }
    }

    override fun onMessage(srcId: Int, message: Message) {
        message.parse {
            val type = readEnum<Type>()
            when (type) {
                Type.REQ -> {
                    val senderCounter = readInt() // Читаем счетчик процесса
                    requestsQueue.offer(senderCounter to srcId)
                    if (!waitingForAck.contains(env.processId) ||
                        senderCounter < counter ||
                        (senderCounter == counter && srcId < env.processId)) {
                        env.send(srcId) {
                            writeEnum(Type.OK)
                            writeInt(senderCounter)
                        }
                    }
                }
                Type.OK -> {
                    waitingForAck.remove(srcId)
                    checkAndEnterCriticalSection()
                }
            }
        }
    }


    override fun onLockRequest() {
        incrementCounter()
        waitingForAck.addAll((1..env.nProcesses).filter { it != env.processId })
        broadcastRequest()
        checkAndEnterCriticalSection()
    }

    override fun onUnlockRequest() {
        env.unlocked()
        sendDeferredReplies()
    }
}
enum class Type { REQ, OK }
