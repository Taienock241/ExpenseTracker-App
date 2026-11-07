package com.example.pesatrackexpensetrackapp

import android.app.Application

class PesaTrackApp : Application() {
    val container: AppContainer by lazy { AppContainer() }
}
