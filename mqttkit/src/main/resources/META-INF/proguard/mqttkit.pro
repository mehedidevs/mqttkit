# Consumer rules for apps that depend on core-mqtt.
#
# The core module does not use reflection to decode app data classes. JSON typed
# helpers rely on kotlinx.serialization generated serializers, so app models
# should be annotated with @kotlinx.serialization.Serializable and compiled with
# the Kotlin serialization plugin.

# Preserve generic signatures and annotations used by Kotlin/coroutines and
# serialization metadata. These are low-risk keeps for optimized Android apps.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# HiveMQ/Netty may reference optional platform classes depending on transport
# features. Android apps do not need these optional classes for normal MQTT TCP.
-dontwarn io.netty.handler.ssl.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
