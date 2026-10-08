package com.motocrashguardian.di

import android.content.Context
import com.motocrashguardian.emergency.CallDispatcher
import com.motocrashguardian.emergency.CallTransport
import com.motocrashguardian.emergency.SmsDispatcher
import com.motocrashguardian.emergency.SmsTransport
import com.motocrashguardian.emergency.platform.AndroidCallPermission
import com.motocrashguardian.emergency.platform.AndroidSmsPermission
import com.motocrashguardian.emergency.platform.AndroidSmsTransport
import com.motocrashguardian.emergency.platform.AndroidTelecomCallTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EmergencyDispatchModule {
    @Provides
    @Singleton
    fun provideAndroidSmsTransport(
        @ApplicationContext context: Context
    ): AndroidSmsTransport = AndroidSmsTransport(context)

    @Provides
    fun provideSmsTransport(transport: AndroidSmsTransport): SmsTransport = transport

    @Provides
    fun provideSmsDispatcher(
        @ApplicationContext context: Context,
        transport: SmsTransport
    ): SmsDispatcher = SmsDispatcher(
        transport = transport,
        hasSendPermission = AndroidSmsPermission(context)::invoke
    )

    @Provides
    @Singleton
    fun provideCallTransport(
        @ApplicationContext context: Context
    ): CallTransport = AndroidTelecomCallTransport(context)

    @Provides
    fun provideCallDispatcher(
        @ApplicationContext context: Context,
        transport: CallTransport
    ): CallDispatcher = CallDispatcher(
        transport = transport,
        hasCallPermission = AndroidCallPermission(context)::invoke
    )
}
