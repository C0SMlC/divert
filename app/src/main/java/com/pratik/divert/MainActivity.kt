package com.pratik.divert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pratik.divert.alarm.AlarmScheduler
import com.pratik.divert.data.Settings
import com.pratik.divert.service.ServiceControl
import com.pratik.divert.ui.DivertScreen
import com.pratik.divert.ui.DivertTheme
import com.pratik.divert.widget.DivertWidget

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DivertTheme { DivertScreen() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Keep automation in sync with the latest settings whenever the app is opened.
        AlarmScheduler.scheduleAll(this)
        val s = Settings.get(this)
        if (s.autoOffOnUnlock && s.isWithinWindow()) ServiceControl.start(this)
        DivertWidget.refresh(this)
    }
}
