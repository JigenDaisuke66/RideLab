package com.isaac.ridelab.ui.ai

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.ai.analysis.*
import com.isaac.ridelab.ui.theme.RideLabCard

@Composable
internal fun AiAnalysisRoute(model: AiAnalysisViewModel, close: () -> Unit,
    settings: @Composable () -> Unit = {}) {
    val ui by model.state.collectAsState()
    val languageTag = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].toLanguageTag()
    AiAnalysisScreen(ui, model::question, model::skill, { model.analyze(languageTag) }, model::cancel,
        model::clear, close, settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiAnalysisScreen(ui: AiAnalysisUiState, question: (String) -> Unit, skill: (AiAnalysisSkill) -> Unit,
    analyze: () -> Unit, cancel: () -> Unit, clear: () -> Unit, close: () -> Unit,
    settings: @Composable () -> Unit = {}) {
    Scaffold(modifier = Modifier.fillMaxSize().imePadding(), topBar = {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), title = { Text(stringResource(R.string.ai_analysis_title)) }, navigationIcon = {
            IconButton(onClick = close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.ai_close)) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            settings()
            Text(stringResource(R.string.ai_analysis_privacy), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            SkillPicker(ui.skill, !ui.running, skill)
            Text(stringResource(skillDescription(ui.skill)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(ui.question, question, enabled = !ui.running, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.ai_analysis_question)) },
                placeholder = { Text(stringResource(R.string.ai_analysis_example)) }, minLines = 2, maxLines = 5)
            Text(stringResource(R.string.ai_analysis_budget), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ui.running) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (ui.cancelling) stringResource(R.string.ai_analysis_cancelling)
                    else if (ui.requestIndex == 0) stringResource(R.string.ai_analysis_running)
                    else stringResource(if (ui.generatingReport) R.string.ai_analysis_report_wait else R.string.ai_analysis_model_wait,
                        ui.requestIndex, ui.elapsedSeconds))
                OutlinedButton(onClick = cancel, enabled = !ui.cancelling, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ai_analysis_stop))
                }
            } else Button(onClick = analyze, enabled = ui.question.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_analysis_start))
            }
            ui.provider?.let { Text(stringResource(R.string.ai_analysis_provider, stringResource(providerTitle(it))),
                style = MaterialTheme.typography.labelMedium) }
            ui.error?.let { error ->
                val requestTimedOut = error == AiAnalysisError.API && ui.apiProblem == com.isaac.ridelab.ai.AiProblem.TIMEOUT
                Text(if (requestTimedOut) stringResource(R.string.ai_analysis_request_timeout, ui.requestIndex)
                    else stringResource(analysisError(error)), color = MaterialTheme.colorScheme.error)
                ui.apiProblem?.takeIf { !requestTimedOut }?.let { Text(stringResource(problemTitle(it)), color = MaterialTheme.colorScheme.error) }
                ui.httpStatus?.let { Text(stringResource(R.string.ai_http_status, it)) }
            }
            ui.answer?.let { answer ->
                RideLabCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ai_analysis_report), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    SelectionContainer { Text(answer.text, style = MaterialTheme.typography.bodyLarge) }
                }
                val usage = answer.usage
                Text(if (usage.input != null && usage.output != null)
                    stringResource(R.string.ai_analysis_tokens, usage.input, usage.output, answer.requests)
                    else stringResource(R.string.ai_analysis_tokens_unknown, answer.requests),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ui.traces.isNotEmpty()) QueryTraces(ui.traces)
            if (ui.answer != null || ui.error != null || ui.traces.isNotEmpty()) TextButton(onClick = clear, enabled = !ui.running) {
                Text(stringResource(R.string.ai_analysis_clear))
            }
        }
    }
}

@Composable
private fun SkillPicker(selected: AiAnalysisSkill, enabled: Boolean, select: (AiAnalysisSkill) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(skillTitle(selected)))
        }
        DropdownMenu(expanded && enabled, { expanded = false }) {
            AiAnalysisSkill.entries.forEach { option -> DropdownMenuItem(text = { Text(stringResource(skillTitle(option))) },
                onClick = { expanded = false; select(option) }) }
        }
    }
}

@Composable
private fun QueryTraces(traces: List<AiToolTrace>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.ai_analysis_queries, traces.size)) }
        if (expanded) traces.forEachIndexed { index, trace ->
            RideLabCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_analysis_query_number, index + 1,
                    stringResource(if (trace.name == "query_sql") R.string.ai_analysis_sql else R.string.ai_analysis_metrics)))
                SelectionContainer { Text(trace.arguments, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                trace.returnedRows?.let { Text(stringResource(R.string.ai_analysis_rows, it), style = MaterialTheme.typography.bodySmall) }
                if (trace.cached) Text(stringResource(R.string.ai_analysis_cached), style = MaterialTheme.typography.bodySmall)
                if (trace.truncated) Text(stringResource(R.string.ai_analysis_truncated), color = MaterialTheme.colorScheme.error)
                trace.error?.let { Text(stringResource(R.string.ai_analysis_query_error, it), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

internal fun skillTitle(skill: AiAnalysisSkill) = when (skill) {
    AiAnalysisSkill.RIDE_REVIEW -> R.string.ai_skill_review
    AiAnalysisSkill.ENDURANCE -> R.string.ai_skill_endurance
    AiAnalysisSkill.FTP_EVIDENCE -> R.string.ai_skill_ftp
    AiAnalysisSkill.TRAINING_TRENDS -> R.string.ai_skill_trends
}
private fun skillDescription(skill: AiAnalysisSkill) = when (skill) {
    AiAnalysisSkill.RIDE_REVIEW -> R.string.ai_skill_review_hint
    AiAnalysisSkill.ENDURANCE -> R.string.ai_skill_endurance_hint
    AiAnalysisSkill.FTP_EVIDENCE -> R.string.ai_skill_ftp_hint
    AiAnalysisSkill.TRAINING_TRENDS -> R.string.ai_skill_trends_hint
}
private fun analysisError(error: AiAnalysisError) = when (error) {
    AiAnalysisError.CONFIGURATION -> R.string.ai_analysis_config_error
    AiAnalysisError.API -> R.string.ai_analysis_api_error
    AiAnalysisError.OUTPUT_LIMIT -> R.string.ai_analysis_output_limit
    AiAnalysisError.BUDGET -> R.string.ai_analysis_budget_error
    AiAnalysisError.NO_EVIDENCE -> R.string.ai_analysis_no_evidence
    AiAnalysisError.STORAGE -> R.string.ai_error_storage
    AiAnalysisError.CANCELLED -> R.string.ai_analysis_cancelled
    AiAnalysisError.TIME_LIMIT -> R.string.ai_analysis_time_limit
    AiAnalysisError.INTERNAL -> R.string.ai_analysis_internal
}
