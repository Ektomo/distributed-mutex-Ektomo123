package mutex

import mutex.ProcessRickartAgrawalaMutex.MsgType
import java.util.*
import kotlin.math.max


/**
 * Distributed mutual exclusion implementation.
 * All functions are called from the single main thread.
 *
 * @author Горбунов Иван //
 */
class ProcessImpl(private val env: Environment) : Process {
    private val forks = mutableMapOf<Int, Fork>()
    private val queue = PriorityQueue<Request>(compareBy({ it.time }, { it.srcId }))
    private var requesting = false
    private var inCriticalSection = false
    private var logicalClock = 0

    init {
        val n = env.nProcesses
        for (i in 1..n) {
            if (i < env.processId) {
                forks[i] = Fork(env.processId, false)
            } else if (i > env.processId) {
                forks[i] = Fork(i, true)
            }
        }
    }

    override fun onMessage(srcId: Int, message: Message) {
        synchronized(this) {
            message.parse {
                val time = readInt()
                val type = readEnum<Type>()
                logicalClock = maxOf(logicalClock, time) + 1

                when (type) {
                    Type.REQ -> handleRequest(srcId, time)
                    Type.OK -> handleOk(srcId)
                }
            }
        }
    }

    override fun onLockRequest() {
        synchronized(this) {
            if (requesting || inCriticalSection) {
                return
            }
            requesting = true
            logicalClock++
            for (i in 1..env.nProcesses) {
                if (i != env.processId && (forks[i]?.holder != env.processId || forks[i]?.dirty == true)) {
                    env.send(i) {
                        writeInt(logicalClock)
                        writeEnum(Type.REQ)
                    }
                }
            }
            checkCriticalSection()
        }
    }

    override fun onUnlockRequest() {
        synchronized(this) {
            if (!inCriticalSection) {
                return
            }
            env.unlocked()
            inCriticalSection = false
            requesting = false
            processQueue()
        }
    }

    private fun handleRequest(srcId: Int, time: Int) {
        synchronized(this) {
            queue.add(Request(time, srcId))
            processQueue()
        }
    }

    private fun handleOk(srcId: Int) {
        synchronized(this) {
            forks[srcId]?.apply {
                holder = env.processId
                dirty = false
            }
            checkCriticalSection()
        }
    }

    private fun checkCriticalSection() {
        if (requesting && forks.all { it.value.holder == env.processId && !it.value.dirty }) {
            inCriticalSection = true
            env.locked()
        }
    }

    private fun processQueue() {
        while (queue.isNotEmpty()) {
            val req = queue.peek()
            val fork = forks[req.srcId]
            if (fork != null && fork.holder == env.processId && !requesting) {
                fork.dirty = true
                fork.holder = req.srcId
                queue.poll()
                env.send(req.srcId) {
                    writeInt(logicalClock)
                    writeEnum(Type.OK)
                }
            } else {
                break
            }
        }
    }
}


enum class Type { REQ, OK }

data class Fork(var holder: Int, var dirty: Boolean)

data class Request(val time: Int, val srcId: Int)
