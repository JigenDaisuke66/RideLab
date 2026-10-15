# Localization phase 2

No dependency changes, APK/AAB, BLE/service/workout/Room/analytics implementation changes.
The previous uncommitted UI cleanup is preserved.

Resource coverage: 454 string IDs in each of seven languages. Added values-ja,
values-zh-rTW, values-es, values-pt-rBR and values-fr; retained values (Chinese)
and values-en. Resource IDs, format indexes/types/precision, CSV column order and
resources.properties unqualifiedResLocale=zh-CN are preserved. Android Gradle
automatically generates the seven-language per-app locale configuration. Language
names use native names where useful. Numeric-only/unit/technical resources remain
identical where translation is unnecessary. Traditional Chinese uses Taiwan terms
such as 裝置/訓練台/資料/感測器/位址. Native-speaker review remains recommended.

Selection architecture:
- SettingsUiState exposes AppLanguage, SettingsDestination emits an existing
  SettingsActions.language callback, Activity delegates to AppearanceViewModel.
- AppLocale owns platform/pref integration. LanguagePolicy is pure and tested.
- Fresh install: Follow System; no locale is seeded. Supported regional variants
  map to the supported language (pt-PT to pt-BR; zh-Hant/HK/TW to zh-TW; zh-Hans to
  zh-CN). System lists use the first supported entry, else zh-CN.
- Manual selection always wins, across system changes and process restart.
- Android 13+: only LocaleManager.applicationLocales stores overrides. Clearing
  the list selects Follow System. Android handles persistence/recreation, including
  selection through Android app-language settings. No competing override preference.
- Android 12/12L: existing app_locale/legacy_language stores manual canonical tags;
  absent key is Follow System. Selection removes the key for System; Activity
  recreates only when needed. System changes resolve again on configuration change.
- Upgrade: keep platform choices and platform_initialized marker. If the marker
  is absent and platform override is empty, migrate only an existing legacy key.
  Never overwrite an already initialized platform System choice. Old automatically
  seeded zh-CN cannot be distinguished from a manual choice; retain it to prevent
  losing a user's explicit Chinese preference. Users can choose Follow System.
- Existing rememberSaveable navigation and service ownership are untouched.

Whole-app localization:
- Activity and Compose/dialogs use a resolved localized context even when the
  primary system locale is unsupported. The original overlay polling reads the
  same policy through AppLocale.overlayContext without service changes.
- The service already requests AppLocale contexts for channel/notification strings
  and refreshes notifications on Android configuration changes. On Android 12,
  manual locale changes do not notify the service; LocalizedRideNotification updates
  text/action/channel labels on the existing notification only. It preserves pending
  intents/chronometer and issues no ride, trainer or recording commands.
- CSV array references resolve localized headers in Activity. Exporter remains
  unchanged: UTC timestamps, dot decimal values, raw/null semantics and column order.
- Longer navigation labels may wrap to two lines; ride End button can grow vertically;
  language rows use selectable radio semantics and minimum height rather than fixed
  text height. No screen redesign.

Checks:
- 21 relevant unit tests passed: LanguagePolicyTest (5), TranslationResourcesTest
  (2, all IDs and exact format tokens plus rendering/precision), RideCsvExporterTest
  (14, including all seven header sets under French default locale with dot numbers).
- Resource linking, application Kotlin and Android-test Kotlin compilation pass.
- Existing locale device tests updated for new default; added seven-language manual
  persistence/context/CSV/overlay/notification resource checks. Device tests have
  NOT been run, since this task must not build an APK.
- Existing compile-SDK compatibility/Compose test API deprecation warnings remain.

Physical/manual checks (Android 12 and Android 13+):
1. Clean install with system ja/en/es/fr/pt-BR/zh-TW/zh-CN: correct language and
   Follow System radio selected. German-only system: Chinese. Also test locale lists.
2. Manually select each language. Restart app/process, change device language: manual
   choice remains. Select Follow System: clear override, device language follows again.
3. Upgrade a saved-English/saved-Chinese install. Selection must survive; test a
   legacy Android 12 backup upgraded to Android 13. Android app-language System/English
   choices must be reflected in RideLab settings without reseeding.
4. Start a ride with trainer/HR, switch language, rotate and change tabs/back. Session
   identity, elapsed time and connections must survive; no duplicate Start/ERG command.
5. While ACTIVE and PAUSED/after End with retained devices, switch language. Check
   notification title/text/action/channel and overlay labels, Close and sliders.
   Native per-app external changes and Android 12 manual changes both need checking.
6. Inspect Home, device picker/errors, Ride/ERG, workout preview/progress/results,
   history/detail/analytics/zones, deletion dialogs and CSV success/failure toasts in
   all seven languages, at normal and 130–150% font sizes. Pay attention to French
   and Portuguese labels, landscape, small screens and dialogs.
7. Export a ride in each language. Headers change; numeric rows/UTC dates/empty cells
   stay identical with dots. Historical W/bpm/rpm metrics must display numbers,
   not literal %1$ placeholders.

Files changed for this phase:
- locale/AppLocale.kt; new LanguagePolicy.kt and LocalizedRideNotification.kt
- MainActivity.kt; ui/settings/AppearanceViewModel.kt, SettingsContract.kt,
  SettingsDestination.kt; ui/screen/AppShell.kt and RideControls.kt
- res/values/strings.xml, values-en/strings.xml and five new translation folders
- unit locale/LanguagePolicyTest.kt, TranslationResourcesTest.kt;
  export/RideCsvExporterTest.kt; androidTest LocalizationTest.kt, locale/AppLocaleTest.kt
- AGENTS.md, PROJECT.md, UI_ARCHITECTURE.md, LOCALIZATION_PHASE2.md
