# STEPWISE --- Полный план разработки

## Промты, ожидаемые результаты, проверки и критерии завершения

**Версия:** 1.0 · **Дата:** 13 ноября 2026 · **Репозиторий:**
`ntegas/Stepwise`\
**Текущий статус по предоставленному отчёту:** DEV-000--DEV-005
завершены; следующий --- DEV-006.

> Этот документ --- подробная рабочая спецификация. Он не подтверждает
> реализацию этапов. Репозиторий, код, тесты, CI, ADR и
> `PROJECT_STATE.md` --- источники истины. Не использовать
> термин/концепцию MVP: Stepwise разрабатывается как полноценное
> приложение.
>
> Добавлен в репозиторий как справочный playbook: `/ROADMAP.md`
> остаётся единственным авторитетным источником порядка DEV-задач и
> чекпоинтов (см. его собственную формулировку этой иерархии). Этот
> файл детализирует промты/expected results/verification criteria по
> каждой задаче — при расхождении с `/ROADMAP.md` или фактическим
> состоянием репозитория побеждает `/ROADMAP.md`/код/`PROJECT_STATE.md`,
> а расхождение не устраняется молча (`/ANTI_ERROR_STANDARD.md` §4).

# 1. Постоянный мастер-промт Claude

Ты --- основной инженер Stepwise. Выполняй этапы последовательно по
`/ROADMAP.md` и этому документу. Не останавливайся после каждого
небольшого изменения и не запрашивай подтверждение обычных технических
решений. Останавливайся только при существенном
продуктовом/архитектурном конфликте, риске потери данных, security
blocker, необходимости production credentials/publication или решении
владельца.

Работай только в `ntegas/Stepwise`. Перед каждым изменением изучай
актуальные файлы, версии, зависимости, ADR, consumers и тесты. Не
переписывай работающие компоненты без доказанной необходимости. Не
дублируй сервисы, модели, расчёты, enum, helpers или UI-компоненты. Не
расширяй scope. Не удаляй/ослабляй падающие тесты. Не делай force-push,
history rewrite, delete/merge без полномочий.

## Цель продукта

Stepwise связывает долгосрочное направление жизни с ежедневным
действием: **MY VISION → LIFE AREAS → GOALS → MILESTONES → PROJECTS /
PLANS → TASKS / ACTIVITIES / HABITS → TODAY → EXECUTION → PROGRESS →
ANALYSIS → ADJUSTMENT → ACHIEVEMENT.**

Ежедневный цикл: **Посмотреть → Выполнить → Отметить.**\
Навигация: **TODAY \| GOALS \| PLAN \| PROGRESS** + глобальный **+ Quick
Add**.\
Обещание: "Turn your goals into action." / «Сложная система внутри.
Простое действие снаружи».

Stepwise --- не CRM и не networking app. Не добавлять контакты/CRM.

## Стек

Нативный Android: Kotlin, Jetpack Compose, Material 3, Room,
ViewModel/StateFlow, Coroutines, DataStore, WorkManager, Navigation
Compose, Hilt, Supabase/Postgres, FCM при необходимости, Google Play
Billing, Android Home Screen Widgets. Offline-first.

## Канонические сущности и инварианты

-   Activity --- продолжающаяся деятельность; Goal --- целевой
    результат; Session --- фактически выполненное событие.
-   Habit --- recurring behavioral rule, не дубль Activity; выполнение
    Habit создаёт Session через общий execution pipeline.
-   Task --- конкретное действие; Project/Milestone --- необязательная
    структура сложных целей.
-   Activity может существовать без Goal и после завершения Goal.
    Activity↔Goals --- many-to-many.
-   Одна Session может содержать несколько метрик и влиять на несколько
    Goals через явное metric mapping.
-   Session/execution --- канонический факт. `Task.Actual` и Goal
    progress --- derived, не отдельный source of truth.
-   Edit/delete Session корректно пересчитывает downstream results. Один
    реальный action --- один канонический факт.
-   Идемпотентность защищает от double tap, retries, widget replay и
    sync replay.
-   Критические локальные изменения атомарны: occurrence state +
    Session + Outbox, где применимо.
-   Execution statuses: Done, Partial, Missed/Not Done, Rescheduled, In
    Progress, Cancelled. Done default Actual=Planned; Partial фиксирует
    Actual; Rescheduled не считается missed в исходную дату; Cancelled
    исключается из знаменателя, где уместно.
-   Goal progress modes: METRIC, TARGET_VALUE, FREQUENCY, MILESTONES,
    TASKS, MANUAL. Для project percentage: Weighted Milestones →
    Weighted Tasks fallback → Manual только по явному выбору.
-   Единый Recurrence Engine для Task/Activity/Habit:
    daily/weekly/weekdays/every N days/monthly/custom, start/end,
    exceptions, skip, reschedule. Lazy materialization и stable
    occurrence identity.
-   Timer опционален; ручное создание Session всегда доступно.
-   Life Balance измеряет внимание/действия, не объективное качество
    жизни. Различать Attention, Execution, Goal Progress, Trend.
-   Skills только вторичная аналитика, где полезно. Запрещены Skill
    Graph, RPG levels, unlock mechanics без нового явного решения
    владельца.

## Source of truth, offline и sync

Room --- локальный operational source of truth для UI; backend
хранит/синхронизирует канонические данные. Агрегаты ---
derived/materialized, не authoritative counters. Не использовать
universal LWW: - новые Sessions с разными stable IDs merge; - Session
edits --- revision/version; - deletes --- tombstones; - low-risk scalar
metadata может использовать version/LWW; - concurrent edits одной
Session требуют явной политики; - независимые offline Sessions с двух
устройств сосуществуют; - stale device не должен воскресить удалённую
запись.

## Централизация

Единая реализация для логики, которая иначе может дать разные ответы:
Design System, navigation/deep links, domain/data/UI models,
repositories, Execution Engine, Calculation Engine, Today Aggregator,
Recurrence Engine, Timer Engine, Clock/date-time, metrics/units,
validation/errors, Sync Engine, transactions/concurrency,
background/reminders, permissions/auth, billing/entitlements,
environments/secrets, dependency catalog, DB migrations, derived cache
invalidation, performance/security/privacy, accessibility/localization,
тесты, CI/observability, export/import/backup, ADR/docs. Не создавать
пустые модули и speculative abstractions.

## Security by design

Android client --- недоверенная среда. Supabase RLS deny-by-default для
каждой exposed user-owned table; тестировать SELECT/INSERT/UPDATE/DELETE
и User A→User B, unauthenticated. Backend проверяет auth/ownership. Не
доверять client userId/ownerId/role/premium/entitlement/serverVerified.
Service-role key никогда не в APK/repo/BuildConfig/logs/AI context.
Секреты не hardcode; production credentials/keystore исключены из AI
context. TLS validation не отключать; TrustAllCertificates/always-true
HostnameVerifier запрещены; production cleartext HTTP запрещён без
review. Exported components по умолчанию non-exported; проверить
IPC/intents/deep links/PendingIntents/widgets/notifications. SQL
parameterized. Проверять ownership, input, replay, rate limits. Play
Integrity --- дополнительный backend-verified signal, не auth
replacement. Редактировать логи, backup rules, dev/staging/prod
isolation. Никаких production bypass, hardcoded premium unlock, master
passwords или универсальных admin credentials. Incident readiness:
revoke/rotate credentials, invalidate sessions, disable vulnerable
feature/function, force min version при необходимости, backend patch
independently.

Security review levels: - S0 visual/layout; - S1 ordinary local
feature; - S2 DB mutations/sync/deep
links/notifications/export/import/widget; - S3
Auth/RLS/Billing/Secrets/Encryption/Account
deletion/Authorization/privileged backend/Play Integrity. S3 требует
independent review, security tests, automated checks и явный acceptance.

## Anti-Error

Не выдумывать API/files/tables/fields/test results. Сначала inspect
repo. Семантическое изменение данных требует migration. Acceptance
criteria ↔ tests 1:1. Проверять edge cases, consumers, regression и
полный diff. Не менять продукт/архитектуру молча. Если не проверено ---
`NOT VERIFIED` с причиной. После fixes --- full available regression.
AI-generated code/tests не являются доказательством без независимой
проверки.

## Каждая DEV-задача выполняется так

1.  **Preflight:** прочитать canon/ADR/state, изучить код/consumers,
    найти аналоги, определить риски.
2.  **Plan:** scope, файлы/модули, acceptance criteria, tests,
    migration/security level, exclusions.
3.  **Implement:** узкий связный diff.
4.  **Verify:** реальные build/tests/lint/static/security checks,
    regression, migration, diff review.
5.  **Close:** обновить PROJECT_STATE.md, DEVELOPMENT_LOG.md, ADR при
    необходимости; commit; отчёт.

Отчёт: COMPLETE/PARTIAL/BLOCKED; branch/commit; файлы/модули;
реализация; команды и реальные результаты; CI; migration/security
impact; NOT VERIFIED/verification debt; риски; следующий DEV.

## Android SDK/CI

Если Android SDK отсутствует, сначала определить потребность по Gradle
config. Устанавливать только официальным способом Android/Google;
никаких unofficial mirrors/TLS bypass. Если невозможно ---
`NOT VERIFIED — ANDROID SDK REQUIRED`, вести verification debt и
обеспечить GitHub Actions Android CI с доверенными pinned actions. JVM
tests не заменяют Android compilation/instrumentation. Не копить крупный
Android scope без независимого Android CI. Не устанавливать SDK раньше,
чем Android-модуль/сборка реально его требуют.

# 2. Полный план DEV-000--DEV-037

## DEV-000 --- Repository & Architecture Preflight

**Статус:** завершён по предоставленному отчёту.

**Цель и промт исполнителю** \> Аудит репозитория, canon, ADR, CI; один
авторитетный ROADMAP; DEVELOPMENT_LOG; PROJECT_STATE; security levels
S0--S3; сохранить root-level canonical docs; supersede старую
последовательность после сохранения полезных ограничений.

**Ожидаемый результат** - Карта репозитория; согласованные документы;
единый roadmap; зафиксированные ограничения среды.

**Проверки и критерии завершения** - Ссылки и пути корректны; нет
конкурирующих roadmap; CI статус подтверждён; diff без
секретов/случайных удалений.

## DEV-001 --- Development Foundation

**Статус:** завершён по предоставленному отчёту.

**Цель и промт исполнителю** \> Проверить и довести Gradle/Kotlin
foundation: wrapper, plugin/dependency management, version
catalog/conventions по необходимости, module boundaries, воспроизводимые
команды. Не создавать пустые модули.

**Ожидаемый результат** - Воспроизводимая Gradle-конфигурация,
закреплённые зависимости, CI базовых проверок, инструкции запуска.

**Проверки и критерии завершения** - Clean checkout; wrapper; нет
динамических версий/циклических зависимостей; команды реально работают.

## DEV-002 --- Security & Quality Foundation

**Статус:** завершён по предоставленному отчёту.

**Цель и промт исполнителю** \> Настроить реальные CI quality/security
checks, secret scanning, dependency review; создать/обновить threat
model, security architecture, test matrix, AI code security, dependency
policy, incident response.

**Ожидаемый результат** - Security docs, CI checks, threat boundaries,
S0--S3 process.

**Проверки и критерии завершения** - Проверки действительно выполняются;
секретов нет; CI permissions минимальны; нет фиктивных placeholders.

## DEV-003 --- Design System Foundation

**Статус:** завершён по предоставленному отчёту.

**Цель и промт исполнителю** \> Централизовать semantic colors,
typography, spacing, shapes, elevation, icon sizing, light/dark themes и
нужные базовые компоненты. Не создавать компоненты без потребителя.

**Ожидаемый результат** - Design tokens, темы, базовые UI contracts,
документация.

**Проверки и критерии завершения** - Нет дублирующих токенов; контраст;
module boundaries; не заявлять Compose build, если Android UI ещё
отсутствует.

## DEV-004 --- Canonical Data Model

**Статус:** завершён по предоставленному отчёту.

**Цель и промт исполнителю** \> Согласовать Domain models,
идентификаторы, ownership, lifecycle,
Activity/Goal/Session/Habit/Task/Project/Milestone, progress modes,
metrics/units, recurrence, time zones, tombstones и derived data.

**Ожидаемый результат** - Каноническая доменная модель, связи,
инварианты, ADR/DATA_MODEL.

**Проверки и критерии завершения** - Нет двойного source of truth;
Activity↔Goals many-to-many; Session multi-metric; семантика edit/delete
определена.

## DEV-005 --- Room Persistence Foundation

**Статус:** завершён по предоставленному отчёту.

**Цель и промт исполнителю** \> Создать Room
entities/DAOs/converters/database, mapping boundary, constraints,
indexes, foreign keys, schema export/versioning и migrations. Не
добавлять UI/sync.

**Ожидаемый результат** - Room foundation, schema snapshots, migration
strategy, DAO tests.

**Проверки и критерии завершения** -
CRUD/relations/constraints/migrations tests; нет destructive fallback;
Domain не зависит от Room.

## DEV-006 --- Repository & Offline Data Layer

**Цель и промт исполнителю** \> Реализовать domain-facing repository
contracts и Room implementations; mapping; observable streams; единый
error contract; local transactions; подготовить sync seams, но не
реализовывать Supabase/Sync Engine.

**Ожидаемый результат** - Repositories, mappers, offline read/write,
тесты локальных сценариев.

**Проверки и критерии завершения** - Domain/UI не обращаются к DAO;
offline CRUD; ошибки не маскируются; атомарность и regression consumers
проверены.

## DEV-007 --- Authentication & Supabase Foundation

**Цель и промт исполнителю** \> Добавить auth/session boundary,
dev/staging/prod config, backend schema и deny-by-default RLS. Не
доверять client ownerId/role/premium; service-role key только
server-side.

**Ожидаемый результат** - Auth lifecycle, Supabase schema, RLS
policies/tests, environment isolation.

**Проверки и критерии завершения** - CRUD RLS; User A/B isolation;
unauthenticated denied; logout/session expiry; нет secrets в
APK/repo/logs; S3 review.

## DEV-008 --- Sync Engine

**Цель и промт исполнителю** \> Описать conflict matrix; durable
Outbox/Inbox, idempotency, retries/backoff, tombstones/revisions. Не
использовать universal LWW; aggregates не authoritative.

**Ожидаемый результат** - Sync protocol, durable queue, conflict
resolution, observability.

**Проверки и критерии завершения** - Replay/retry без дублей; offline
multi-device merge; delete не воскресает; concurrent edits разрешаются
явно; process death tests.

## DEV-009 --- Canonical Execution Engine

**Цель и промт исполнителю** \> Единая точка команд
Done/Partial/Missed/Rescheduled/In Progress/Cancelled и Session
create/edit/delete; validation, ownership, idempotency, transaction +
outbox; derived recomputation.

**Ожидаемый результат** - Execution commands/results/errors,
единственный engine, матрица тестов.

**Проверки и критерии завершения** - Одна команда --- один факт;
Done=Planned default; Partial=Actual; Rescheduled не Missed; edit/delete
recompute; rollback атомарен.

## DEV-010 --- Recurrence Engine

**Цель и промт исполнителю** \> Единый recurrence для
Task/Activity/Habit: daily/weekly/weekdays/every N days/monthly/custom,
start/end/exceptions/skip/reschedule; lazy occurrences; stable identity.

**Ожидаемый результат** - Canonical recurrence engine и occurrence IDs.

**Проверки и критерии завершения** - DST, leap year, month-end,
timezone, exceptions, reschedule tests; одинаковые IDs app/widget/sync;
нет дублирующих engines.

## DEV-011 --- Goals Domain

**Цель и промт исполнителю** \> Goal use cases, types, lifecycle
Active/Paused/Completed/Archived, progressMode
METRIC/TARGET_VALUE/FREQUENCY/MILESTONES/TASKS/MANUAL;
target/deadline/units. UI не делать.

**Ожидаемый результат** - Goal domain, validation, lifecycle, tests.

**Проверки и критерии завершения** - Типы/units валидируются; изменение
target не повреждает Sessions; нет второго progress source; простое
создание не перегружено.

## DEV-012 --- Activities & Sessions

**Цель и промт исполнителю** \> Activity independent of Goal;
many-to-many Goal mapping; multi-metric Session; ручной ввод; Session
mutations только через Execution Engine.

**Ожидаемый результат** - Activity/Session use cases, mappings,
multi-metric tests.

**Проверки и критерии завершения** - Activity живёт после Goal; Session
влияет на несколько Goals через явный mapping без double count;
edit/delete recompute.

## DEV-013 --- Tasks & Habits

**Цель и промт исполнителю** \> Task --- действие; Habit --- recurring
behavioral rule, не Activity. Общий Recurrence Engine; Habit completion
→ Execution Engine → Session; streaks derived.

**Ожидаемый результат** - Task/Habit use cases, recurrence integration,
tests.

**Проверки и критерии завершения** - Один occurrence --- максимум одна
Session; skip/reschedule/missed различаются; Actual/streaks не
independent counters.

## DEV-014 --- Today Engine

**Цель и промт исполнителю** \> Единый Today Aggregator:
focus/high-impact, tasks, activities, habits, overdue и events только
если интеграция подтверждена. Детерминированный порядок, local
date/timezone.

**Ожидаемый результат** - Today read model/aggregator.

**Проверки и критерии завершения** - Нет UI-specific logic; корректны
timezone/statuses/overdue; virtual occurrences не сохраняются без
причины; empty/error/loading states.

## DEV-015 --- First Complete UI Vertical Flow

**Цель и промт исполнителю** \> Создать Android :app и
Compose/Material3/navigation/ViewModel/Hilt foundation;
Today→item→execute→Session→Goal progress. UI без domain logic; offline и
Android CI.

**Ожидаемый результат** - Рабочий end-to-end flow и Android build/test
CI.

**Проверки и критерии завершения** - UI/domain separation; no duplicate;
process recreation; offline; Compose/UI tests; Android CI реально green.

## DEV-016 --- Goals UI

**Цель и промт исполнителю** \> Goals list/detail/create/edit;
pause/resume/complete/archive; базовая форма простая, advanced options
под More Options; canonical use cases.

**Ожидаемый результат** - Полный Goals UI.

**Проверки и критерии завершения** - UI progress совпадает с engine;
lifecycle не меняет историю; валидация; UI/accessibility tests.

## DEV-017 --- Plan / Calendar UI

**Цель и промт исполнителю** \> Calendar/timeline
Tasks/Activities/Habits; reschedule только через canonical use cases;
единая recurrence; различать scheduled/due/done/rescheduled/cancelled.

**Ожидаемый результат** - Plan UI и корректные перемещения.

**Проверки и критерии завершения** - Today синхронизирован; recurrence
series не повреждается; timezone/DST; UI tests.

## DEV-018 --- Activities / Habits UI

**Цель и промт исполнителю** \> Activities/Habits list/detail, Session
history/manual entry, mappings; не объединять Activity и Habit.

**Ожидаемый результат** - UI Activities/Habits/Sessions.

**Проверки и критерии завершения** - Habit→canonical Session; Activity
без Goal; edit/delete recompute; multi-metric validation; tests.

## DEV-019 --- Progress Calculation Engine

**Цель и промт исполнителю** \> Единые формулы Goal Progress, Planned vs
Actual, Execution, Goal Impact, Activity/Life Area stats, periods.
Done=100%; Partial=Actual/Planned; Missed=0%; Cancelled исключается
уместно; Rescheduled не missed.

**Ожидаемый результат** - Calculation Engine, формулы и test matrix.

**Проверки и критерии завершения** - Все формулы unit/property tested;
повторяемость; edit/delete recompute; no double count; одинаковые
результаты на всех экранах.

## DEV-020 --- Pace & Forecast

**Цель и промт исполнителю** \> Remaining, required/current pace,
ahead/behind и explainable forecast; учитывать недостаточность данных,
paused goals, deadline/target changes.

**Ожидаемый результат** - Pace/forecast model и UI contracts.

**Проверки и критерии завершения** - Нет ложной точности; zero history;
edge cases; формулы объяснимы и тестируемы.

## DEV-021 --- My Vision / Life Areas / Life Plan

**Цель и промт исполнителю** \> Optional Vision, customizable Life
Areas, strategic Life Plan overview. Vision не обязательна; Goals
связываются с областями.

**Ожидаемый результат** - Vision, Life Areas, overview.

**Проверки и критерии завершения** - Можно пользоваться без Vision;
области настраиваемы; архивирование не удаляет историю; нет forced
onboarding.

## DEV-022 --- Life Balance

**Цель и промт исполнителю** \> Analytics по Sessions/Goals/Life Areas.
Различать Attention, Execution, Goal Progress, Trend; не оценивать
объективное качество жизни.

**Ожидаемый результат** - Life Balance metrics и объяснения.

**Проверки и критерии завершения** - Multi-area allocation без double
count; unmapped/small sample; понятные определения; UI copy не вводит в
заблуждение.

## DEV-023 --- Projects & Milestones

**Цель и промт исполнителю** \> Опциональные Projects/Milestones;
weighted progress: Milestones → Tasks fallback → Manual только по явному
выбору; формулы без смешения режимов.

**Ожидаемый результат** - Project/Milestone domain/UI и progress rules.

**Проверки и критерии завершения** - Простая Goal не требует project;
веса валидны; нет циклов; удаление не теряет Sessions.

## DEV-024 --- History / Review / Insights

**Цель и промт исполнителю** \> История Sessions/execution и
week/month/year review; insights только из проверяемых фактов; фильтры и
масштабируемость.

**Ожидаемый результат** - History, review, explainable insights.

**Проверки и критерии завершения** - История = canonical facts; edits
пересчитываются; нет недоказанных выводов; pagination/performance.

## DEV-025 --- Search / Inbox / Quick Add

**Цель и промт исполнителю** \> Global Quick Add и search для
согласованных сущностей; Inbox только при ясной ответственности;
offline, ownership, idempotency.

**Ожидаемый результат** - Quick Add, search, при необходимости Inbox.

**Проверки и критерии завершения** - Quick Add не обходит validation;
retry без дублей; поиск не раскрывает чужие данные; deep links
ownership-safe.

## DEV-026 --- Timer Engine

**Цель и промт исполнителю** \> Timer state machine
Start/Pause/Resume/Stop/Review/Save/Cancel; устойчивость к process
death; сохранение через Execution Engine; ручная Session остаётся.

**Ожидаемый результат** - Persistent Timer Engine и Session integration.

**Проверки и критерии завершения** - Elapsed time корректен после
process death/reboot/time changes; Save idempotent; Cancel не создаёт
Session; widget/app согласованы.

## DEV-027 --- Notifications & Reminders

**Цель и промт исполнителю** \> Reminders на canonical scheduling;
выбрать WorkManager/AlarmManager по реальным требованиям;
permissions/channels/quiet hours/timezone; actions через use cases.

**Ожидаемый результат** - Scheduling, channels, preferences,
notification actions.

**Проверки и критерии завершения** - Нет дублей; recurrence
update/reschedule; permission denied; DST; PendingIntent security;
platform limits documented.

## DEV-028 --- Android Home Screen Widget

**Цель и промт исполнителю** \> Widget как execution surface: Focus,
3--5 actions, Done/Partial/Start Timer/Quick Add; shared
Today/Execution/Timer engines; offline; защищённые intents.

**Ожидаемый результат** - Widget sizes/configurations, actions, refresh.

**Проверки и критерии завершения** - Widget не source of truth;
forged/stale/replayed intents; один execution fact; app/widget
consistency; API-level tests.

## DEV-029 --- Billing & Entitlements

**Цель и промт исполнителю** \> Утвердить entitlement matrix; Google
Play Billing; backend verification purchase tokens; server-authoritative
entitlement; pending/restore/refund/grace/expiry.

**Ожидаемый результат** - Billing integration и entitlement lifecycle.

**Проверки и критерии завершения** - Client premium spoof не работает;
token backend verified; test/prod separated; S3 independent review;
актуальные Play policies.

## DEV-030 --- Settings / Account / Export / Delete

**Цель и промт исполнителю** \> Settings, account/session, preferences,
secure export и backend-authorized deletion; локальные
копии/outbox/tokens/stale devices; не заявлять удаление без backend
confirmation.

**Ожидаемый результат** - Settings, export/delete flows, data lifecycle
policy.

**Проверки и критерии завершения** - User isolation; export
completeness; backend+local deletion; stale device cannot resurrect;
token invalidation; S3 review.

## DEV-031 --- Offline & Conflict Hardening

**Цель и промт исполнителю** \> Системная матрица offline, multi-device,
concurrent edits, delete/stale update, process death, retry storms,
clock skew, account switch, token expiry, reinstall/restore.

**Ожидаемый результат** - Conflict hardening, convergence tests,
documented limits.

**Проверки и критерии завершения** - No duplicate Sessions; tombstones
hold; independent records preserved; account isolation; stress/property
tests.

## DEV-032 --- Performance & Database Optimization

**Цель и промт исполнителю** \> Сначала измерить; затем оптимизировать
Room queries, indexes, paging, recomposition, Today, analytics, sync,
widget refresh, background work. Cache только с invalidation.

**Ожидаемый результат** - Performance baseline и измеримые оптимизации.

**Проверки и критерии завершения** - Before/after metrics; no N+1; cache
correctness; large datasets; main-thread/recomposition; correctness
preserved.

## DEV-033 --- Accessibility / Adaptive UI / Localization

**Цель и промт исполнителю** \> Аудит TalkBack, semantics, touch
targets, focus, contrast, font scaling, adaptive layouts, locale
strings/plurals/dates/units, RTL readiness.

**Ожидаемый результат** - Accessibility fixes, adaptive layouts,
localization foundation.

**Проверки и критерии завершения** - TalkBack; font scaling; screen
sizes; no hardcoded strings; locale formatting; RTL проверен в
заявленном scope.

## DEV-034 --- Full Cybersecurity Audit

**Цель и промт исполнителю** \> Формальный аудит
Auth/RLS/Sync/Billing/Widget/Notifications/Import/Export/Delete/Backup/deep
links/local storage/secrets/TLS/IPC/dependencies. Abuse tests и
независимый S3 review.

**Ожидаемый результат** - Threat model update, findings register,
remediation, security report.

**Проверки и критерии завершения** - RLS CRUD isolation; no secrets;
replay/ownership/billing/widget/deep-link tests; Critical/High закрыты
или release blocked.

## DEV-035 --- Full Regression / Integration / Migration Testing

**Цель и промт исполнителю** \> Полный regression/E2E plan:
install/upgrade, offline, auth, sync, execution, recurrence, goals,
Today, Plan, Progress, timer, notifications, widget, billing,
export/delete.

**Ожидаемый результат** - Regression suite, migration report, coverage
gaps.

**Проверки и критерии завершения** - Fresh install + real upgrade paths;
critical journeys; process death/network failure/multi-device; tests не
ослаблены; NOT VERIFIED list.

## DEV-036 --- Google Play Release Hardening

**Цель и промт исполнителю** \> Перепроверить официальные Play
requirements; production variants/signing/R8/permissions/backup/target
SDK/privacy/Data Safety/content rating/store
listing/billing/rollout/rollback. Не публиковать без разрешения.

**Ожидаемый результат** - Release config, store package, compliance
checklist, rollback plan.

**Проверки и критерии завершения** - Reproducible production build; keys
изолированы; minimal permissions; Data Safety truthful; policies current;
no critical/high security issues.

## DEV-037 --- Production Release Candidate Audit

**Цель и промт исполнителю** \> Финальный Go/No-Go audit без новых
функций: canon, UX, security, data integrity, performance,
accessibility, localization, CI, migrations, offline/sync, billing,
deletion, widget, notifications, artifact provenance.

**Ожидаемый результат** - RC report, risks, verified artifact, rollback
readiness, recommendation.

**Проверки и критерии завершения** - All mandatory gates green; no
Critical/High; supported migrations; critical journeys; verification
debt disclosed; explicit owner approval before publish.

# 3. Контрольные точки независимого аудита

Claude продолжает работу между контрольными точками. Контрольные точки
не являются поводом останавливать обычную разработку.

-   **После DEV-004:** domain model, source of truth, relationships,
    progress modes, units/time semantics, canon/ADR.
-   **После DEV-009:** Repository/offline, Auth/Supabase/RLS,
    Sync/conflicts, Execution Engine, idempotency, transactions, Session
    edit/delete/recompute, CI evidence. Это первая критическая
    техническая проверка.
-   **После DEV-015:** полный flow Today → item → execute → Session →
    Goal progress; UI/domain separation; Android CI; offline; duplicate
    prevention.
-   **После DEV-022:** Calculation Engine, Pace/Forecast, Vision/Life
    Areas, Life Balance, no double counting.
-   **После DEV-030:** Quick Add/Search, Timer, Notifications, Widget,
    Billing, Export/Delete, S2/S3 security, account/offline lifecycle.
-   **После DEV-034:** полный security audit и подтверждение fixes.
-   **После DEV-037:** final release candidate Go/No-Go.

# 4. Git и распределение ответственности

Claude --- единственный основной исполнитель конкретной DEV-задачи. Не
поручать Claude и Codex одновременно менять одни и те же файлы. Codex
может выступать независимым reviewer. Изменения Room schema, Sessions,
recurrence, sync, auth, RLS, billing, secrets и account deletion ---
отдельный review до merge. Не выполнять force-push/history
rewrite/delete/merge без разрешения.

Предпочтительный поток: **Task → Branch → Implementation → Tests →
Commit → Independent Review → Fixes → Final Verification → Merge.**

# 5. PROJECT_STATE.md

Поддерживать краткий актуальный статус: - Current phase/DEV и overall
status; - last completed DEV + commit; - active DEV; - changed
modules/files; - tests/build/lint/CI; - Android SDK availability; -
verification debt (что не проверено, почему и как закрыть); -
blockers/accepted risks; - canonical decisions/ADR; - next DEV.

`DEVELOPMENT_LOG.md` --- история значимых milestones, не копия Git log и
не перечень каждого файла.

# 6. Когда обязательно остановиться и спросить владельца

Только если решение меняет product promise/navigation/core entity/source
of truth/execution semantics/sync conflict policy/security boundary;
несёт риск потери данных; требует production credentials или публикации;
существенно меняет лицензии/стоимость/архитектуру; либо конфликтует с
ADR и нет безопасного решения.

Формат эскалации: 1. утверждённый canon; 2. фактическое состояние; 3.
точный конфликт; 4. затронутые модули/данные/flows; 5. варианты A/B и
trade-offs; 6. рекомендация; 7. что блокируется.

# 7. Команда для продолжения с текущего места

> Используй `STEPWISE_FULL_DEVELOPMENT_PLAN.md` как подробную
> спецификацию DEV-000--DEV-037, а `/ROADMAP.md` --- как единственный
> авторитетный порядок этапов. DEV-000--DEV-005 завершены согласно
> PROJECT_STATE. Продолжай с DEV-006. Для каждого этапа применяй
> мастер-промт, конкретную задачу, expected results и verification
> checklist. Не запрашивай подтверждение обычных технических решений.
> После каждого DEV обновляй PROJECT_STATE/DEVELOPMENT_LOG, выполняй
> реальные проверки, diff review и commit. Продолжай автоматически,
> кроме условий эскалации из раздела 6. Не заявляй проверки, которые
> фактически не выполнял; веди verification debt. Не меняй продуктовую
> концепцию, не вводи CRM/Networking/Skill Graph/RPG. Публикация в
> Google Play требует отдельного разрешения владельца.

# 8. Ограничения

Этот документ основан на известных решениях Stepwise и предоставленном
статусе. Он не является независимым аудитом репозитория и не доказывает
реализацию. Перед каждым DEV Claude сверяет его с фактическим кодом,
`/ROADMAP.md`, PRODUCT_CANON, ARCHITECTURE, DATA_MODEL и ADR. При
расхождении нельзя молча заменять утверждённый canon: описать конфликт и
применить актуальное решение. Детали, не подтверждённые canon (например,
entitlement matrix, календарные интеграции, поддерживаемые локали,
точность reminders), не выдумывать. Актуальные Android/Google
Play/Supabase requirements перепроверять на момент реализации. Зелёный
CI подтверждает только реально запущенные jobs.

# 9. Внутренняя панель ревью (добавлено при включении в репозиторий)

Начиная с DEV-006 каждая DEV-задача перед коммитом проходит ревью одним
или несколькими внутренними критик-агентами (`.claude/agents/`), в
зависимости от security level и типа изменений. Полное описание ролей,
триггеров и ограничений — в `/ROADMAP.md`'s раздела "Internal Review
Panel". Ключевое ограничение, которое нельзя упускать: все эти
критик-агенты — тот же Claude, поэтому они **не** заменяют независимую
(человеческую или отдельную от исполнителя) проверку, требуемую
`AI_CODE_SECURITY.md` для S3-кода — только дополнительный внутренний
фильтр качества.
