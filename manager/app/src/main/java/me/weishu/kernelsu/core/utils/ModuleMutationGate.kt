package me.weishu.kernelsu.core.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore

/** One process-wide owner for installer scripts and user module mutations. */
object ModuleMutationGate {
    private val permits = Semaphore(1)
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    fun tryAcquire(): Boolean {
        if (!permits.tryAcquire()) return false
        mutableBusy.value = true
        return true
    }
    fun release() { mutableBusy.value = false; permits.release() }
}
