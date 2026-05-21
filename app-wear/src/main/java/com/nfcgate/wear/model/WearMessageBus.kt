package com.nfcgate.wear.model

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object WearMessageBus {
    private val _messages = MutableSharedFlow<WearMessage>(extraBufferCapacity = 32)
    val messages = _messages.asSharedFlow()

    fun emit(message: WearMessage) {
        _messages.tryEmit(message)
    }
}
