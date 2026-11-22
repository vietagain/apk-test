package com.vietvuive.ylauncher

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class VolumePowerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var isVolumeUpPressed = false
    private var longPressTriggered = false

    private val lockRunnable = Runnable {
        if (isVolumeUpPressed) {
            longPressTriggered = true
            lockDeviceScreen()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    isVolumeUpPressed = true
                    longPressTriggered = false
                    handler.postDelayed(lockRunnable, 600)
                }
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(lockRunnable)
                isVolumeUpPressed = false
                if (!longPressTriggered) {
                    val audio = getSystemService(AUDIO_SERVICE) as AudioManager
                    audio.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    private fun lockDeviceScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
