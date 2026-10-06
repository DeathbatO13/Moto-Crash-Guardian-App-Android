package com.motocrashguardian.data.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.motocrashguardian.data.settings.proto.AppSettingsProto
import java.io.InputStream
import java.io.OutputStream

internal object SettingsProtoSerializer : Serializer<AppSettingsProto> {
    override val defaultValue: AppSettingsProto = AppSettingsProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): AppSettingsProto =
        try {
            AppSettingsProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Unable to read settings.pb.", exception)
        }

    override suspend fun writeTo(t: AppSettingsProto, output: OutputStream) {
        t.writeTo(output)
    }
}
