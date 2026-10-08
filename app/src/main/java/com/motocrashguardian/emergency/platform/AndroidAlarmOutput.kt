package com.motocrashguardian.emergency.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.motocrashguardian.emergency.AlarmOutput

class AndroidAlarmOutput(context: Context) : AlarmOutput {
    private val appContext = context.applicationContext
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Vibrator::class.java)
    }
    private var player: MediaPlayer? = null

    @Synchronized
    override fun start() {
        if (player != null) return
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: throw IllegalStateException("No default alarm tone is configured.")
        val newPlayer = MediaPlayer.create(appContext, alarmUri)
            ?: throw IllegalStateException("Could not create the alarm sound player.")
        try {
            newPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            newPlayer.isLooping = true
            newPlayer.start()
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createWaveform(SosPattern, 0))
            } else {
                Log.w(TAG, "Vibration hardware is unavailable; alarm sound will continue.")
            }
            player = newPlayer
        } catch (error: SecurityException) {
            release(newPlayer)
            throw error
        } catch (error: IllegalStateException) {
            release(newPlayer)
            throw error
        }
    }

    @Synchronized
    override fun stop() {
        vibrator?.cancel()
        player?.let(::release)
        player = null
    }

    private fun release(mediaPlayer: MediaPlayer) {
        if (mediaPlayer.isPlaying) mediaPlayer.stop()
        mediaPlayer.release()
    }

    private companion object {
        const val TAG = "AndroidAlarmOutput"
        val SosPattern = longArrayOf(
            0, 250, 100, 250, 100, 250, 400,
            600, 100, 600, 100, 600, 400
        )
    }
}
