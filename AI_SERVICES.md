# AI services: optional connection setup and analysis

Provider configuration and the explicit connection probe are now joined by the
user-requested read-only SQL analysis screen. See **AI_ANALYSIS.md** for the current
data interface, four bundled analyst skills, permissions and token budgets. No MCP,
SQL writes, trainer tools or automatic coaching are implemented. Offline riding,
BLE ownership, workouts, recording, Room schema and exports are unchanged. No account,
backend or AI configuration is required to ride. The probe-specific behavior below
still applies to Test connection; starting analysis separately sends queried ride data.

## Open-source evaluation (2026-10-25)

- [Koog](https://github.com/JetBrains/koog), Apache-2.0: Kotlin framework with Android,
  multiple model clients and tool/agent orchestration. A good candidate if a future
  approved scope needs agent tools. A framework is unnecessary for this settings/probe slice.
- [openai-kotlin](https://github.com/aallam/openai-kotlin), MIT: coroutine/multiplatform
  client for OpenAI APIs. Useful for compatible providers; native Claude/Gemini still
  need their own support. Would introduce Ktor/client dependencies into this app.
- [RikkaHub](https://github.com/rikkahub/rikkahub), AGPL-3.0: complete Android/Compose
  multi-provider chat application, useful as a product reference, not a drop-in SDK.
  No code/assets were copied from it or from the other evaluated projects.

Decision: use Android HttpURLConnection, platform org.json, coroutines and Android
Keystore for this limited increment. No new production dependencies. JSON-java
20240303 is added only to JVM tests because Android's org.json is a stub there.
Keep AiConnectionProbe replaceable; avoid adding an agent framework until needed.

## Presets and transport

All base URLs are editable for the account's region/plan. Enter a **base URL**,
not the full final chat endpoint. No model ID is silently chosen: users must enter
the complete ID supported by their account or loaded in their local server.

| Provider | Default base URL | Wire protocol |
| --- | --- | --- |
| OpenAI | https://api.openai.com/v1 | Chat Completions |
| Anthropic / Claude | https://api.anthropic.com/v1 | Messages |
| Google / Gemini | https://generativelanguage.googleapis.com/v1beta | generateContent |
| DeepSeek | https://api.deepseek.com | Chat Completions |
| Qwen / DashScope | https://dashscope.aliyuncs.com/compatible-mode/v1 | Chat Completions |
| Kimi / Moonshot | https://api.moonshot.ai/v1 | Chat Completions |
| GLM / Zhipu | https://open.bigmodel.cn/api/paas/v4 | Chat Completions |
| SiliconFlow | https://api.siliconflow.cn/v1 | Chat Completions |
| OpenRouter | https://openrouter.ai/api/v1 | Chat Completions |
| Ollama | http://127.0.0.1:11434/v1 | Compatible Chat Completions |
| LM Studio | http://127.0.0.1:1234/v1 | Compatible Chat Completions |
| Custom | User supplied | Compatible Chat Completions |

Official references checked:
[OpenAI](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create),
[Claude](https://platform.claude.com/docs/en/api/messages/create),
[Gemini](https://ai.google.dev/api/generate-content),
[DeepSeek](https://api-docs.deepseek.com/api/responses),
[Qwen regional endpoints](https://help.aliyun.com/en/model-studio/base-url),
[Kimi](https://platform.kimi.ai/docs/get-your-api-key),
[GLM](https://docs.bigmodel.cn/cn/guide/develop/openai/introduction),
[SiliconFlow](https://docs.siliconflow.cn/docs/userguide/quickstart),
[OpenRouter](https://openrouter.ai/docs/api/api-reference/chat/create-a-chat-completion),
[Ollama](https://docs.ollama.com/api/openai-compatibility),
[LM Studio](https://lmstudio.ai/docs/developer/openai-compat).
These are protocol presets, not a claim that every model/account/subscription works.
Chat website subscriptions or coding-plan endpoints are not interchangeable with API keys.
Kimi now has explicit .ai / .cn chips (https://api.moonshot.cn/v1 for China).
They change only the editable base URL; the model/key remain in the draft and must
match the account region. Save remembers the selected URL. The regions do not gain
shared account credentials merely because the app can select both endpoints.

## State, storage and networking

- MainActivity creates an Activity-scoped AiSettingsViewModel. AppShell forwards
  an independent AI bottom-tab content slot containing analysis and provider settings;
  no existing ride callback is redirected.
- AiSettingsRoute collects immutable state. AiSettingsCard renders it and emits
  typed field edits plus Save/Test/Forget. Draft keys are never put in a Bundle.
- AiSettingsController owns loaded profiles, validation, save/test results and
  single-flight operations. Switching provider discards unsaved edits (stated in UI).
  Save persists only that provider's draft and the selected provider; other profiles
  are retained. Test uses the current draft but does not save it. Neither action
  confers permission to control a trainer. Restore/open/save never sends a request.
- EncryptedAiSettingsStore stores all profiles as AES-256-GCM ciphertext in
  noBackupFilesDir/ai-providers.bin using an Android Keystore key. AtomicFile prevents
  torn writes; authentication detects tampering. Android backup/device transfer and
  RideLab CSV/.ridelab exports do not include this file. Reinstallation requires keys
  to be entered again. This protects storage, not a compromised/rooted device.
- A read/decryption failure is visible and blocks overwriting unseen profiles.
  Explicitly confirmed Reset can replace all AI settings. Forget normally removes
  only the selected provider. Errors never expose raw response text or credentials.
- INTERNET is a normal permission; no new runtime prompt. Target SDK remains 36.
  Network security config defaults to HTTPS, with HTTP exceptions only for localhost,
  127.0.0.1, Android emulator host 10.0.2.2 and *.local names. The controller additionally
  requires explicit Allow local HTTP and Ollama/LM Studio/Custom selection. Ordinary
  numeric LAN addresses require HTTPS; never enable cleartext globally. No certificate
  validation bypass or redirects. The user-supplied endpoint receives its supplied key.
- A physical phone's 127.0.0.1 is the phone, **not the PC**. For a PC server use an
  actually resolvable PC-name.local, enable LAN listening/firewall access, and use the
  same trusted LAN. This depends on the device/network's mDNS support. Alternatively
  use an HTTPS server with a trusted certificate. Merely typing name.local does not
  configure or advertise the PC. Local models must already be installed and loaded.
- Explicit Test sends only a short localized "reply OK" probe. No ride/profile/sample
  data is read or sent. A real provider generation may incur API charges. Three wire
  adapters use the selected model and a 256-output-token budget, no tools, streaming
  or temperature assumption; OpenAI uses max_completion_tokens and store=false.
- Success requires a nonempty assistant text, not just HTTP 200 or reasoning/tool-only
  output. Some reasoning models need a larger budget or other parameters and may fail
  this small probe. No automatic parameter fallback or retries. Failures retain typed
  authentication/rate/HTTP/network/timeout/response states and a status code when known.
- Connect timeout 10 s; read timeout 30 s; body reading has an additional 30 s deadline
  and 64 KiB limit. Cancellation prevents stale results; blocking network stages finish
  at their socket timeout. No polling/background work, ride services or GATT objects.

## Verification

Run only unit tests and compile checks, not assemble/bundle/package APK tasks:

```text
:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest
--tests '*AiProviderTest' --tests '*AiWireProtocolTest'
--tests '*AiSettingsControllerTest' --tests '*HttpAiConnectionProbeTest'
--tests '*TranslationResourcesTest'
```

Unit tests cover presets/validation, native protocol request/response shapes, key
redaction, per-provider persistence through a fake store, no automatic requests,
duplicate-click prevention, draft/saved separation, reset/forget/errors and real
loopback HTTP responses/redirect rejection/bounded bodies. Android tests cover UI
callbacks/disabled input and Keystore encrypted round-trip/tamper rejection. Those
device tests are compile-checked only; no APK or real API credentials are used.

Manual acceptance (pending):
1. Open the AI bottom tab > AI services while no BLE devices are connected. Check all seven
   languages, a small screen/large font and the provider menu.
2. Select a provider; enter the account's correct region URL, chat model ID and key.
   Opening/editing must produce no traffic. Tap Test: spinner, then real success or
   localized failure/status; rapid taps must not create duplicate requests.
3. Save A, then configure and save B. Switch back to A and restart the app: both
   credentials/models retained, last **saved** provider selected, no automatic request.
   Change fields without saving, switch away/back: stored values restored.
4. Wrong key should show authentication failure; wrong model/endpoint should fail,
   not report success from a 200 login page. Never send real keys to an untrusted URL.
5. With Ollama/LM Studio on a PC, configure its working .local address and opt in to
   HTTP. Confirm an installed model responds. Disable HTTP opt-in: Test is disabled.
   Check a non-local HTTP host/IP is rejected. Use HTTPS if .local is unavailable.
6. Forget A with confirmation: B remains. Cancel forget: unchanged. Check credentials
   do not appear in Logcat, CSV, .ridelab or Android backup. Keystore tests need device execution.
7. During an active trainer/HR ride, open settings, test a connection, close, background
   and return: ride/ERG/recording should be unaffected. No new BLE/control path exists.

Results and actual provider/device verification must be distinguished. Without user
keys and model servers, provider connectivity is **not physically verified**.

Executed result (2026-10-25): **28 focused unit tests passed**, zero failures/errors:
AiProviderTest 6, AiWireProtocolTest 7, AiSettingsControllerTest 8,
HttpAiConnectionProbeTest 5, TranslationResourcesTest 2. Application Kotlin and
Android-test Kotlin compilation passed. No APK/AAB was built. Existing compile-SDK
compatibility and Compose test API deprecation warnings remain; provider/device
and Keystore hardware acceptance are still pending.

## Files for this increment

- New ai/: AiProvider.kt, AiWireProtocol.kt, HttpAiConnectionProbe.kt,
  EncryptedAiSettingsStore.kt.
- New ui/ai/: AiSettingsController.kt, AiSettingsViewModel.kt, AiSettingsCard.kt.
- MainActivity.kt, AppShell.kt, SettingsDestination.kt: additive settings slot/wiring.
- AndroidManifest.xml, new xml/network_security_config.xml; app/build.gradle.kts
  (test-only JSON); seven strings.xml files (48 IDs each).
- Four new JVM test files in test/.../ai; AiSettingsUiTest and
  ai/EncryptedAiSettingsStoreTest in androidTest.
- This document, PROJECT.md, UI_ARCHITECTURE.md.
