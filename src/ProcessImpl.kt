package mutex

import java.lang.Integer.max
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Distributed mutual exclusion implementation.
 * All functions are called from the single main thread.
 *
 * @author Горбунов Иван //
 */
class ProcessImpl(private val env: Environment) : Process {
    private val clock = AtomicInteger(0)
    private var state = State.THINKING
    // Изначальное состояние вилок зависит от идентификатора процесса
    private var hasLeftFork = env.processId % 2 == 0
    private var hasRightFork = env.processId % 2 == 1
    private var requestedLeftFork = false
    private var requestedRightFork = false

    override fun onMessage(srcId: Int, message: Message) {
        message.parse {
            val msgTime = readInt()
            val type = readEnum<MsgType>()
            clock.updateAndGet { current -> max(current, msgTime) + 1 }
            when (type) {
                MsgType.REQ -> {
                    // Если процесс находится в критической секции или ожидает входа и имеет вилку, он не отдаёт её сразу.
                    if ((state == State.HUNGRY || state == State.EATING) && (hasLeftFork && srcId == leftNeighbor() || hasRightFork && srcId == rightNeighbor())) {
                        return@parse // Процесс ожидает возможности поесть и не отдаёт вилку.
                    }
                    sendFork(srcId)
                }
                MsgType.OK -> {
                    if (srcId == leftNeighbor()) {
                        hasLeftFork = true
                        requestedLeftFork = false
                    }
                    if (srcId == rightNeighbor()) {
                        hasRightFork = true
                        requestedRightFork = false
                    }
                    tryToEat()
                }
            }
        }
    }

    override fun onLockRequest() {
        state = State.HUNGRY
        tryToEat()
        requestForks()
    }

    override fun onUnlockRequest() {
        state = State.THINKING
        // Помечаем вилки как "грязные", но не отправляем их автоматически.
        // Вилки будут отправлены только при получении запроса от соседа.
        if (!hasLeftFork) requestedLeftFork = false
        if (!hasRightFork) requestedRightFork = false
        env.unlocked()
        tryToSendForks()
    }

    private fun leftNeighbor() = if (env.processId == 1) env.nProcesses else env.processId - 1
    private fun rightNeighbor() = if (env.processId == env.nProcesses) 1 else env.processId + 1

    private fun tryToEat() {
        if (state == State.HUNGRY && hasLeftFork && hasRightFork) {
            state = State.EATING
            env.locked()
        }
    }

    private fun requestForks() {
        if (!hasLeftFork && !requestedLeftFork) {
            requestedLeftFork = true
            sendReq(leftNeighbor())
        }
        if (!hasRightFork && !requestedRightFork) {
            requestedRightFork = true
            sendReq(rightNeighbor())
        }
    }

    private fun tryToSendForks() {
        if (hasLeftFork && !requestedLeftFork) {
            sendFork(leftNeighbor())
        }
        if (hasRightFork && !requestedRightFork) {
            sendFork(rightNeighbor())
        }
    }

    private fun sendReq(destId: Int) {
        env.send(destId) {
            writeInt(clock.incrementAndGet())
            writeEnum(MsgType.REQ)
        }
    }

    private fun sendFork(destId: Int) {
        if (destId == leftNeighbor()) hasLeftFork = false
        if (destId == rightNeighbor()) hasRightFork = false
        env.send(destId) {
            writeInt(clock.incrementAndGet())
            writeEnum(MsgType.OK)
        }
    }

    enum class State { THINKING, HUNGRY, EATING }
    enum class MsgType { REQ, OK }
}


