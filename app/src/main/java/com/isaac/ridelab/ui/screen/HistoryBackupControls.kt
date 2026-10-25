package com.isaac.ridelab.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R

/** Document pickers are platform adapters; all archive/database work stays in the ViewModel. */
@Composable
fun HistoryBackupControls(model: HistoryViewModel) {
    val state by model.backup.collectAsState()
    var confirm by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var acknowledged by rememberSaveable { mutableStateOf("") }
    val resultKey = "${state.phase}:${state.added}:${state.skipped}"
    LaunchedEffect(state.phase) { if (state.phase == BackupPhase.BUSY) acknowledged = "" }
    val labels = com.isaac.ridelab.export.CsvArchiveLabels(
        androidx.compose.ui.res.stringArrayResource(R.array.ride_csv_headers).toList(),
        listOf(stringResource(R.string.csv_archive_file),
            stringResource(R.string.history_start) + " (UTC)", stringResource(R.string.history_end) + " (UTC)",
            stringResource(R.string.csv_elapsed), stringResource(R.string.history_status), stringResource(R.string.history_samples)),
        stringResource(R.string.history_completed), stringResource(R.string.history_aborted))
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        if (it != null) model.exportAllCsv(it, labels)
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        if (it != null) model.backupHistory(it, restore = false)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) model.backupHistory(it, restore = true)
    }
    Box {
        IconButton(enabled = state.phase != BackupPhase.BUSY, onClick = { menu = true }) {
            if (state.phase == BackupPhase.BUSY) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.ui_data_management))
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.csv_archive_export)) }, onClick = {
                menu = false
                try { csv.launch("RideLab-CSV-${java.time.LocalDate.now()}.zip") }
                catch (error: Exception) { model.backupFailed(error) }
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.backup_export)) }, onClick = {
                menu = false
                try { export.launch("RideLab-${java.time.LocalDate.now()}.ridelab") }
                catch (error: Exception) { model.backupFailed(error) }
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.backup_restore)) },
                onClick = { menu = false; confirm = true })
        }
    }
    if (state.phase in setOf(BackupPhase.EXPORTED, BackupPhase.RESTORED, BackupPhase.FAILED) && acknowledged != resultKey) {
        AlertDialog(onDismissRequest = { acknowledged = resultKey },
            title = { Text(stringResource(R.string.ui_data_management)) },
            text = { Text(when (state.phase) {
                BackupPhase.EXPORTED -> stringResource(R.string.backup_exported, state.added)
                BackupPhase.RESTORED -> stringResource(R.string.backup_restored, state.added, state.skipped)
                else -> stringResource(R.string.backup_failed)
            }) },
            confirmButton = { TextButton(onClick = { acknowledged = resultKey }) {
                Text(stringResource(R.string.erg_confirm))
            } })
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.backup_restore)) },
        text = { Text(stringResource(R.string.backup_explanation)) },
        confirmButton = { TextButton(onClick = {
            confirm = false
            try { restore.launch(arrayOf("*/*")) } catch (error: Exception) { model.backupFailed(error) }
        }) { Text(stringResource(R.string.backup_choose)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.backup_cancel)) } })
}
