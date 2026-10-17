package com.isaac.ridelab.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(modifier = Modifier.fillMaxWidth(), enabled = state.phase != BackupPhase.BUSY,
            onClick = {
                try { csv.launch("RideLab-CSV-${java.time.LocalDate.now()}.zip") }
                catch (error: Exception) { model.backupFailed(error) }
            }) { Text(stringResource(R.string.csv_archive_export)) }
        Text(stringResource(R.string.csv_archive_help), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(modifier = Modifier.weight(1f), enabled = state.phase != BackupPhase.BUSY,
                onClick = {
                    try { export.launch("RideLab-${java.time.LocalDate.now()}.ridelab") }
                    catch (error: Exception) { model.backupFailed(error) }
                }) { Text(stringResource(R.string.backup_export)) }
            OutlinedButton(modifier = Modifier.weight(1f), enabled = state.phase != BackupPhase.BUSY,
                onClick = { confirm = true }) { Text(stringResource(R.string.backup_restore)) }
        }
        when (state.phase) {
            BackupPhase.BUSY -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.backup_busy)) }
            BackupPhase.EXPORTED -> Text(stringResource(R.string.backup_exported, state.added))
            BackupPhase.RESTORED -> Text(stringResource(R.string.backup_restored, state.added, state.skipped))
            BackupPhase.FAILED -> Text(stringResource(R.string.backup_failed), color = MaterialTheme.colorScheme.error)
            else -> Unit
        }
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
