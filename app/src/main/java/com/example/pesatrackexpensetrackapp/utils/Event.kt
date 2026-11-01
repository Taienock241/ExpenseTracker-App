package com.example.pesatrackexpensetrackapp.utils

class Event<out T>(private val content: T) {
    private var handled = false

    fun getContentIfNotHandled(): T? {
        if (handled) return null
        handled = true
        return content
    }

    fun peekContent(): T = content
}
