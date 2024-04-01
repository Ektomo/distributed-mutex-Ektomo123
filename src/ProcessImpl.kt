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
    private var logicalClock = 0
    private val forksState = mutableMapOf<Int, Boolean>() // true если вилка у нас и она "грязная"
    private var needCS = false // Флаг необходимости входа в критическую секцию
    private var inCriticalSection = false

    init {
        // Инициализируем состояние вилок на основе ID процессов
        for (i in 1..env.nProcesses) {
            if (i < env.processId) {
                forksState[i] = true // У нас есть "грязная" вилка от процесса с меньшим ID
            } else if (i > env.processId) {
                forksState[i] = false // У процесса с большим ID есть "грязная" вилка
            }
        }
    }

    override fun onMessage(srcId: Int, message: Message) {
        message.parse {
            val msgType = readEnum<MessageType>()
            val msgTime = readInt()
            logicalClock = max(logicalClock, msgTime) + 1
            when (msgType) {
                MessageType.REQ -> {
                    if (forksState[srcId] == true && !needCS) {
                        sendFork(srcId)
                    }
                }

                MessageType.OK -> {
                    forksState[srcId] = true // Помечаем вилку как полученную
                    tryEnterCriticalSection()
                }
            }
        }
    }

    override fun onLockRequest() {
        needCS = true
        requestForks()
        tryEnterCriticalSection()
    }

    override fun onUnlockRequest() {
        if (inCriticalSection) {
            leaveCriticalSection()
        }
    }

    private fun requestForks() {
        for (i in forksState.keys) {
            if (!forksState[i]!!) {
                env.send(i, Message{
                    writeEnum(MessageType.REQ)
                    writeInt(logicalClock)
                })
            }
        }
    }


    private fun sendFork(receiverId: Int) {
        forksState[receiverId] = false // Убираем вилку у себя
        env.send(receiverId, Message{
            writeEnum(MessageType.OK)
            writeInt(logicalClock)
        })
    }

    private fun tryEnterCriticalSection() {
        if (needCS && forksState.all { it.value }) {
            // Все вилки у нас, входим в критическую секцию
            env.locked()
            inCriticalSection = true
            needCS = false
        }
    }

    private fun leaveCriticalSection() {
        inCriticalSection = false
        // После выхода из критической секции вилки остаются "грязными" и у нас
        env.unlocked()
    }
}
enum class MessageType {
    REQ, OK
}

enum class ForkState {
    CLEAN, DIRTY, NONE
}
