package com.example.aireader

import java.util.concurrent.atomic.AtomicLong

class RequestGeneration {
    private val current = AtomicLong(0)
    fun next(): Long = current.incrementAndGet()
    fun invalidate() { current.incrementAndGet() }
    fun isCurrent(value: Long): Boolean = current.get() == value
}
