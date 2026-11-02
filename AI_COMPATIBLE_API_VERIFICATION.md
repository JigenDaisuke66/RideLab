# Custom compatible API correction — 2026-10-29

## Confirmed root cause

The screenshot shows HTTP 200, not an Android cleartext refusal. The old probe sent
only `max_tokens=256`. Against the user's existing vLLM server, the identical Chinese
probe (`请只回复 OK。`) was reproduced with:

```text
finish_reason=length
content=null
completion_tokens=256
reasoning characters=912
```

Other runs completed within that budget (181/195 tokens), explaining the intermittent
behavior. The old response parser collapsed truncation, reasoning-only output, empty
content and malformed JSON into RESPONSE. There was no model-list request or visible
assistant reply. Server deployment, Docker and port forwarding were not modified.

GET /v1/models was also checked from the host: id=qwen-local, owned_by=vllm,
root=sanskar003/Qwen3.5-9B-AWQ, max_model_len=32768. Only `data[].id` is used to
validate the selected model; the root name never replaces the model ID.

## Minimal changes

- Custom's explicit Test now performs GET /models, parses exact IDs, and then sends
  POST /chat/completions. A missing ID stops before the chat and shows available IDs.
- Local-provider probes use 2048 output tokens, standard SSE and the existing 120 s
  per-request budget. Other cloud preset probes keep 256 tokens and their previous
  non-streaming/read budgets. Actual assistant content appears in the settings dialog.
- JSON and SSE are both accepted. The existing SSE decoder recognizes `reasoning`
  as well as `reasoning_content`; it never shows thinking as the assistant's answer.
- No model/server/key is hardcoded into production configuration; no vendor-specific
  thinking-disable parameter or automatic retry/fallback was added. Existing saved
  settings remain user-selected and encrypted. Opening/restoring/editing/saving does
  not start requests. Explicit Test is still required.
- `/v1`, `/v1/`, `/v1/models` and `/v1/models/` use the same normalized base. No
  duplicate `/v1`, `/models` or `/chat/completions` is appended for these inputs.
  URL credentials, queries/fragments and unsafe base paths remain rejected.
- Typed errors distinguish application HTTP consent, Android cleartext blocking,
  refusal, timeout, HTTP status, unavailable model, malformed JSON/incomplete SSE,
  output exhaustion, empty content and reasoning only. Known statuses and redacted
  network exception chains are retained. Raw server error bodies, keys, reasoning
  and ride data are not logged. `RideLabAi` logs probe stages/status/problem only.

## Network policy impact

The manifest still declares INTERNET and references network_security_config; no new
permission, dependency, applicationId or signing change was necessary.

To support arbitrary user-selected private IPv4 addresses, Android's static network
configuration now has application-wide `cleartextTrafficPermitted=true`. This is a
broader platform allowance: future code bypassing the guarded transport could use
HTTP. All networking must continue through AiHttpTransport. This change was explicitly
authorized by the current request; it does not change HTTPS certificate verification.

Both configuration and the actual request boundary enforce explicit local-HTTP consent
for Custom/Ollama/LM Studio. HTTP literals must be RFC1918 private IPv4 (10/8,
172.16/12, 192.168/16) or the existing loopback. Public, link-local, CGNAT and ambiguous
numeric encodings are rejected. `.local`/localhost resolution is checked before
transmission: all resolved addresses must be private/loopback. Redirects remain off.
Cloud presets still require HTTPS. Disabling consent prevents both model-list and
chat requests. HTTP is unencrypted and is intended only for trusted local networks.

## Verification actually performed

- `:app:testDebugUnitTest`: **341 tests passed, 0 failures/errors**. Existing BLE,
  FE-C, recording, analytics and other tests are preserved.
- `:app:compileDebugAndroidTestKotlin` and `:app:assembleDebug`: passed.
- Pixel_8a Android 16 emulator: **10 AI settings/analysis UI tests passed** (5 each),
  including explicit-only actions, disabled consent, exact model display and actual
  answer text rendering. Initial runner output is retained in
  app/build/verification/ai-ui-before-live-fix/. Its additional initialization failure
  belonged to the new live test and was corrected by explicitly declaring Unit.
- The corrected live class was then executed directly with ADB after Gradle UTP's
  installer hit an ADB connection timeout: **2/2 passed**. Android's actual HTTP stack
  fetched the user's server model list and completed the selected model's SSE chat.

```text
RideLabAi: Android live test stage=GET models httpStatus=200 problem=null
RideLabAi: Android live test stage=POST chat/completions httpStatus=200 problem=null
RideLabAi: Android live test completed model=qwen-local reply=OK
OK (2 tests)
```

Live runner output: app/build/verification/ai-compatible-android-run.txt.
The tests sent only a synthetic short prompt; they did not read ride data, stored
credentials or change settings. The emulator was started headlessly for these checks.
**No physical phone was connected via ADB; physical-phone App chat is not verified.**
This also does not claim the server's ride-analysis tool-calling behavior was tested.

The APK's packaged network config was inspected and its v2 signature verified:

```text
D:\AndroidStudioProject\RideLab\app\build\outputs\apk\debug\app-debug.apk
61,743,985 bytes
SHA256 A7A72356DE4DE0BA9492AFE597FD88C5C8BA2FD481D1428A05ED04F2B3BEBFD0
package com.isaac.ridelab; versionCode 2; versionName 1.0.1; targetSdk 36
```

## Physical-phone check

1. Install the Debug APK as an update; do not clear app data.
2. AI tab > AI services > Custom compatible API. Enter the already-working base
   `http://192.168.0.4:8001/v1` (or paste its /models link), model `qwen-local`, blank
   key and Allow local HTTP on. These are this test's inputs, not production defaults.
3. Explicitly Test. Expect the server model list containing qwen-local, a success
   message AND the assistant's short reply. Then explicitly Save if desired.
4. Turn Allow local HTTP off: Test/Save must be disabled. Restore it for local use.
5. If unsuccessful, capture the specific error, HTTP status, diagnostic line and
   RideLabAi probe logs. Do not send API keys or full private ride/report content.

No redeployment or port changes are required by this fix.

## Changed implementation files

Paths below are relative to app/src/main/java/com/isaac/ridelab unless specified:

- ai/AiEndpointPolicy.kt (new), AiProvider.kt, AiWireProtocol.kt,
  AiHttpTransport.kt, HttpAiConnectionProbe.kt, AiChatStreamDecoder.kt,
  AiAnalysisWire.kt and ai/analysis/AiAnalysisEngine.kt (error detail only).
- ui/ai/AiSettingsController.kt, AiSettingsViewModel.kt, AiSettingsCard.kt,
  AiAnalysisController.kt and AiAnalysisScreen.kt.
- app/src/main/res/xml/network_security_config.xml and all seven strings.xml files.
- Focused JVM tests: AiEndpointPolicyTest (new), AiProviderTest, AiWireProtocolTest,
  AiHttpTransportTest, HttpAiConnectionProbeTest, AiAnalysisWireTest,
  AiSettingsControllerTest and HttpAiAnalysisTest.
- Android tests: AiSettingsUiTest and new ai/CompatibleApiInstrumentedTest.
- AI_SERVICES.md, AI_ANALYSIS.md and this document.

No BLE/FE-C/service, Room schema, recording, workout or analytics formulas changed.
Existing compile-SDK/AGP compatibility and deprecated Compose test API warnings remain.

Protocol references: [Qwen vLLM deployment](https://qwen.readthedocs.io/en/latest/deployment/vllm.html),
[vLLM reasoning outputs](https://docs.vllm.ai/en/latest/features/reasoning_outputs/),
[Android network security configuration](https://developer.android.com/privacy-and-security/security-config).
