package com.openfog.online.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openfog.online.ui.OpenFogViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(viewModel: OpenFogViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracksVisible by viewModel.tracksVisible.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()

    var confirmReset by remember { mutableStateOf<SuspendableAction?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.buildBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray())
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment ?: "backup.json"
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                }
                viewModel.importBackup(String(bytes))
            }
        }
    }

    val fileImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment ?: "track"
            viewModel.importFile(name) {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                }
            }
        }
    }

    confirmReset?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text(action.title) },
            text = { Text("Diese Aktion kann nicht rückgängig gemacht werden.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = null
                    scope.launch { action.execute(viewModel) }
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = null }) { Text("Abbrechen") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineSmall)

        if (importProgress != null) {
            LinearProgressIndicator(
                progress = (importProgress!!.first / 100f),
                modifier = Modifier.fillMaxWidth()
            )
            Text(importProgress!!.second, style = MaterialTheme.typography.bodySmall)
        }

        // Tracks toggle.
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Tracks anzeigen", style = MaterialTheme.typography.titleSmall)
                }
                Switch(checked = tracksVisible, onCheckedChange = { viewModel.toggleTracks() })
            }
        }

        // Import track file.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Track importieren", style = MaterialTheme.typography.titleSmall)
                Text("GPX, KML oder KMZ-Datei wählen.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    fileImportLauncher.launch(arrayOf("application/gpx+xml", "application/vnd.google-earth.kml+xml", "application/vnd.google-earth.kmz", "application/xml", "*/*"))
                }) { Text("Datei auswählen") }
            }
        }

        // Backup.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Daten sichern / wiederherstellen", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { exportLauncher.launch("fog-of-world-backup-${System.currentTimeMillis()}.json") }) {
                        Text("Export")
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                        Text("Import")
                    }
                }
            }
        }

        // Resets.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Daten zurücksetzen", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { confirmReset = SuspendableAction("Alles zurücksetzen") { vm -> vm.resetAllData() } }) {
                    Text("Alles zurücksetzen")
                }
                OutlinedButton(onClick = { confirmReset = SuspendableAction("Tracks + Statistik zurücksetzen") { vm -> vm.resetTracksAndStats() } }) {
                    Text("Tracks + Statistik")
                }
                OutlinedButton(onClick = { confirmReset = SuspendableAction("Nur Nebel zurücksetzen") { vm -> vm.resetFogOnly() } }) {
                    Text("Nur Nebel")
                }
            }
        }
    }
}

private class SuspendableAction(
    val title: String,
    val execute: suspend (OpenFogViewModel) -> Unit
)
