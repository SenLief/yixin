package cn.yixin.todo

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val model: YixinViewModel by viewModels()
    private val systemChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> model.background()
                Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> model.refreshToday()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(
            this, systemChanges, IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }, ContextCompat.RECEIVER_EXPORTED,
        )
        setContent { YixinApp(model, window, ::setFocusOrientation) }
    }

    private fun setFocusOrientation(landscape: Boolean?) {
        val orientation = when (landscape) {
            true -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            false -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            null -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (requestedOrientation != orientation) requestedOrientation = orientation
    }

    override fun onResume() {
        super.onResume()
        if (!getSystemService(KeyguardManager::class.java).isKeyguardLocked) model.foreground()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !getSystemService(KeyguardManager::class.java).isKeyguardLocked) model.foreground()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        model.background()
    }

    override fun onStop() {
        if (!isChangingConfigurations) model.background()
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(systemChanges)
        super.onDestroy()
    }
}
