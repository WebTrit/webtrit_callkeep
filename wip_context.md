# WIP Context — fix/standalone-mode-no-telecom

Date: 2026-03-27

## Мета гілки

Виправити падіння інтеграційних тестів на пристрої HA1SVX8G (Lenovo TB300FU, Android 13, API 33)
який не має `android.software.telecom`. Всі дзвінки роутяться через `StandaloneCallService`.

---

## Статус тестів

| Script | Status |
|--------|--------|
| `tools/run_callkeep_client_scenarios.sh` | PASSED |
| `tools/run_callkeep_connections.sh` | PASSED |
| `tools/run_callkeep_delegate_edge_cases.sh` | PASSED |
| `tools/run_callkeep_foreground_service.sh` | PASSED |
| `tools/run_callkeep_lifecycle.sh` | PASSED |
| `tools/run_callkeep_reportendcall_reasons.sh` | PASSED |
| `tools/run_callkeep_state_machine.sh` | PASSED |
| `tools/run_callkeep_stress.sh` | PASSED |
| `tools/run_callkeep_call_scenarios.sh` | PASSED |
| `tools/run_callkeep_background_services.sh` | **IN PROGRESS** |

---

## Вже застосовані фікси (закомічено в попередніх комітах)

### Root cause 1 — StandaloneCallService в :callkeep_core процесі

`StandaloneCallService` був задекларований з `android:process=":callkeep_core"` в
`AndroidManifest.xml`. На Lenovo TB300FU OEM `bringUpServiceLocked` позначав вторинні
процеси як "bad", блокуючи всі наступні старти сервісу.

**Fix**: прибрано `android:process=":callkeep_core"` з `StandaloneCallService`,
`foregroundServiceType` змінено з `phoneCall` на `microphone`.

### Root cause 2 — OEM broadcast suppression

Lenovo TB300FU має кастомний `ActivityManagerService.broadcastIntentWithFeature()` який
пригнічує ВСІ `sendBroadcast` виклики з застосунку. `sendBroadcast` завжди йде через
`system_server` (PID 1191) навіть для компонентів в одному процесі — через Binder IPC.
OEM перехоплює виклик на рівні AMS до того як intent потрапляє в чергу доставки.

Logcat доказ (під час failing тесту):

```
06:52:07.887  FlutterEngineHelper(12257): FlutterEngine initialized and attached successfully
06:52:08.388  ActivityManager(1191): broadcastIntentWithFeature, suppress to broadcastIntent!
// ...18 разів з інтервалом ~512ms (це polling loop тесту)...
06:52:17.747  FlutterEngineHelper(12257): FlutterEngine detached and destroyed
```

**Fix**: додано in-process delivery до `ConnectionServicePerformBroadcaster`:

- `inProcessReceivers: ConcurrentHashMap<BroadcastReceiver, List<String>>`
- `mainHandler = Handler(Looper.getMainLooper())`
- `registerInProcessReceiver()` / `unregisterInProcessReceiver()`
- `deliverInProcess()` — `Handler.post` прямий виклик `receiver.onReceive()`, без AMS
- `localHandle` — `DispatchHandle` що використовує `deliverInProcess` замість `sendBroadcast`

`StandaloneCallService` змінено з `handle` на `localHandle`.
`ForegroundService` реєструє своїх receivers і в системному broadcast, і в in-process registry.

---

## Поточна проблема — run_callkeep_background_services.sh

### Що тестує цей файл

`callkeep_background_services_test.dart` — дві групи тестів:

1. **Push notification background service** — симулює FCM-triggered дзвінки через
   `PushNotificationIsolateManager`. Тестує: дублікати, конкурентні виклики, endCall,
   endCalls, tearDown, answered/terminated states.

2. **Background signaling service** — симулює `SignalingForegroundIsolateManager`.
   Стартує реальний `SignalingIsolateService` + background Flutter engine. Тести
   комунікують з background isolate через `IsolateNameServer` SendPort. Background
   isolate викликає callkeep API (Pigeon) від свого імені.

### Де падає

`_startSignalingServiceAndAwaitPort()` — helper що поллить `IsolateNameServer` 10 секунд.

Chain:

```
updateActivitySignalingStatus(disconnect)
  -> ConnectionsApi (Pigeon)
  -> SignalingStatusBroadcaster.setValue()
  -> sendInternalBroadcast("SignalingStatusBroadcaster.SIGNALING_STATUS")
  -> AMS (system_server PID 1191)
  -> [OEM suppress]
  -> SignalingIsolateService.signalingStatusReceiver -- NEVER fires
  -> synchronizeSignalingIsolate() -- NEVER called
  -> onWakeUpBackgroundHandler -- NEVER called
  -> background isolate port -- NEVER registered
  -> timeout after 10s
```

### Додаткова проблема (ширше)

Навіть якщо `signalingStatusReceiver` отримає подію (після фіксу),
`SignalingIsolateService.endCall()` і `endAllCalls()` також реєструють receiver через
`registerConnectionPerformReceiver` (системний broadcast). `StandaloneCallService` вже
відправляє через `localHandle` (in-process). Цей receiver ніколи не отримає подію —
завжди буде 5-секундний timeout.

---

## Дискусія про архітектурне рішення (незавершена)

### Поточна реалізація — ConcurrentHashMap + Handler.post

Вже застосована для `ConnectionServicePerformBroadcaster`. Працює, але:

- Два паралельні канали (системний broadcast + in-process registry) для однієї події
- Ручний register/unregister — ризик витоку пам'яті якщо забути
- Шаблонний код: `AtomicBoolean`, `Handler`, `lateinit var receiver`, `snapshot`

### Альтернатива — SharedFlow (Kotlin Coroutines)

```kotlin
object SignalingStatusBus {
    private val _flow = MutableStateFlow<SignalingStatus?>(null)
    val flow: StateFlow<SignalingStatus?> = _flow.asStateFlow()
    fun emit(value: SignalingStatus) { _flow.value = value }
}
```

Підписник:

```kotlin
private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
// в onCreate:
serviceScope.launch { SignalingStatusBus.flow.filterNotNull().collect { ... } }
// в onDestroy:
serviceScope.cancel()
```

Переваги SharedFlow над поточною реалізацією:

- Один канал замість двох (немає питання "через AMS чи ні")
- Scope скасовує підписку автоматично — немає ризику витоку
- Type-safe (Kotlin-об'єкт замість Bundle)
- `endCall` стає: `withTimeoutOrNull(TIMEOUT) { flow.first { it.callId == callId } }`
  замість `AtomicBoolean + Handler + lateinit var + ручний unregister`
- Thread safety вбудований в coroutines

Мінус: потребує `CoroutineScope` в сервісах (manual scope — без `LifecycleService`).

### Не вирішено

Чи використовувати `SharedFlow` для:

- Тільки `SignalingStatusBroadcaster` (мінімальна зміна)
- І `SignalingStatusBroadcaster` і `ConnectionServicePerformBroadcaster` (повний рефакторинг)
- Чи залишити поточний патерн і просто розширити його на `SignalingIsolateService`

---

## Файли змінені в поточному робочому дереві (unstaged)

| Файл | Зміна |
|------|-------|
| `.gitignore` | додано виключення для worktrees |
| `webtrit_callkeep/example/run_integration_tests.sh` | оновлено скрипт |
| `AndroidManifest.xml` | прибрано process з StandaloneCallService |
| `ConnectionServicePerformBroadcaster.kt` | in-process registry + localHandle |
| `StandaloneCallService.kt` | localHandle замість handle |
| `ForegroundService.kt` | registerInProcessReceiver в onCreate/onDestroy/startCall/tearDown |

`SignalingStatusBroadcaster.kt` та `SignalingIsolateService.kt` — НЕ змінені,
фікс відкатаний, очікує архітектурного рішення.
