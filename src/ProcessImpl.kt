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
    

    override fun onMessage(srcId: Int, message: Message) {
        
    }

    override fun onLockRequest() {
        
    }

    override fun onUnlockRequest() {
        
    }
}


