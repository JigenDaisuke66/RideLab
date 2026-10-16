package com.isaac.ridelab.ble.control

/** Small main-thread ATT queue. A failed dispatch completes immediately; a timeout closes
 * the queue so a late callback cannot release a newer operation with the same characteristic.
 */
class SerialGattOperations(
    private val armTimeout: () -> Unit,
    private val cancelTimeout: () -> Unit,
    private val onTimeout: () -> Unit,
) {
    private data class Operation(val key: Any, val start: () -> Boolean, val complete: (Int, ByteArray?) -> Unit)
    private val waiting = ArrayDeque<Operation>()
    private var current: Operation? = null
    private var closed = false
    val idle get() = current == null && waiting.isEmpty() && !closed
    fun enqueue(key: Any, start: () -> Boolean, complete: (Int, ByteArray?) -> Unit) {
        if (closed) return
        waiting.addLast(Operation(key, start, complete)); drain()
    }
    private fun drain() {
        if (closed || current != null || waiting.isEmpty()) return
        val op = waiting.removeFirst(); current = op; armTimeout()
        if (!op.start()) complete(op.key, -1)
    }
    fun complete(key: Any, status: Int, value: ByteArray? = null): Boolean {
        val op = current ?: return false
        if (op.key != key) return false
        current = null; cancelTimeout()
        op.complete(status, value); drain(); return true
    }
    fun timeout() {
        if (current == null) return
        close(); onTimeout()
    }
    fun close() { closed = true; current = null; waiting.clear(); cancelTimeout() }
}
