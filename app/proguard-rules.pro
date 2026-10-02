# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# --- Moto Crash Guardian ---
# Conservar numeros de linea para leer los stack traces de Play Console
# (subir mapping.txt de build/outputs/mapping/release con cada version).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Proto DataStore (Fase 1): los mensajes generados se acceden por reflexion.
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Hilt, Room, Retrofit 3, OkHttp 5 y kotlinx.serialization traen sus reglas de consumidor.
# Agregar aqui solo reglas verificadas con un build release real.
