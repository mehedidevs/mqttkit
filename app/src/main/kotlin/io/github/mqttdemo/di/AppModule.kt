package io.github.mqttdemo.di

import io.github.mehedidevs.mqttkit.HiveMqttClient
import io.github.mehedidevs.mqttkit.MqttSessionManager
import io.github.mqttdemo.presentation.MqttViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * App-level Koin module.
 *
 * MqttSessionManager is app-scoped so the same MQTT connection survives screen
 * rotations and ViewModel recreation.
 *
 * AndroidViewModel requires the Application as its first constructor parameter.
 * Koin provides it via androidApplication().
 */
val appModule = module {
    factory<(io.github.mehedidevs.mqttkit.MqttConfig) -> io.github.mehedidevs.mqttkit.MqttClient> { { cfg: io.github.mehedidevs.mqttkit.MqttConfig ->
        _root_ide_package_.io.food.core.mqtt.HiveMqttClient(
            cfg
        )
    } }
    single { _root_ide_package_.io.food.core.mqtt.MqttSessionManager(get()) }
    viewModel {
        _root_ide_package_.io.food.mqttdemo.presentation.MqttViewModel(
            application = androidApplication(),
            sessionManager = get()
        )
    }
}
