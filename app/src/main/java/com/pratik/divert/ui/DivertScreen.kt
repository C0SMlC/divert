@file:OptIn(ExperimentalMaterial3Api::class)

package com.pratik.divert.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pratik.divert.alarm.AlarmScheduler
import com.pratik.divert.data.EventLog
import com.pratik.divert.data.Settings
import com.pratik.divert.service.ServiceControl
import com.pratik.divert.telephony.ForwardingController
import com.pratik.divert.telephony.SimOption
import com.pratik.divert.telephony.Telephony
import com.pratik.divert.widget.DivertWidget
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private fun fmt(minutes: Int): String =
    LocalTime.of((minutes / 60) % 24, minutes % 60).format(TIME_FMT)

// label -> Calendar day constant (Mon..Sun)
private val WEEKDAYS = listOf(
    "Mon" to 2, "Tue" to 3, "Wed" to 4, "Thu" to 5, "Fri" to 6, "Sat" to 7, "Sun" to 1
)

@Composable
fun DivertScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = remember { Settings.get(context) }
    val snackbar = remember { SnackbarHostState() }

    var active by remember { mutableStateOf(s.isForwardingActive) }
    var number by remember { mutableStateOf(s.forwardingNumber) }
    var autoOff by remember { mutableStateOf(s.autoOffOnUnlock) }
    var winStart by remember { mutableIntStateOf(s.windowStart) }
    var winEnd by remember { mutableIntStateOf(s.windowEnd) }
    var autoOn by remember { mutableStateOf(s.autoOnEnabled) }
    var autoOnTime by remember { mutableIntStateOf(s.autoOnTime) }
    var autoOnDays by remember { mutableStateOf(s.autoOnDays) }
    var subId by remember { mutableIntStateOf(s.subscriptionId) }
    var activateTpl by remember { mutableStateOf(s.activateTemplate) }
    var deactivate by remember { mutableStateOf(s.deactivateCode) }
    var interrogate by remember { mutableStateOf(s.interrogateCode) }
    var showAdvanced by remember { mutableStateOf(false) }

    var hasPhone by remember { mutableStateOf(Telephony.hasPhonePermission(context)) }
    var sims by remember { mutableStateOf(Telephony.simOptions(context)) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-sync state that the widget or automations can change behind the app's back
    // (e.g. toggling forwarding from the home-screen widget) and keep the activity log
    // fresh — but only while the screen is actually visible, to avoid wasting battery.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                active = s.isForwardingActive
                hasPhone = Telephony.hasPhonePermission(context)
                refreshKey++
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    val perms = remember {
        buildList {
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPhone = Telephony.hasPhonePermission(context)
        sims = Telephony.simOptions(context)
    }

    fun snack(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    fun applyAutomation() {
        AlarmScheduler.scheduleAll(context)
        if (s.autoOffOnUnlock && s.isWithinWindow()) ServiceControl.start(context)
        else ServiceControl.stop(context)
    }

    fun onToggle() {
        if (!hasPhone) {
            permLauncher.launch(perms)
            return
        }
        ForwardingController.toggle(context) { ok, msg ->
            active = s.isForwardingActive
            DivertWidget.refresh(context)
            snack(msg)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Header()
            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(visible = !hasPhone) {
                Column {
                    PermissionBanner { permLauncher.launch(perms) }
                    Spacer(Modifier.height(16.dp))
                }
            }

            HeroStatus(active = active, number = number, onToggle = ::onToggle)
            Spacer(Modifier.height(20.dp))

            SectionCard(title = "Forwarding number") {
                OutlinedTextField(
                    value = number,
                    onValueChange = {
                        number = it.filter { c -> c.isDigit() || c == '+' }
                        s.forwardingNumber = number
                        active = s.isForwardingActive
                    },
                    singleLine = true,
                    placeholder = { Text("e.g. office landline") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Caption("Calls divert here when forwarding is ON.")
            }

            Spacer(Modifier.height(14.dp))

            SectionCard(title = "Auto-cancel on unlock") {
                RowSwitch(
                    label = "Cancel forwarding when I unlock",
                    sub = "Only during the window below",
                    checked = autoOff
                ) {
                    autoOff = it
                    s.autoOffOnUnlock = it
                    applyAutomation()
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TimeField(
                        label = "From",
                        minutes = winStart,
                        modifier = Modifier.weight(1f)
                    ) { winStart = it; s.windowStart = it; applyAutomation() }
                    TimeField(
                        label = "To",
                        minutes = winEnd,
                        modifier = Modifier.weight(1f)
                    ) { winEnd = it; s.windowEnd = it; applyAutomation() }
                }
            }

            Spacer(Modifier.height(14.dp))

            SectionCard(title = "Auto-enable on schedule") {
                RowSwitch(
                    label = "Turn forwarding ON automatically",
                    sub = "At the time & days below",
                    checked = autoOn
                ) {
                    autoOn = it
                    s.autoOnEnabled = it
                    AlarmScheduler.scheduleAll(context)
                }
                AnimatedVisibility(visible = autoOn) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        TimeField(
                            label = "Enable at",
                            minutes = autoOnTime,
                            modifier = Modifier.fillMaxWidth()
                        ) { autoOnTime = it; s.autoOnTime = it; AlarmScheduler.scheduleAll(context) }
                        Spacer(Modifier.height(12.dp))
                        WeekdayPicker(selected = autoOnDays) { day, on ->
                            autoOnDays = if (on) autoOnDays + day else autoOnDays - day
                            s.autoOnDays = autoOnDays
                            AlarmScheduler.scheduleAll(context)
                        }
                    }
                }
            }

            if (sims.size > 1) {
                Spacer(Modifier.height(14.dp))
                SectionCard(title = "SIM") {
                    SimSelector(sims = sims, selected = subId) {
                        subId = it; s.subscriptionId = it
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            SectionCard(title = "Advanced") {
                RowSwitch(
                    label = "Edit carrier codes",
                    sub = "USSD / MMI strings",
                    checked = showAdvanced
                ) { showAdvanced = it }
                AnimatedVisibility(visible = showAdvanced) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        CodeField("Activate (use {number})", activateTpl) {
                            activateTpl = it; s.activateTemplate = it
                        }
                        Spacer(Modifier.height(10.dp))
                        CodeField("Deactivate", deactivate) {
                            deactivate = it; s.deactivateCode = it
                        }
                        Spacer(Modifier.height(10.dp))
                        CodeField("Check status", interrogate) {
                            interrogate = it; s.interrogateCode = it
                        }
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = {
                            if (!hasPhone) { permLauncher.launch(perms); return@TextButton }
                            queryStatus(context) { msg -> snack(msg) }
                        }) { Text("Check carrier status now") }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            DiagnosticsCard(refreshKey = refreshKey) { refreshKey++ }

            Spacer(Modifier.height(20.dp))
            Caption(
                "Tip: add the Divert widget to your home screen for one-tap toggling. " +
                    "Status reflects what this app last set."
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun queryStatus(
    context: android.content.Context,
    onResult: (String) -> Unit
) {
    val s = Settings.get(context)
    val tm = Telephony.telephonyFor(context, s.subscriptionId)
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    try {
        tm.sendUssdRequest(
            s.interrogateCode,
            object : android.telephony.TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(
                    t: android.telephony.TelephonyManager,
                    request: String,
                    response: CharSequence
                ) = onResult(response.toString().ifBlank { "No response" })

                override fun onReceiveUssdResponseFailed(
                    t: android.telephony.TelephonyManager,
                    request: String,
                    failureCode: Int
                ) = onResult("Status check failed (code $failureCode)")
            },
            handler
        )
    } catch (e: Exception) {
        onResult(e.message ?: "Status check failed")
    }
}

@Composable
private fun Header() {
    Column {
        Text(
            "Divert",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Call-forwarding autopilot",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HeroStatus(active: Boolean, number: String, onToggle: () -> Unit) {
    val bg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        label = "heroBg"
    )
    val dot by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "heroDot"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable(onClick = onToggle)
            .padding(22.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(dot)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (active) "Forwarding ON" else "Forwarding OFF",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (active) "Tap to cancel" else "Tap to forward",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (number.isBlank()) "No number set"
                else if (active) "Calls go to $number" else "Will forward to $number",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun RowSwitch(
    label: String,
    sub: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun TimeField(
    label: String,
    minutes: Int,
    modifier: Modifier = Modifier,
    onPicked: (Int) -> Unit
) {
    var show by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { show = true }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(
                fmt(minutes),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
    if (show) {
        TimePickerDialog(
            initial = minutes,
            onDismiss = { show = false },
            onConfirm = { show = false; onPicked(it) }
        )
    }
}

@Composable
private fun TimePickerDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initial / 60,
        initialMinute = initial % 60,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Pick a time") },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        }
    )
}

@Composable
private fun WeekdayPicker(selected: Set<Int>, onToggle: (Int, Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WEEKDAYS.forEach { (label, day) ->
            val on = day in selected
            FilterChip(
                selected = on,
                onClick = { onToggle(day, !on) },
                label = {
                    Text(label, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SimSelector(sims: List<SimOption>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        sims.forEach { sim ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(sim.subscriptionId) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == sim.subscriptionId,
                    onClick = { onSelect(sim.subscriptionId) }
                )
                Spacer(Modifier.width(4.dp))
                Text(sim.label, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun CodeField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PermissionBanner(onGrant: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "Permission needed",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Divert needs phone access to send call-forwarding codes silently.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onGrant) { Text("Grant access") }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val CLOCK_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm:ss a")
private val STAMP_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE h:mm a")

private fun fmtClock(ts: Long): String =
    java.time.Instant.ofEpochMilli(ts)
        .atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(CLOCK_FMT)

private fun fmtStamp(ts: Long): String =
    java.time.Instant.ofEpochMilli(ts)
        .atZone(java.time.ZoneId.systemDefault()).format(STAMP_FMT)

@Composable
private fun DiagnosticsCard(refreshKey: Int, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val exactOk = remember(refreshKey) {
        if (Build.VERSION.SDK_INT >= 31)
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        else true
    }
    val batteryOk = remember(refreshKey) {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    val nextAuto = remember(refreshKey) { AlarmScheduler.nextAutoOn(context) }
    val entries = remember(refreshKey) { EventLog.entries(context) }

    SectionCard(title = "Reliability & activity") {
        StatusRow(
            ok = exactOk,
            label = "Alarms & reminders",
            sub = if (exactOk) "Allowed \u2014 scheduling is reliable"
            else "Off \u2014 auto-enable may not fire on time",
            actionLabel = if (exactOk) null else "Allow"
        ) {
            if (Build.VERSION.SDK_INT >= 31) runCatching {
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        StatusRow(
            ok = batteryOk,
            label = "Battery unrestricted",
            sub = if (batteryOk) "Exempt \u2014 background work won't be delayed"
            else "Restricted \u2014 unlock & alarms may be missed",
            actionLabel = if (batteryOk) null else "Fix"
        ) {
            runCatching {
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        }

        if (nextAuto != null) {
            Spacer(Modifier.height(12.dp))
            Caption("Next auto-enable scheduled for ${fmtStamp(nextAuto)}")
        }

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Activity log",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRefresh) { Text("Refresh") }
            TextButton(onClick = { EventLog.clear(context); onRefresh() }) { Text("Clear") }
        }
        Spacer(Modifier.height(2.dp))
        if (entries.isEmpty()) {
            Caption("No events yet. Unlocks and alarm activity will show up here.")
        } else {
            Column {
                entries.take(14).forEach { (ts, msg) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Text(
                            fmtClock(ts),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(72.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            msg,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    ok: Boolean,
    label: String,
    sub: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (ok) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
