# Read-only SQL cycling analyst

Implemented as an optional, user-started feature on top of AI_SERVICES.md. Physical
provider/device verification remains pending. This is an in-app analyst, not an
exported MCP/HTTP server and not an AI permission to operate Android or the trainer.

## Using it

1. Settings > AI services: select the provider, correct base URL, full model ID and
   API key. Test if desired, then **Save configuration** once. Keys are encrypted
   by Android Keystore; they are restored across launches, not backed up/reinstalled.
2. Kimi offers International .ai / China .cn. Switching keeps the model/key text;
   use a key valid for that account region and save. Existing .ai configurations
   are preserved. This is one Kimi profile storing the last saved endpoint, not two
   separately stored region accounts.
3. Settings > AI ride analysis: choose a skill and enter a question, then Start.
   Analysis uses the **last saved provider/configuration**, not an unsaved settings
   draft. Opening/typing/restoring settings never sends data or starts analysis.
4. Expand Query evidence to inspect SQL/metric-tool requests, returned row counts,
   truncation, cache hits and errors. Reports are selectable plain text.
5. Each question is independent. Results survive rotation through ViewModel, but
   are not persisted to Room and disappear on process death or explicit Clear.
   Closing the screen leaves an in-flight run in that ViewModel; Stop cancels it.

Starting analysis permits the selected endpoint to receive the question and required
SQL/metric results, including all ride records, current/historical FTP and weight.
The screen discloses this before Start. Queries are performed on-device; cloud
analysis is not on-device private analysis. Local APIs receive results on that host.
API charges may apply; the app cannot guarantee cost or accuracy for arbitrary models.

## Data tools supplied to the model

### query_sql

Arguments: `{"sql":"SELECT ..."}`. SQLite single SELECT and non-recursive WITH/CTE,
including joins, aggregates, date/time functions and window functions supported by
the device's SQLite. The model receives the exact existing schema and units before
calling tools. SQL is not reduced to a set of canned filters.

Sources:
- `ride_sessions`: all existing columns (IDs, status, wall-clock/active timing,
  source device information, saved target, historical FTP and weight).
- `ride_samples`: all existing raw columns (session ID, wall/elapsed timing,
  trainer/external power separately, HR, cadence, target, speed/resistance, sources).
- `user_profile`: one virtual row `ftpWatts INTEGER NULL, bodyWeightKg REAL NULL`.
  These are **current settings**, bound into the statement; they are not Room columns
  or a new storage layer. Historical fields remain `analyticsFtpWatts`/`bodyWeightKg`
  on each ride session. Null is preserved when the current setting is unset.

The full data dictionary is `AiAnalysisInstructions.SCHEMA` in
`ai/analysis/AiAnalysisSkill.kt`; the model is given it on each run. Schema v3 is
unchanged. Example queries (omit terminating semicolons):

```sql
SELECT ftpWatts, bodyWeightKg FROM user_profile
```

```sql
SELECT id, startTimeEpochMillis, durationMillis, status, analyticsFtpWatts, bodyWeightKg
FROM ride_sessions WHERE status = 'COMPLETED'
ORDER BY startTimeEpochMillis DESC, id DESC LIMIT 5
```

```sql
SELECT elapsedMillis / 60000 AS minute,
       COUNT(*) AS rows, COUNT(trainerPowerWatts) AS powerRows,
       AVG(trainerPowerWatts) AS avgW, MAX(trainerPowerWatts) AS maxW,
       AVG(heartRateBpm) AS avgBpm, AVG(cadenceRpm) AS avgRpm
FROM ride_samples WHERE sessionId = 1
GROUP BY minute ORDER BY minute
```

Numbers above are query syntax examples, not production ride data. SQL AVG/MAX
ignore null and retain real zero. SQL sample AVG is not an assertion of continuous
time coverage. Model instructions require duration/coverage checks before inference.
Rows are encoded compactly as `columns` + `rows`, with `returned_rows` and `truncated`.

Read barriers and resource limits:
- Repository opens an `OPEN_READONLY` SQLite handle on the **same Room file**, after
  the existing Room owner initializes it. No second database, file copy, schema,
  Room owner or recording connection is created; the short-lived reader closes.
- The SELECT is also wrapped as a subquery with an outer LIMIT and bound profile
  values. Writes, transactions, ATTACH, PRAGMA/table-valued PRAGMA, comments,
  multi-statements, recursive CTEs, extension/file functions and a small group of
  unbounded blob/string aggregation functions are rejected before execution.
  The tool cannot access API settings, Keystore or arbitrary files.
- Maximum SQL 6000 characters, 100 rows, 24 columns, 10000 result characters, strings
  256 characters; numeric wire values round to 3 decimals. Truncation is explicit.
- A 2-second watchdog cancels the native query. Non-cancellation SQL errors return
  structured codes without reflecting raw exception text. Results are not SQL writes.
- AI readers do not stop/restart the foreground service. WAL/Room remains recording
  owner. Actual Android concurrent read/write behavior must be checked on device.

### ride_metrics

Arguments: `{"session_id":123}`. Uses existing ride/session repository reads and
**unchanged** TrainingAnalytics, PowerAnalysisCalculator and RideInsightsCalculator.
Returns NP, IF, TSS, saved FTP/weight, null-aware average/max sensors, best efforts,
FTP zones, work/estimated kcal, coverage, first/last samples and gap count.

No historical FTP/weight backfill. No AI-computed replacement NP/TSS, new formulas,
schema migration, eFTP, CTL/ATL/TSB or HR zones. Energy estimate remains the existing
24% gross-efficiency approximation. ACTIVE metrics are provisional; ABORTED remains
interrupted. To bound memory, metrics on a ride with more than 36000 samples return
an explicit limit error; arbitrary bounded SQL aggregates still remain available.

## Analyst skills

Original, reviewable instructions are bundled in `app/src/main/assets/ai_skills/`:
- `ride_review.md`: execution quality, power/HR/cadence, coverage and practical review.
- `endurance.md`: steady pacing, comparable segments, HR-drift confounders and evidence.
- `ftp_evidence.md`: maximal-effort limitations, current versus historical FTP;
  no arbitrary best-minute ramp reconstruction or automatic FTP update.
- `training_trends.md`: frequency/duration and comparable calendar periods;
  partial-period/incomplete-load caveats, no invented CTL/ATL or recovery claims.

Only the selected skill is loaded. Shared instructions require actual successful
tool reads, ride IDs/dates, units, data quality, measured-versus-derived-versus-inferred
distinctions, and concise actions plus limitations. Empty successful reads can support
"no recorded data". A model that never calls tools cannot present an apparent
personal analysis. These are prompt policies, not a guarantee of expert accuracy.

References used to review terminology (not copied prompts):
[NP/IF/TSS definitions, Andrew Coggan](https://www.trainingpeaks.com/learn/articles/normalized-power-intensity-factor-training-stress/),
[TSS formula, Joe Friel](https://www.trainingpeaks.com/learn/articles/estimating-training-stress-score-tss/).
No additional analytics calculation is introduced by these instructions.

## Token and execution budgets

- No whole-database dump, full 1 Hz stream, previous chat history, all-skills prompt
  or background polling. Question max 2000 characters; selected skill + compact schema.
- SQL aggregates first; exact complex metrics computed locally, not by the model.
  Compact tabular results, precision/row/size limits, duplicate per-run tool cache.
- At most 5 model requests, 6 tool calls, 2048 output tokens per request and 60000
  request-body characters. Final turn offers no tools. No automatic retries or
  cross-analysis stale caches. Whole analysis has a 300-second timeout.
- Token usage is summed from provider-reported usage only. Gemini thought tokens are
  included when reported. Unknown usage is shown as unavailable, not fabricated zero.
  No fixed cost/token-saving percentage is claimed; reasoning models may consume the
  output budget before answering. Such exhaustion is an explicit error, not success.
- Models must support the chosen native tool protocol. OpenAI-compatible function
  calls (including Kimi reasoning_content replay), Claude tool_use/tool_result, Gemini
  functionCall/functionResponse with thought signatures are supported structurally.
  Arbitrary models/local servers may not support them. No silent text-SQL execution
  fallback. Analysis has its own 120-second request/read budget; the short connection
  probe retains a 30-second read timeout. Redirect and certificate rules are unchanged.
  Kimi analysis uses bounded Chat Completions SSE, preserving full tool arguments,
  reasoning replay and the final usage chunk. Other providers keep their existing
  non-streaming request format. K3 explicitly uses reasoning_effort=low and the
  documented max_completion_tokens=2048 instead of inheriting max reasoning effort.

Native API references checked:
[OpenAI function calling](https://developers.openai.com/api/docs/guides/function-calling),
[Claude tool results](https://platform.claude.com/docs/en/agents-and-tools/tool-use/handle-tool-calls),
[Gemini function calling](https://ai.google.dev/gemini-api/docs/function-calling).
Endpoint presets are not hardware/provider verification.

## Verification and manual acceptance

Run relevant unit tests plus app/Android-test compilation only. No APK/AAB:
```text
:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest
--tests 'com.isaac.ridelab.ai.*' --tests '*TranslationResourcesTest'
```

Unit coverage: SQL guard/compact values/limits, native metrics and nullable snapshots,
all three tool envelopes/replay fields/usage, bounded tool loops/cache/no-evidence,
saved credentials, selected skill, cancellation, no automatic requests and Kimi region.
Android compile-only coverage adds real file-backed Room/reader/profile/null/write-barrier
checks and analysis Start/Stop callbacks. No real keys or remote providers in tests.

Manual steps:
1. Save Kimi .cn (or .ai) with the matching key/model; restart. Address/model/key remain.
   Switch region: model/key stay filled. Save to remember it; regional key validity
   still belongs to the provider. Do not assume .ai and .cn keys are interchangeable.
2. Open analysis with completed rides. Ask for the latest ride review. Expand evidence:
   a SELECT finds the ride and ride_metrics gives the exact detail-screen NP/IF/TSS.
3. Ask for current FTP/weight and compare them with an older ride. Confirm SELECT of
   user_profile versus the ride snapshots; changing global FTP must not alter old data.
4. Try each skill. For a period with no records, the result should state missing data;
   with missing HR/FTP it must not invent HR zones, IF or TSS.
5. Ask for DELETE/UPDATE or unrelated tools: blocked, history/settings unchanged.
   Ask for more than 100 samples: visible truncation, not a claim of full data.
6. Rapidly tap Start, then Stop; no duplicate analysis or late report. Rotate, switch
   tabs and reopen: one ViewModel run/result. Starting a new question is independent.
7. During a real trainer+HR ride, run a small analysis, background/lock/unlock and
   verify recording/ERG/BLE continue. Stop/end behavior is unchanged.
8. Check seven languages, large font, keyboard and dropdowns. Inspect Logcat/exports:
   no API keys or whole prompts/results are logged or added to backups.

Changes: new ai/analysis domain files, AiAnalysisWire/AiHttpTransport, read-only
data/repository/AiRideDataRepository, ui/ai analysis controller/ViewModel/screen,
four skill assets, application/Activity wiring, Kimi settings chips, seven resource
files, focused JVM/Android tests, AI_SERVICES/PROJECT/UI_ARCHITECTURE/AGENTS docs.
Dependencies: **none added or changed in this increment**.

Executed verification (2026-10-25): **101 relevant JVM tests passed, 0 failures,
0 errors** (54 AI, 45 existing analytics regression, 2 localization). Application
Kotlin and all Android-test Kotlin compilation passed, including the nullable native
profile binding. No APK/AAB generated. Real provider/Android SQL/UI execution remains
pending; compile-SDK compatibility and existing Compose test deprecation warnings remain.

## Detailed changed-file list

- `ai/AiProvider.kt`, `ai/HttpAiConnectionProbe.kt`; new `ai/AiHttpTransport.kt`,
  `ai/AiAnalysisWire.kt`.
- New `ai/analysis/AiSqlPolicy.kt`, `AiRideData.kt`, `AiRideMetrics.kt`,
  `AiAnalysisSkill.kt`, `AiAnalysisEngine.kt`.
- New `data/repository/AiRideDataRepository.kt`.
- `ui/ai/AiSettingsCard.kt`, `AiSettingsViewModel.kt`; new `AiAnalysisController.kt`,
  `AiAnalysisViewModel.kt`, `AiAnalysisScreen.kt`.
- `MainActivity.kt`, `RideLabApplication.kt`: additive AI wiring only.
- Four new `main/assets/ai_skills/*.md` files listed above.
- `main/res/values/strings.xml`, `values-en`, `values-ja`, `values-zh-rTW`,
  `values-es`, `values-pt-rBR`, `values-fr`: 41 new IDs and the setup summary update.
- `test/.../ai/AiSettingsControllerTest.kt`; new `AiSqlPolicyTest.kt`,
  `AiRideMetricsTest.kt`, `AiAnalysisEngineTest.kt`, `AiAnalysisWireTest.kt`,
  `AiAnalysisControllerTest.kt`, `HttpAiAnalysisTest.kt`, `AiAnalysisInstructionsTest.kt`.
- New `androidTest/.../AiAnalysisUiTest.kt`, `ai/AiRideDataRepositoryTest.kt`.
- `AGENTS.md`, `PROJECT.md`, `UI_ARCHITECTURE.md`, `AI_SERVICES.md`, new `AI_ANALYSIS.md`.

## Kimi K3 timeout correction

Physical report: SQL returned profile/history rows and six tool traces were present,
then the screen showed a timeout plus a misleading suggestion about unsupported tools.
This establishes successful provider/tool/data communication, not a missing API key
or proof that tool calling is unsupported. The screenshot cannot distinguish a socket
timeout from the old aggregate deadline without timing logs. Code audit found both
an overly short shared 30-second read timeout and a 120-second whole-analysis timeout.

The [official Kimi Chat API](https://platform.kimi.ai/docs/api/chat) documents streaming
with stream_options.include_usage, preserved reasoning for tool replay, K3's default
max reasoning effort with low available, and max_completion_tokens. The correction:

- Kimi analysis streams response chunks; the decoder assembles fragmented tool calls
  before any execution, retains reasoning only for replay, requires a completed stream
  and finish reason, and never renders private reasoning as an answer.
- Wire framing is capped at 2 Mi characters, each line/assembled content at 64 Ki
  characters; oversized/malformed/interrupted streams fail explicitly. Normal JSON
  responses remain accepted on the same request if an endpoint ignores streaming.
- K3 uses low effort and the same 2048-token ceiling. It does not receive an invalid
  thinking-disable flag or an unlimited output allowance. No model substitution.
- After six tools, Kimi retains the same definitions/reasoning but sends tool_choice=none
  to produce the final report; the engine still rejects further tool calls.
- Analysis request/read limit is 120 seconds, whole run 5 minutes. A bounded transport
  watcher attempts to close blocked I/O on cancellation/deadline; heartbeats do not
  extend the deadline indefinitely. Stop remains single-flight, with no retries.
- UI shows model request index/report stage and monotonic elapsed seconds. A socket
  timeout identifies its request; a whole-run deadline has a distinct localized state.
  Previously completed query traces remain. General API errors no longer allege that
  tools are unsupported. All changes have seven-language resources.
- RideLabAi diagnostics contain provider, request index, STARTED/FINISHED/CANCELLED,
  elapsed milliseconds, HTTP status and transport problem. They contain no endpoint,
  key, question, SQL, reasoning, response text or ride measurements.

Focused verification: **70 AI/localization unit tests passed, zero failures/errors**;
application and Android-test Kotlin compilation passed. Tests include a real loopback
HTTP regression with six tool results followed by a delayed K3-style SSE final report,
reasoning replay, token usage, fragmented UTF-8/text/tool arguments, slow first byte,
trickle-response deadline, interrupted stream, request versus run timeout and retained
query evidence. Android timeout wording test was compile-checked only. No APK/AAB or
new dependency. Real K3 service latency and phone network cancellation remain pending.

Files for this fix: AiHttpTransport.kt, AiAnalysisWire.kt, new AiChatStreamDecoder.kt;
AiAnalysisEngine.kt; AiAnalysisController.kt, AiAnalysisViewModel.kt, AiAnalysisScreen.kt;
seven strings.xml files; AiAnalysisWireTest.kt, AiAnalysisControllerTest.kt,
HttpAiAnalysisTest.kt, new AiHttpTransportTest.kt / AiChatStreamDecoderTest.kt,
androidTest/AiAnalysisUiTest.kt; this document. SQL/recording/BLE/metrics code is unchanged.

Device retest: run the updated app from Android Studio using the existing saved Kimi
configuration. Ask first for current FTP/weight plus the latest completed ride review,
then retry the original wider question. Check request number/elapsed/report stage,
final report, six-call limit and a single Stop path. If it still fails, capture the
screen including the new specific error and RideLabAi STARTED/FINISHED/CANCELLED lines,
plus model ID and .ai/.cn choice, without the API key. No key reset is required.
