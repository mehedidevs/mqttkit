package io.github.mqttdemo

import android.app.Application
import io.github.mqttdemo.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber

class MqttDemoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Timber — structured logging. Plant a DebugTree in debug builds only.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Koin — dependency injection container.
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@MqttDemoApp)
            modules(_root_ide_package_.io.food.mqttdemo.di.appModule)
        }
    }
}
