# Сборка и проверка

Как собрать оба проекта и прогнать проверки Фазы 0. Часть проверок я уже прогнал в песочнице
(Python), часть требует твоего окружения (Java-сборка невозможна там, где я работаю: нет JDK 17/25,
нет сети для скачивания зависимостей Gradle).

## Что уже проверено (в песочнице, зелёное)

```
freecam-pvp-collector/python$ python verify_schema.py
  OK  names+order match (16 features)
  OK  version match (combat.v1)
  OK  neutral defaults match
  OK  onnx input width = 16 (float_input)
  RESULT: PASS

freecam-pvp-collector/python$ python run_scenarios.py
  blatant_killaura_cheat   CHEAT  P(cheat)=0.425   HARD_SNAP               OK
  corner_360_crit_legit    LEGIT  P(cheat)=0.000   HARD_SNAP,HITBOX_MISS   OK
  normal_combat_legit      LEGIT  P(cheat)=0.145   -                       OK
  reach_lagspike_legit     LEGIT  P(cheat)=0.280   REACH                   OK
  RESULT: PASS
```

Ключевой вывод: модель на **правильной знаковой схеме** даёт кейсу «360° в углу» P(cheat)=**0.000** —
значит ложное срабатывание там идёт целиком от синхронного hard-check, а не от ИИ. Даже явная killaura
= 0.425 < порога 0.75 → ИИ обязан быть одним сигналом, а не решающим гейтом.

## Проверки на твоей стороне (нужен JDK + сеть)

### Anticheat (Purpur, Java 17)
```bash
cd purpur-ai-anticheat
./gradlew clean test        # прогонит FeatureSchemaTest + ScenarioResourcesTest (JUnit 5)
./gradlew build             # соберёт shadowJar с моделью
```
Ожидаемо: оба тест-класса зелёные. `ScenarioResourcesTest` грузит те же JSON-фикстуры, что и
`run_scenarios.py`, и проверяет, что каждая фикстура схемо-корректна и что регресс-кейс
`corner_360_crit_legit` помечен LEGIT.

### Collector (Fabric, Java 25)
```bash
cd freecam-pvp-collector
./gradlew build
```
В Фазе 0 код коллектора не менялся (только `python/`), так что сборка должна пройти как раньше.

### Python-пайплайн (где есть sklearn)
Схему обучения я НЕ трогаю до готовности Фазы 2 (по твоему требованию — не переобучать модель до
фиксации feature schema). Но проверить, что тренер видит единую схему, можно уже сейчас:
```bash
cd freecam-pvp-collector/python
python verify_schema.py       # sanity: Java == Python == ONNX
python run_scenarios.py       # sanity: фикстуры прогоняются через схему + модель
```

## Замечание о верификации в песочнице (честно)

`javac`/Gradle в моём окружении недоступны и нет сети → **JUnit-тесты я локально не запускал**. Их логика
выверена статически; данные схемы (имена/порядок/дефолты/версия) сверены машинно через `verify_schema.py`,
который парсит реальный `FeatureSchema.java`. Первый реальный прогон `./gradlew test` — на твоей стороне.

---

# Фаза 1 — устранение ложных срабатываний (Risk Engine)

Логика решения переведена с «hard-check → отмена урона → кик» на мандатную схему
**«сигнал → контекст → Risk Engine → решение»**. ИИ теперь — **один** сигнал с низким доверием, а не гейт.

## Новые / изменённые классы (Anticheat)

Чистое ядро (без Bukkit, полностью юнит-тестируется):
- `engine/Signal.java`, `engine/SignalType.java` — типизированные сигналы + базовые веса.
- `engine/CombatContext.java` — ситуативный контекст (угол, стена, отбрасывание, пинг, крит, смена цели,
  повтор паттерна, знаковое ускорение).
- `engine/ContextEngine.java` — мультипликативные модификаторы + лаг-компенсация (скалярная модель).
- `engine/RiskEngine.java` — `сигнал → вес → модификаторы → уверенность → агрегация → риск`.
- `engine/RiskAssessment.java` — объяснимый результат (вердикт + разбивка по сигналам).
- `engine/RiskAccumulator.java` — рантайм-агрегатор риска на игрока (экспоненциальный полураспад 30 с).

Рантайм (переписан):
- `engine/HardCombatChecks.java` — **преобразован**: `evaluate()` теперь возвращает `List<Signal>` +
  `CombatContext` + защитный флаг `cancelDamage` (отмена урона только при лаг-компенсированно-достоверном
  реаче; это не наказание, а обратимая защита). Больше ничего не кикает и не банит.
- `engine/LagCompensator.java` — **реальная** лаг-компенсация: буфер позиций жертвы, реач сверяется с
  «перемотанной» позицией в окне пинга.
- `engine/InferenceService.java` — здесь принимается решение (модель доступна в async): собирает вклады,
  агрегирует по времени, строит `DetectionSnapshot` из `RiskAssessment`.
- `tracker/CombatTracker.java` — состояние паттернов (смена цели / серия роботичных снапов),
  `RiskAccumulator`, `applyRisk()`.
- `listener/CombatListener.java` — **удалён синхронный блок наказания**; в `onMove` пишет позиции для
  лаг-компенсации; наказание только при `isActionable()` (вердикт CHEAT), алерт — при SUSPICIOUS+.
- `engine/DetectionSnapshot.java` — добавлены `verdict` / `riskScore` / `reasons`; `isActionable()` = CHEAT.

## Что уже проверено (в песочнице, зелёное)

```
freecam-pvp-collector/python$ python risk_engine.py
  blatant_killaura_cheat   expected=CHEAT   got=CHEAT   risk=100.00  event=35.03
  corner_360_crit_legit    expected=LEGIT   got=LEGIT   risk= 0.74   event= 0.74
  normal_combat_legit      expected=LEGIT   got=LEGIT   risk= 0.00   event= 0.00
  reach_lagspike_legit     expected=LEGIT   got=LEGIT   risk= 0.28   event= 0.28
  RESULT: PASS (все вердикты совпали)
```

Регресс-кейс «360° в углу с критом» = **LEGIT** (риск 0.74 из 100) на сигналах+контексте при ИИ=OFF.
Явная killaura достигает CHEAT (100) только за счёт **временной агрегации** повторяющихся роботичных снапов —
единичное событие даёт 35 (SUSPICIOUS), наказания за один хит нет.

## Проверки на твоей стороне (нужен JDK + сеть)

```bash
cd purpur-ai-anticheat
./gradlew clean test        # + RiskEngineTest (пины точных чисел, все с modelPCheat=0)
./gradlew build
```

`RiskEngineTest` сверяет **бит-в-бит** числа из `risk_engine.py`:
- `cornerThreeSixtyCritResolvesToLegit_withAiOff` — HARD_SNAP=0.259875, HITBOX_MISS=0.476, риск=0.735875, LEGIT;
- `blatantKillauraReachesCheat_withAiOff` — event=35.025, риск=100.0, CHEAT (HARD_SNAP=10.125 / AIM=14.4 / KINEMATIC=10.5);
- `aiModelAloneCannotReachCheatOrSuspicious` — p=1.0 → риск=15.0 → LEGIT (ИИ никогда не гейт);
- `lagCompensationNeutralizesReachUnderPingAndKnockback` — 3.42м при 150мс+отбрасывании → excess=0;
- три теста на `RiskAccumulator` (единичное событие, полураспад, стек повторов за CHEAT-порог).

Если хоть одно число разойдётся с Python — сборка красная (это и есть страховка от рассинхрона портов).

## Калибровка (config.yml → `detection.calibration`)

```yaml
detection:
  calibration:
    suspicious_threshold: 25.0   # риск ≥ этого → SUSPICIOUS (только алерт)
    cheat_threshold: 60.0        # риск ≥ этого → CHEAT (наказание)
```
Выше пороги = мягче, ниже = строже. Пороги читаются в `InferenceService` при старте.

---

# Фаза 2 — устранение train/serve skew (модель видит ровно то, на чём обучалась)

Модель обучали на данных коллектора (каноничный порядок, **знаковые** yaw/pitch, честный `raycast_hit`),
а на сервере ей скармливали **искажённый** вектор. Это классический train/serve skew — главная скрытая
причина завышенной подозрительности легитимных игроков. Три подтверждённых бага в старом пути инференса:

1. **`Math.abs()` на знаковых слотах 2–9** — уничтожал знак `yaw_accel`. А знак — это *самый*
   различающий признак: −84 (замедление, человеческий флик, доводит прицел до цели) превращался в
   +84 (ускорение, сигнатура killaura). Легитимный флик выглядел как чит.
2. **Захардкоженный `raycast_hit = 1.0`** — модель **никогда** не видела промах луча, хотя в обучающих
   данных промахи есть.
3. **Перепутанные слоты** — `raycast_distance` попадал в слот `distance`, а неканоничный `distanceMean`
   — в слот `raycast_distance`; угол дублировался.

## Что исправлено (Anticheat)

- `engine/FeatureVector.java` — **полностью переписан**. Убраны старый `getInferenceInput16()` (с
  `Math.abs`, хардкодом и перепутанными слотами) и 18-аргументный `createV1`. Новый `createV1` принимает
  ровно 16 каноничных признаков, кладёт их **со знаком** в `LinkedHashMap` по ключам `FeatureSchema.NAMES`,
  и **единственный** путь сборки вектора — `FeatureSchema.assemble(...)`. `getInferenceInput16()` теперь
  просто делегирует в `assemble` → серверный вектор физически не может разойтись со схемой обучения.
- `tracker/CombatTracker.createFeatureVector(...)` — считает каноничные значения так же, как коллектор:
  `distance` = глаз-в-глаз; `angle_offset_deg` = угол до **глаз** цели; `raycast_hit` = реальный результат
  луча; `raycast_angle_error` = **0 на попадании**, иначе угловой промах до центра бокса (правило коллектора).
  Убраны неканоничные входы (`attackIntervalMean/StdDev`, `distanceMean/StdDev`, `ping`) — их в 16 признаках
  никогда не было, и именно `distanceMean` был багом слота 11.
- `alert/AlertManager.java` — вебхук читает каноничные ключи `raycast_distance` и `angle_offset_deg`
  (раньше — camelCase `raycastDistance` / `targetAngleDeg`, которых в новой карте нет).

## Что уже проверено (в песочнице, зелёное)

Один и тот же физический удар (легитимный «360° в углу» с критом) прогнан через **реальную** ONNX-модель
двумя способами — старым (искажённым) и новым (каноничным):

```
freecam-pvp-collector/python$ python verify_serving_skew.py
  (A) OLD skewed serving     yaw_accel=  +84.0  raycast_hit=1  ->  P(cheat)=0.160  [LEGIT]
  (B) NEW canonical serving  yaw_accel=  -84.0  raycast_hit=0  ->  P(cheat)=0.000  [LEGIT]
  P(cheat) reduction from fixing the skew: +0.160
```

Честно про масштаб: на этом baseline-RandomForest оба варианта пока ниже порога, но перекос давал
**+0.160** «паразитной» вероятности чита на *легитимном* действии. Именно такая добавка складывается с
другими сигналами и с временным накоплением серии — и однажды перешагивает порог (ложный бан). После
фиксы модель читает флик как человеческий (P=0.000). Это одна детерминированная инференс-пара, не бенчмарк.

## Проверки на твоей стороне (нужен JDK + сеть)

```bash
cd purpur-ai-anticheat
./gradlew clean test        # + FeatureVectorServingTest (5 тестов пути инференса)
./gradlew build
```

`FeatureVectorServingTest` фиксирует, что skew не вернётся:
- `servingVectorIsCanonicalSignedAndHonest` — знаки целы (`yaw_accel` = −84, не +84; флик 361° не
  оборачивается/не клампится), `raycast_hit` = 0 на промахе (не захардкоженная 1.0), верное соответствие
  слотов (`distance` / `raycast_distance` / `angle_offset_deg` / `raycast_angle_error`), длина 16,
  `FeatureSchema.validate()` == null;
- `cleanHitEncodesRaycastHitTrue` — на попадании `raycast_hit` = 1.0, `raycast_angle_error` = 0.0;
- `servingVectorMatchesRawValues` — `getInferenceInput16()` бит-в-бит равен `rawValues()` (одна сборка);
- `namedFeaturesUseCanonicalKeys` — карта на каноничных snake_case ключах, старые camelCase удалены
  (стейл-читатель падает громко, а не молча берёт дефолт).

