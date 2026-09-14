# PRODUCT_CANON.md — Stepwise Product Memory

This file is the canonical, living product spec for Stepwise — the "what and why." It is one of the documents `/CLAUDE.md` (a short pointer, auto-loaded by Claude Code at session start) directs every session to read before acting, regardless of which chat/session is doing the work. Extend this file in place as the concept evolves; do not let the spec live only in a chat conversation.

Sibling canonical documents (read alongside this file — full index in `/CLAUDE.md`):
- `/ARCHITECTURE.md` — the canonical technical architecture (stack, layers, modules, offline/sync, security, testing, widget, etc.)
- `/DATA_MODEL.md` — entities, fields, relationships, and the automatic-cascade rule
- `/DEVELOPMENT_PROTOCOL.md` — binding code-architecture centralization rules
- `/ANTI_ERROR_STANDARD.md` — the audit/change-management process for all future work
- `/PROJECT_STATE.md` — current phase and status
- `/CALCULATION_ENGINE.md`, `/DESIGN_SYSTEM.md` — scaffolded, populated in their own phase
- `/ADR/` — individual Architecture Decision Records
- `/docs/security/` — security architecture, threat model, test matrix, dependency policy, incident response, AI-code-security
- `/docs/functional-analysis.md`, `/docs/scope-of-work.md`, `/docs/play-store-checklist.md` — supporting breakdowns
- `/docs/android-stack.md`, `/docs/android-architecture.md` — superseded (React Native/Expo era), kept for history only

## Non-negotiable principle

**Powerful underneath. Simple every day.** Every new feature must be checked against two questions: (1) does it help the person achieve their goals? (2) does it add an extra action to the daily scenario? If (2) is true, find a way to automate or hide the complexity — never push it onto the user.

## Concept change process (§77 — binding on all future work)

Nothing in the concept below may be arbitrarily removed, simplified, or reinterpreted during implementation. If a technical constraint requires changing the concept: (1) explain the problem, (2) show the affected functions/entities and their consumers, (3) propose options, (4) wait for the user's choice, (5) only then change the architecture. Build sequencing (what gets coded first) is not the same as scope reduction — every part is designed as a piece of one complete application.

## Full concept — Master Product Concept (source of truth, verbatim)

### 1. Что такое Stepwise

Stepwise — полноценное мобильное приложение для iOS и Android (Android разрабатывается первым; iOS строится позже на той же базе), которое помогает человеку превращать долгосрочные жизненные цели в конкретные ежедневные действия и измерять реальный прогресс.

Главная идея: то, что человек хочет получить завтра, должно быть связано с тем, что он делает сегодня.

Stepwise не должен быть просто: todo list; habit tracker; календарём; системой целей; тайм-трекером; приложением со статистикой. Это должна быть единая система, которая связывает всё вместе.

Основная логика:

> MY VISION → LIFE AREAS → GOALS → MILESTONES → PROJECTS / PLANS → TASKS / ACTIVITIES / HABITS → TODAY → EXECUTION → PROGRESS → ANALYSIS → ADJUSTMENT → ACHIEVEMENT

И обратная связь:

> DAILY ACTIONS → GOALS → LIFE AREAS → LIFE BALANCE

### 2. Главный UX-принцип

Powerful underneath. Simple every day.

Внутри приложение может иметь сложную архитектуру и большое количество взаимосвязей. Но пользователь не должен ощущать эту сложность. Главный принцип: сложная система внутри, простое действие снаружи.

Около 80% ежедневного использования — Посмотреть → Сделать → Отметить.

> TODAY
> Boxing — 1h
> Stepwise — 2h
> English — 30m
> Meditation — 15m

После бокса пользователь нажимает ✓. Всё. Внутри приложение автоматически: создаёт Session; записывает Actual; обновляет Activity; обновляет связанную Goal; обновляет Life Area; обновляет статистику Week/Month/Year/Lifetime; recalculates Goal Pace; recalculates Forecast; обновляет Life Balance; обновляет Personal Progress History. Пользователь не должен выполнять эти действия вручную.

### 3. Главная продуктовая формула

> GOAL → PLAN → DO → TRACK → ANALYZE → ADJUST → ACHIEVE

На стратегическом уровне:

> VISION → LIFE AREAS → GOALS → ACTIONS

И обратно:

> ACTIONS → RESULTS → GOALS → LIFE BALANCE

### 4. Основная навигация

TODAY | GOALS | PLAN | PROGRESS, плюс глобальная кнопка ＋. Не нужно создавать отдельный основной tab для каждой внутренней сущности. Activities, Skills, History, Vision, Life Areas и другие функции открываются из основных экранов.

### 5. MY VISION

My Vision — верхний стратегический уровень. Отвечает на вопрос: какой жизни я хочу? Не обязательная сложная анкета. Пользователь может: написать общее видение своей жизни; написать Vision отдельно по каждой сфере жизни; редактировать; возвращаться к нему; связывать Goals со своим Vision.

> MY VISION
> — иметь сильное и здоровое тело
> — финансовая независимость
> — создать успешный собственный продукт
> — сильные отношения
> — постоянное развитие
> — свобода путешествовать
> — иметь достаточно свободного времени

Vision опционален. Пользователь может начать пользоваться Stepwise вообще без заполнения Vision.

### 6. LIFE AREAS

Цели могут быть сгруппированы по сферам жизни. Стандартные примеры: Health, Finance, Career, Relationships, Personal Development, Family, Lifestyle, Projects, Education, Spirituality. Life Areas полностью настраиваемы: создать свою, удалить, переименовать, поменять порядок, выбрать иконку/категорию.

> HEALTH — Goal: 100 Hours Boxing / Goal: Weight 85 kg / Goal: Sleep average 8h
> FINANCE — Goal: Capital €100,000 / Goal: Additional income €2,000/month
> PROJECTS — Goal: Launch Stepwise

### 7. LIFE PLAN

Life Plan — стратегическое представление всей жизни пользователя: My Vision, Life Areas, Goals внутри каждой сферы, состояние целей, прогресс, активность по каждой сфере, баланс между сферами. Не должен становиться дополнительной ежедневной обязанностью — это стратегический экран.

> MY LIFE
> Health / Finance / Career / Relationships / Development / Lifestyle

Внутри каждой сферы отображаются активные цели.

### 8. LIFE BALANCE

Показывает, куда фактически направляется внимание человека. Строится автоматически из реальных данных: Goals, Activities, Sessions, Tasks, Habits, Actual time, Execution, Goal progress.

> LIFE BALANCE — September
> Health 32h / Career 71h / Development 14h / Relationships 6h / Lifestyle 2h

Нельзя сводить всю жизнь к одному искусственному проценту. Для Life Area показываются независимые показатели:
- **Attention** — сколько времени/действий направлено в эту сферу
- **Execution** — насколько выполнен запланированный объём
- **Goal Progress** — как двигаются цели внутри сферы
- **Trend** — растёт или падает внимание относительно предыдущего периода

> HEALTH — Attention: 32h / Execution: 84% / Goals progressing: 3/3 / Trend: +12%

Stepwise может показывать наблюдение («Career занимает 54% отслеживаемого времени этого месяца», «Relationships получает меньше внимания третий месяц подряд»), но не должен говорить пользователю, как ему правильно жить — оно показывает данные.

### 9. TODAY

Главный ежедневный экран. Отвечает на вопрос: что сегодня действительно двигает меня вперёд?

> TODAY
> Focus: Finish payment integration
> Today: Boxing — 19:00 — 1h / English — 30m / Meditation — 15m / Finish Stepwise onboarding
> Progress: 3 / 5

Today не должен быть перегружен сложными графиками, полной статистикой, Skills, всеми Milestones, всеми Goals, Life Balance, деталями Forecast. Это execution screen.

### 10. GOALS

Отвечает на вопрос: чего я хочу достичь?

> 100 Hours Boxing — 72/100h · Read 20 Books — 7/20 · Run 500km — 183/500km · Save €30,000 — €8,400/€30,000 · Weight 92→85kg · Launch Stepwise — 68%

### 11. GOAL TYPES

Разные цели требуют разной математики — нельзя использовать одну формулу для всех:
- **Cumulative** — 72 → 73 → 74 → 100 hours boxing
- **Quantity** — Read 20 books, 7/20
- **Distance** — Run 500km, 183/500km
- **Financial** — Save €30,000, €8,400/€30,000
- **Target Value** — Weight 92→85kg (значение не накапливается, оно движется к target)
- **Frequency** — Train 3 times per week
- **Percentage / Project** — Launch Stepwise, 68%

### 12. GOAL CREATION

Основная форма: Goal name, Target, Deadline (например «100 Hours Boxing / 100 hours / 31 December» → Save). Дополнительные параметры под «More Options»: Life Area, description, priority, milestones, project, metric, schedule, category, notes.

### 13. GOAL STATUS

Goal имеет отдельный lifecycle status — не то же самое, что статус выполнения Task: **Active, Paused, Completed, Archived**. Paused важен и это не Failed и не Cancelled («Boxing temporarily paused»).

### 14. MILESTONES

Сложные Goals могут содержать Milestones:

> Launch Stepwise — Specification ✓ / UI/UX ✓ / Development 73% / Testing 21% / Store preparation 0% / Launch 0%

Опциональны — для простой цели вроде «100 Hours Boxing» могут не использоваться вообще.

### 15. PROJECTS

Нужны для сложных Goals. Goal → Project → Tasks. Например Launch Stepwise → Product Design / Development / Testing / Marketing / Launch. Не должны быть обязательными.

### 16. TASKS

Task — конкретное действие («Finish onboarding screen»). Может содержать: Goal, Project, Life Area, date, time, deadline, duration, priority, reminder, recurrence, metric, Planned, Actual, status, notes — почти все поля optional. Быстрая задача: «Finish onboarding / Tomorrow» → Save.

### 17. INBOX / UNSCHEDULED

Пользователь должен быстро добавить мысль или задачу без организации («Buy insurance»). Если дата и другие параметры не указаны — Task попадает в Inbox/Unscheduled. Позже можно добавить дату, связать с Goal/Project, задать priority, удалить, архивировать. Принцип: **Capture now. Organize later.**

### 18. OVERDUE

Невыполненные действия не должны просто исчезать. Today может иметь блок OVERDUE («Finish payment integration — Yesterday») с действиями Complete/Reschedule/Skip/Edit. Но приложение не должно автоматически переносить все просроченные задачи на сегодня — иначе Today перегрузится.

### 19. ACTIVITIES

Activity — постоянная деятельность человека (Boxing, Running, English, Programming, Reading, Meditation, Gym, Cycling). Существует независимо от конкретной Goal.

### 20. ACTIVITY ≠ GOAL

Принципиально разные сущности. Activity: Boxing. Goal: 100 Hours Boxing. Когда Goal достигается (100/100h ✓), Goal становится Completed, но Activity остаётся («Boxing — Lifetime: 347h»). Позже можно создать новую Goal («500 Hours Boxing») — история Activity не обнуляется.

### 21. ACTIVITY ↔ MULTIPLE GOALS

Одна Activity может быть связана с несколькими Goals. Пример: Activity Running ↔ Goal 1 «Run 500km» и Goal 2 «100 Hours Running». Один Running Session (Distance: 8.2km, Duration: 52min) может одновременно обновить «Run 500km» (+8.2km) и «100 Hours Running» (+52min). Связь Activity ↔ Goal должна поддерживать multiple relations, каждая со своей метрикой.

### 22. SESSIONS

Session — фактическая запись выполненной Activity («Boxing Session, 13 September, Planned: 60min, Actual: 60min, Status: Done»; или «Running Session, Duration: 52min, Distance: 8.2km»). Session — важная часть source of truth.

### 23. MULTI-METRIC SESSIONS

Session не ограничен одной метрикой — может иметь несколько Metric Values: Running (Duration: 52min, Distance: 8.2km), Reading (Duration: 40min, Pages: 32), Cycling (Duration: 1h40m, Distance: 37km), Workout (Duration: 70min). Это необходимо учитывать в data model.

### 24. ONE ACTION → ONE PROGRESS EVENT

Критически важное правило: одно реальное действие не должно требовать нескольких отметок в разных разделах. После Boxing пользователь НЕ отмечает Calendar, потом Habit, потом Activity, потом Goal, потом Progress по отдельности — он делает одно действие «Boxing ✓», и Stepwise создаёт один Progress Event/Session, который автоматически обновляет всё связанное.

### 25. EXECUTION STATUSES

✓ Done, ◐ Partial, ○ Missed/Not Done, → Rescheduled, ◌ In Progress, × Cancelled. Пользователь не должен постоянно видеть 6 больших кнопок. Основное действие: Tap ✓. Дополнительные статусы: swipe/long press/more menu.

### 26. QUICK DONE

Если Activity/Task имеет Planned value (Boxing — 60min) и нажато ✓, Stepwise предполагает Actual = Planned без дополнительного popup. Если результат отличается — пользователь выбирает Partial/Edit.

### 27. PARTIAL COMPLETION

Planned: 60min boxing, Actual: 40min, Status: Partial. В Goal и Statistics идёт +40min, а не 0. Полезная работа не считается полностью невыполненной только потому, что не достигнуто 100% плана.

### 28. PLANNED VS ACTUAL

Хранятся отдельно (Development: Planned 3h, Actual 2h10m). Используется для Execution, statistics, trends, behavioral analytics, planning accuracy.

### 29. EXECUTION CALCULATION

Определена однозначно: Done = 100%; Partial measurable = Actual/Planned; Missed = 0%; Cancelled исключается из denominator; Rescheduled не считается Missed на первоначальную дату. Если тип Task невозможно измерить численно: Done = 100%, Partial = approximate completion percentage, заданный пользователем, либо отдельная логика.

### 30. RECURRING ACTIVITIES

Пользователь создаёт расписание один раз («Boxing, Tuesday+Thursday, 19:00, 1h»), Stepwise автоматически формирует будущие occurrences.

### 31. HABITS

Habit — правило регулярности, не дублирует Activity. Модель: Activity «Meditation» / Habit «Meditation — 15min every day». Выполнение Habit создаёт Meditation Session. Activity отвечает «что я делаю?», Habit — «как регулярно я хочу это делать?».

### 32. HABIT PARTIAL

Habit «Read 30min/day», сегодня 18min → результат 18/30min Partial, и +18min Reading идёт в статистику.

### 33. RECURRENCE

Нужно избегать дублирования механизма Recurring Activity и Habit schedule — внутренне единый механизм Schedule/Recurrence Rule, применимый к Task, Activity, Habit.

### 34. TIMER

Для временных Activities: START → timer → STOP → 01:17:32 → Save Session → +1h17m. Всегда optional — можно вручную ввести Actual duration.

### 35. PLAN / CALENDAR

Day/Week/Month/Year. Отображает Tasks, Activities, Habits, events, deadlines, milestones. Recurring activities генерируются автоматически.

### 36. QUICK ADD

Глобальная кнопка ＋, доступна почти везде. Базовые действия: Task, Goal, Activity, Habit, Session, Project, Note/Idea. Context-aware: внутри Boxing ＋ сразу предлагает «Add Boxing Session».

### 37. NATURAL LANGUAGE QUICK ADD

Пользователь пишет «Boxing tomorrow 19:00 for 1h», Stepwise распознаёт Activity/Date/Time/Duration, пользователь подтверждает. Дополнительный способ ввода, не заменяет обычные формы.

### 38. PROGRESSIVE DISCLOSURE

Basic: Boxing / 1h / Tuesday. Advanced: linked Goal, recurrence, reminder, metric, notes, priority, forecasting settings. Пользователь видит сложность только тогда, когда она ему нужна.

### 39. SMART DEFAULTS

Внутри Boxing «Add Session» не спрашивает Activity. Если Boxing связан с Goal, не спрашивает Goal каждый раз. Если Planned=60min и нажато ✓ — Actual=60min.

### 40. PROGRESS

Аналитический центр. Периоды: WEEK | MONTH | YEAR | ALL TIME.

### 41. GLOBAL PROGRESS

> THIS WEEK — Boxing 3h20m / Stepwise 11h45m / English 4h30m / Reading 2h10m / Meditation 1h05m
> Planned: 27h / Actual: 22h50m / Execution: 84.6%

### 42. STATUS STATISTICS

Done — 23, Partial — 4, Missed — 3, Rescheduled — 1, Cancelled — 1.

### 43. ACTIVITY STATISTICS

> BOXING — Goal: 72/100h · This week: 3h20m · This month: 12h40m · This year: 103h15m · Lifetime: 347h42m · Sessions: 286 · Average Session: 1h13m · Average/week: 3.1h

### 44. WEEK / MONTH / YEAR / ALL TIME

Периоды работают для Activities, Goals, Habits, Life Areas, Progress.

### 45. HISTORY

Полная история Sessions, можно открыть и исправить («Actual 60min → 40min»), все связанные totals пересчитываются.

### 46. GRAPHS

Daily/Weekly/Monthly/Yearly trend, cumulative progress, calendar heatmap, consistency. Аналитический элемент — не перегружает Today.

### 47. PERIOD COMPARISON

«Boxing 12h40m this month, ↑22% vs last month» / «Development 38h, ↓7h vs previous month» — показывает динамику.

### 48. PACE

Для Goal с deadline: 100 Hours Boxing, 72/100h, Remaining 28h, Required pace 2.1h/week, Current pace 3.0h/week, Status Ahead (или Behind by 1.4h/week).

### 49. FORECAST

Estimated completion 17 December, Deadline 31 December, 14 days ahead. Пересчитывается при изменении current pace. Не абсолютное обещание — прогноз на основании текущих данных.

### 50. GOAL IMPACT

Различает занятость и реальный прогресс. Task может иметь Goal Impact (High/Medium/Low), внутри — числовой Impact Score 0–100. Today использует Impact Score для ранжирования.

### 51. SKILLS

Пользователь не должен вручную обновлять Skill Progress каждый день. Activity «Programming» + Goal «500 Hours Programming» → Skill «Software Development» использует accumulated activity data. Skills — аналитическая/profile функция, не отдельная ежедневная обязанность.

### 52. PERSONAL PROGRESS HISTORY

Stepwise постепенно превращается в историю развития человека.

> 2026 — Boxing 126h / Development 684h / English 193h / Books 31 / Meditation 82h / Goals completed 14 / Projects completed 9 / Overall execution 81%

Через несколько лет: BOXING — Lifetime 864h, Sessions 691, Started March 2025, Goals completed 6.

### 53. WEEKLY REVIEW

Опциональный, автоматически подготовленный summary.

> YOUR WEEK — Goals progressed: 4/6 · Planned: 22h · Actual: 18h40m · Execution: 84% · Strongest Area: Health · Falling Behind: Stepwise Development · Next: Plan next week

Максимально автоматизирован — пользователь не составляет отчёт вручную.

### 54. BEHAVIORAL ANALYTICS

«Ты планируешь 5 тренировок в неделю, но выполняешь в среднем 3.2.» / «Последние 4 недели время на Stepwise снизилось 14h→8h/week.» / «91% morning tasks vs 54% evening tasks.» / «Goal deadline may be at risk.»

### 55. RECOMMENDATIONS

«Required: 5.1h/week, Current: 3.2h/week → Add approximately 2h/week.» Пользователь сам решает: Apply / Ignore / Edit Plan.

### 56. SEARCH

Global Search по Goal, Activity, Task, Project, Habit, History — нужен по мере роста данных (сотни Goals, тысячи Tasks/Sessions, десятки Activities).

### 57. ARCHIVE

Разделение Active / Completed / Archived для Goals, Projects, Activities, Habits — предотвращает перегрузку интерфейса.

### 58. SETTINGS

Language, Units, Currency, Notifications, Account, Export Data, Delete Account, Privacy, Theme, Backup/Sync configuration. Минимум Russian + English. Вся архитектура i18n-ready, никаких hardcoded UI strings.

### 59. OFFLINE FIRST

Пользователь отмечает ✓ без интернета; после восстановления сети данные синхронизируются. Часть архитектуры с самого начала, не поздний костыль.

### 60. IDEMPOTENCY

Критически важное техническое требование: двойной тап ✓ из-за плохого интернета не должен дать +2h вместо +1h. Progress Events/Sessions должны иметь idempotent processing.

### 61. EDIT / DELETE RECOMPUTATION

Изменение Session (60min→40min) или удаление должно корректно пересчитать: Goal progress, Month/Year/Lifetime total, Pace, Forecast, Life Balance.

### 62. SOURCE OF TRUTH

Source of truth — уровень Sessions/Progress Events, не manually maintained totals. Goal progress «72h» выводится из Progress Events/Sessions, а не хранится как независимый вручную изменяемый total — иначе возможна рассинхронизация (Goal=74h, Activity=73h, Month total=72h40m). Этого нельзя допускать.

### 63. LIFE AREA CALCULATIONS

Life Area не имеет вручную задаваемый progress percentage — агрегирует данные своих Goals/Activities/Actions. Показатели Goal Progress, Attention, Execution, Trend не смешиваются в один математически сомнительный показатель.

### 64. DATA MODEL — HIGH LEVEL

User, Vision, LifeArea, Goal, Milestone, Project, Task, Activity, Habit, Schedule/RecurrenceRule, Session, ProgressEvent, Metric, MetricValue, GoalActivityLink, Skill, Reminder, Review, Insight/Recommendation.

### 65. RELATIONSHIPS — HIGH LEVEL

> User → Vision → Life Areas → Goals → Milestones/Projects → Tasks/Activities/Habits → Sessions/Progress Events → Progress/Analytics/Forecast → Life Balance

Связи не всегда strictly hierarchical: Activity ↔ multiple Goals, Goal ↔ multiple Activities, Activity ↔ Habit, Session → multiple MetricValues.

### 66. ОСНОВНОЙ DAILY FLOW

Morning: Open Today → see Boxing 1h/Stepwise 2h/English 30m/Meditation 15m → Do Boxing → Tap ✓ → Done. Внутри: Session created, Actual=1h, Activity updated, Goal updated, Health Life Area updated, Week/Month/Year/Lifetime updated, Pace recalculated, Forecast recalculated, Life Balance updated. Пользователь видит только: Boxing ✓.

### 67. ПРИМЕР ПОЛНОГО FLOW

> MY VISION (Strong and healthy body) → LIFE AREA (Health) → GOAL (100 Hours Boxing) → ACTIVITY (Boxing) → PLAN (Tue/Thu 19:00 1h) → TODAY (Boxing 19:00 1h) → DO (Training) → ✓ → SESSION (1h) → GOAL (72→73h) → STATISTICS (Week/Month/Year/Lifetime) → PACE (3h/week) → FORECAST (Dec 17) → LIFE BALANCE (Health data updated) → PERSONAL HISTORY (Boxing lifetime increased)

### 68. WHAT MUST NOT HAPPEN

Нельзя: заставлять отмечать одну тренировку несколько раз; требовать сложные формы для простых действий; заставлять пользователя понимать внутреннюю data architecture; превращать Life Areas в ежедневную бюрократию; смешивать Activity и Goal; смешивать Habit и Activity так, что пользователь не понимает разницу; считать Partial как полный failure; автоматически переносить бесконечные Overdue tasks на Today; хранить независимые totals, которые могут рассинхронизироваться; делать Skills отдельной ежедневной системой ручного учёта.

### 69. NETWORKING

Полностью исключены: contacts CRM, companies, relationship database, reminders to contact people, professional networking management — отдельный продуктовый домен. Relationships в Life Areas означает жизненную сферу и цели пользователя, а не CRM.

### 70. ОСНОВНЫЕ ЭКРАНЫ

Primary: 1. Today, 2. Goals, 3. Plan, 4. Progress.

Secondary: 5. My Vision, 6. Life Plan, 7. Life Area, 8. Goal Details, 9. Activity Details, 10. Task Details, 11. Habit Details, 12. Project Details, 13. Session Details, 14. History, 15. Personal Progress History, 16. Skills, 17. Weekly Review, 18. Inbox, 19. Search, 20. Archive, 21. Quick Add, 22. Settings, 23. Auth, 24. Onboarding.

### 71. GOALS SCREEN

Верхний уровень Life Plan (Health/Finance/Career/Relationships/Development/Lifestyle), ниже All Goals/Active/Paused/Completed — цель видна либо в контексте всей жизни, либо обычным списком.

### 72. PROGRESS SCREEN

Анализ по Overall, Life Areas, Goals, Activities, Habits, Tasks; периоды Week/Month/Year/All Time; drill down Progress → Health → Boxing → Session History.

### 73. SIMPLE DAILY EXPERIENCE

Главное правило: количество функций не должно определять сложность ежедневного использования. Каждая новая функция проходит два вопроса: (1) помогает ли достигать целей? (2) добавляет ли лишнее ежедневное действие? Если полезна, но добавляет действие — сначала автоматизировать, вывести из существующих данных, скрыть через progressive disclosure, использовать smart defaults, объединить с существующим действием.

### 74. PRODUCT DIFFERENTIATION

Todo App: «You have 12 tasks.» Stepwise: «These 3 actions have the strongest impact on your important goals.»
Todo App: «Task completed.» Stepwise: «Task completed → Goal progress changed → Pace changed → Forecast changed → Life Plan changed.»
Habit Tracker: «12 day streak.» Stepwise: «This habit contributed 6 hours toward your actual long-term Goal.»
Calendar: «Boxing 19:00.» Stepwise: «Boxing 19:00 → Actual Session → Goal → Statistics → Forecast → Life Area.»

### 75. КЛЮЧЕВОЕ ОБЕЩАНИЕ STEPWISE

> Turn your goals into action.
> Stepwise connects what you want tomorrow with what you do today.
> Powerful underneath. Simple every day.

### 76. FINAL PRODUCT LOOP

> MY VISION → LIFE AREAS → GOALS → PLAN → TODAY → DO → TRACK → PROGRESS → ANALYZE → LIFE BALANCE → ADJUST → ACHIEVE → NEW GOALS

Вся история сохраняется. Stepwise постепенно становится не только системой планирования, но и цифровой историей развития человека.

### 77. ВАЖНО ДЛЯ ДАЛЬНЕЙШЕЙ РАЗРАБОТКИ

При дальнейшей работе нельзя произвольно удалять, упрощать или переосмысливать функции из этого документа. Если техническая реализация требует изменения концепции: (1) объяснить проблему, (2) показать затронутые функции и consumers, (3) предложить варианты, (4) дождаться выбора, (5) только потом менять архитектуру. Техническая последовательность разработки не означает сокращение функциональности продукта — все части проектируются как элементы одного полноценного приложения. При любых изменениях архитектуры учитывать: все связанные entities, all consumers, migrations, historical data, offline sync, idempotency, recalculation, regression impact, UX simplicity.

Главный критерий: Stepwise может быть сложным внутри, но для пользователя должен оставаться простым, быстрым и логичным каждый день.

## Product/engineering decisions made so far (outside the concept text)

- **Primary client: native Android — Kotlin + Jetpack Compose — released on Google Play; iOS built later, path TBD (likely Kotlin Multiplatform sharing the domain/data layer — kept deliberately open, see `/ARCHITECTURE.md` §27 dependency rules).** This supersedes the earlier React Native/Expo decision (`/docs/android-stack.md`, `/docs/android-architecture.md` — kept for history only). The full technical architecture is `/ARCHITECTURE.md`. The Next.js web app from Phase 0 stays in the repo but is parked, not the active development focus.
- **Backend**: Supabase (Postgres + Auth + Realtime + RLS) — chosen specifically because the relational model fits the multi-goal/multi-metric calculation engine and the source-of-truth requirement below; reasoning in the spec's Architecture Decision Record.
- **Source of truth (§62)**: Sessions/Progress Events only. No manually-maintained totals anywhere — Goal progress, Life Area aggregates, and every period rollup are derived views, never independently stored/editable numbers.
- **Idempotency (§60)** and **recomputation on edit/delete (§61)** are binding architecture constraints on the Sessions/Progress Events pipeline, not later hardening.
- **Internationalization**: multi-language from day one, no hardcoded UI strings — `i18next`/`react-i18next`, starting with Russian + English.
- **Units of measurement**: user-configurable per-account setting (metric/imperial, currency), never hardcoded — data stored in one canonical unit, converted for display.
- **Monetization**: decided later (possibly ads); not built now, but architecture shouldn't foreclose it.
- **Legal/jurisdiction**: resolved before the public production Play Store release, not before development starts.
- **Audience**: a public product for other people, not personal-use-only — confirms the existing multi-user design (Supabase Auth + per-user RLS).
- **Scope: no MVP phasing (§77).** The full concept above is the target for the build — not a cut-down core loop. Build order and screen-by-screen scope are tracked in `/docs/scope-of-work.md`, which must be kept in sync whenever this file changes.
- **Build order**: see `/PROJECT_STATE.md` for the live, detailed phase-by-phase status — not duplicated here to avoid two places tracking the same thing drifting apart.
