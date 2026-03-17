package com.example.kitchenmind

import android.app.Application
import com.example.kitchenmind.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class KitchenMindApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@KitchenMindApp)
            modules(appModule)
        }
    }
}
