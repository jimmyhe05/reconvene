package com.reconvene

import android.app.Application

class ReconveneApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}
