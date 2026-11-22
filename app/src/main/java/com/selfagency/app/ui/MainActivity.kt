package com.selfagency.app.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.selfagency.app.blocking.Rules
import com.selfagency.app.blocking.UrlMatcher
import com.selfagency.app.data.BlockSettings
import com.selfagency.app.service.BlockerAccessibilityService
import com.selfagency.app.ui.theme.SelfAgencyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = BlockSettings(this)
        setContent {
            SelfAgencyTheme {
                SettingsScreen(settings)
            }
        }
    }
}

/** Something the user asked to loosen; held until they get through the cooldown dialog. */
private sealed interface PendingUnblock {
    val title: String
    data object TurnOffEverything : PendingUnblock { override val title = "Pause all blocking?" }
    data class App(val pkg: String, val label: String) : PendingUnblock { override val title = "Unblock $label?" }
    data class Site(val domain: String) : PendingUnblock { override val title = "Unblock $domain?" }
    data class Toggle(val label: String, val apply: () -> Unit) : PendingUnblock {
        override val title = "Allow $label again?"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(settings: BlockSettings) {
    val context = LocalContext.current
    var rules by remember { mutableStateOf(settings.snapshot()) }
    var serviceOn by remember { mutableStateOf(BlockerAccessibilityService.isEnabled(context)) }
    var showPicker by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingUnblock?>(null) }

    DisposableEffect(settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> rules = settings.snapshot() }
        settings.registerListener(listener)
        onDispose { settings.unregisterListener(listener) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        serviceOn = BlockerAccessibilityService.isEnabled(context)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Self Agency") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                StatusCard(
                    serviceOn = serviceOn,
                    rules = rules,
                    onOpenAccessibility = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onToggleEnabled = { on ->
                        if (on) settings.setEnabled(true) else pending = PendingUnblock.TurnOffEverything
                    },
                )
            }

            item {
                SectionCard("Shorts & Reels", "Keep the apps, lose the endless scroll.") {
                    ShortFormToggle(
                        "YouTube Shorts — in the app",
                        "Used when the YouTube app itself isn't blocked.",
                        rules.blockYoutubeShortsApp,
                    ) { on ->
                        if (on) settings.setYoutubeShortsApp(true)
                        else pending = PendingUnblock.Toggle("YouTube Shorts in the app") { settings.setYoutubeShortsApp(false) }
                    }
                    ShortFormToggle(
                        "YouTube Shorts — in the browser",
                        "youtube.com/shorts, when youtube.com isn't blocked.",
                        rules.blockYoutubeShortsWeb,
                    ) { on ->
                        if (on) settings.setYoutubeShortsWeb(true)
                        else pending = PendingUnblock.Toggle("YouTube Shorts in the browser") { settings.setYoutubeShortsWeb(false) }
                    }
                    ShortFormToggle(
                        "Instagram Reels — in the app",
                        "Used when the Instagram app itself isn't blocked.",
                        rules.blockInstagramReelsApp,
                    ) { on ->
                        if (on) settings.setInstagramReelsApp(true)
                        else pending = PendingUnblock.Toggle("Instagram Reels in the app") { settings.setInstagramReelsApp(false) }
                    }
                    ShortFormToggle(
                        "Instagram Reels — in the browser",
                        "instagram.com/reels, when instagram.com isn't blocked.",
                        rules.blockInstagramReelsWeb,
                    ) { on ->
                        if (on) settings.setInstagramReelsWeb(true)
                        else pending = PendingUnblock.Toggle("Instagram Reels in the browser") { settings.setInstagramReelsWeb(false) }
                    }
                }
            }

            item {
                SectionCard("Blocked apps", "These apps close as soon as they open.") {
                    if (rules.blockedApps.isEmpty()) {
                        Text("No apps blocked.", style = MaterialTheme.typography.bodyMedium)
                    }
                    rules.blockedApps
                        .map { it to InstalledApps.labelFor(context, it) }
                        .sortedBy { it.second.lowercase() }
                        .forEach { (pkg, label) ->
                            BlockedRow(
                                label = label,
                                leading = { AppIcon(InstalledApps.iconFor(context, pkg)) },
                                onRemove = { pending = PendingUnblock.App(pkg, label) },
                            )
                        }
                    OutlinedButton(onClick = { showPicker = true }) { Text("Add app") }
                }
            }

            item {
                SectionCard(
                    "Blocked websites",
                    "Blocks the site and all its subdomains (youtube.com also covers m.youtube.com).",
                ) {
                    if (rules.blockedSites.isEmpty()) {
                        Text("No websites blocked.", style = MaterialTheme.typography.bodyMedium)
                    }
                    rules.blockedSites.sorted().forEach { domain ->
                        BlockedRow(label = domain, onRemove = { pending = PendingUnblock.Site(domain) })
                    }
                    AddSiteField(onAdd = settings::addSite)
                }
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            alreadyBlocked = rules.blockedApps,
            onPick = { settings.addApp(it); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }

    pending?.let { p ->
        CooldownConfirmDialog(
            title = p.title,
            message = "Take a breath. Is this something you actually want right now, or just a reflex?",
            confirmLabel = if (p is PendingUnblock.TurnOffEverything) "Pause" else "Unblock",
            onConfirm = {
                when (p) {
                    PendingUnblock.TurnOffEverything -> settings.setEnabled(false)
                    is PendingUnblock.App -> settings.removeApp(p.pkg)
                    is PendingUnblock.Site -> settings.removeSite(p.domain)
                    is PendingUnblock.Toggle -> p.apply()
                }
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun StatusCard(
    serviceOn: Boolean,
    rules: Rules,
    onOpenAccessibility: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    val active = serviceOn && rules.enabled
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (active) "Blocking is on" else "Blocking is off",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (!serviceOn) {
                Text(
                    "Self Agency needs Accessibility access to see which app or website is open. " +
                        "Open Accessibility settings, find Self Agency under Downloaded / Installed apps, and turn it on.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onOpenAccessibility) { Text("Open Accessibility settings") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Blocking enabled", Modifier.weight(1f))
                    Switch(checked = rules.enabled, onCheckedChange = onToggleEnabled)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun ShortFormToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun BlockedRow(label: String, leading: (@Composable () -> Unit)? = null, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        leading?.invoke()
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onRemove) { Text("Unblock") }
    }
}

@Composable
private fun AddSiteField(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val submit = {
        val domain = UrlMatcher.normalizeDomain(text)
        if (domain == null) {
            error = "Enter a website like tiktok.com"
        } else {
            onAdd(domain)
            text = ""
            error = null
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; error = null },
            label = { Text("Add website") },
            placeholder = { Text("tiktok.com") },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = submit) { Text("Add") }
    }
}
