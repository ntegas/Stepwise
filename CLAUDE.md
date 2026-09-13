# Stepwise — Project Memory

This file is the canonical, living product spec for Stepwise. It is auto-loaded at the start of every Claude Code session opened against this repository, so it should always be read and followed before acting on any request — regardless of which chat/session is doing the work. Extend this file in place as the concept evolves; do not let the spec live only in a chat conversation.

Supporting documents (read alongside this file):
- `/docs/functional-analysis.md` — the 48 sections below grouped into implementation modules
- `/docs/data-model.md` — entities, fields, relationships, and the automatic-cascade rule
- `/docs/architecture.md` — stack choice (Supabase + Next.js), repo layout, web/Android split

## Non-negotiable principle

**Powerful underneath. Simple every day.** Every new feature must be checked against two questions: (1) does it help the person achieve their goals? (2) does it add an extra action to the daily scenario? If (2) is true, find a way to automate or hide the complexity — never push it onto the user.

## Full concept (source of truth, verbatim)

> Фиксирую обновлённую концепцию Stepwise с новым обязательным принципом: сложная система внутри, максимально простое ежедневное использование снаружи.

# STEPWISE — полная концепция

## 1. Что такое Stepwise

Stepwise — система достижения целей, которая связывает долгосрочные намерения человека с его реальными ежедневными действиями.

Основная цепочка:

Vision → Goal → Milestones → Project/Plan → Tasks & Activities → Today → Execution → Progress → Statistics → Analysis → Adjustment → Achievement

Ключевой вопрос приложения:

> Что мне нужно сделать сегодня, чтобы реально приблизиться к своим целям?

Пользователь не должен вручную управлять всей этой цепочкой. Большая её часть работает автоматически.

## 2. Главный UX-принцип

Сложная система внутри. Простое действие снаружи.

Это становится одним из фундаментальных требований Stepwise.

Приложение может иметь мощную внутреннюю архитектуру, но пользователь не должен ощущать её сложность.

80% ежедневного взаимодействия:

> Посмотреть → Выполнить → Отметить

Например утром:

> TODAY
> 🥊 Boxing · 1h
> 💻 Stepwise · 2h
> 🇬🇧 English · 30m
> 🧘 Meditation · 15m

После бокса пользователь нажимает:

> ✓

Всё.

Внутри Stepwise автоматически:

создаёт Session → записывает Actual 1h → обновляет Activity → Goal → Week → Month → Year → Lifetime → Statistics → Pace → Forecast.

Пользователь этого процесса не видит.

## 3. Основная навигация

Я бы пока зафиксировал четыре основных пространства:

TODAY | GOALS | PLAN | PROGRESS

плюс глобальная кнопка:

＋

Не нужно выводить каждую внутреннюю сущность приложения в отдельный tab.

## 4. Today

Это главный и наиболее часто используемый экран.

Показываем прежде всего то, что нужно сделать сегодня.

Например:

> Saturday, 13 September
>
> Focus
> 🚀 Finish Stepwise onboarding
>
> Today
> 🥊 Boxing · 19:00 · 1h
> 🇬🇧 English · 30m
> 🧘 Meditation · 15m
> 💻 Stepwise · 2h
>
> Today 3/5

Не нужно перегружать Today:

огромными графиками; полной статистикой; деревом навыков; всеми milestones; всеми целями; сложной аналитикой.

Для этого существуют другие экраны.

## 5. Быстрое выполнение

Стандартное действие:

Tap ✓ → Done

Если было запланировано:

> Boxing · 1h

Stepwise предполагает:

> Actual = 1h

и автоматически записывает час.

Дополнительные варианты не должны постоянно занимать экран.

Через swipe / long press / меню:

Partial, Missed, Reschedule, Cancel

## 6. Статусы выполнения

Полная система остаётся:

✓ Done — выполнено.
◐ Partial — выполнено частично.
○ Missed / Not Done — не выполнено.
→ Rescheduled — перенесено.
◌ In Progress — начато.
× Cancelled — отменено.

Но пользователю не показываем одновременно шесть огромных кнопок.

## 7. Частичное выполнение

Это важная особенность Stepwise.

Например:

> Boxing
> Planned: 60 min

Пользователь занимался 40 минут.

Выбирает:

> Partial → 40 min

Получаем:

> Planned: 60 min
> Actual: 40 min
> Status: Partial

Именно 40 минут идут в статистику и цель.

Мы не превращаем полезную работу в «0», только потому что пользователь не сделал 100% плана.

## 8. Planned vs Actual

Stepwise хранит две разные величины:

Planned и Actual

Например:

> 💻 Stepwise Development
> Planned: 3h
> Actual: 2h 10m
> 72% executed

Это фундамент для качественной аналитики.

## 9. Goals

Цель отвечает на вопрос:

> Чего я хочу достичь?

Примеры:

> 🥊 100 Hours Boxing — 72 / 100 h
> 📚 Read 20 Books — 7 / 20
> 🏃 Run 500 km — 183 / 500 km
> 💰 Save €30,000 — €8,400 / €30,000
> ⚖️ Weight — 92 → 85 kg
> 🚀 Launch Stepwise — 68%

## 10. Типы целей

Не заставляем все цели работать по одной математике.

Cumulative — Накапливаем результат: 72 / 100 hours
Quantity — 7 / 20 books
Distance — 183 / 500 km
Financial — €8,400 / €30,000
Target value — 92 → 85 kg
Frequency — 3 workouts/week
Percentage / project — 68 / 100%

Каждый тип имеет собственную логику расчёта.

## 11. Простое создание цели

При этом нельзя заставлять пользователя заполнять двадцать полей.

Базовое создание:

> Goal: 100 Hours Boxing
> Target: 100 h
> Deadline: Dec 31

Готово.

Дополнительные настройки находятся в:

> More options

Там уже: milestones, priority, project, schedule, category, description и т.д.

## 12. Milestones

Сложные цели разбиваются на этапы.

Например, Launch Stepwise:

> Specification ✓
> UI/UX ✓
> Development 73%
> Testing 21%
> Store preparation 0%
> Launch 0%

Для простой цели вроде 100 Hours Boxing milestones вообще могут не понадобиться.

То есть Stepwise не заставляет пользователя пользоваться сложностью, которая ему не нужна.

## 13. Projects

Для сложных целей:

Goal ↓ Projects ↓ Tasks

Например, Launch Stepwise:

↳ Product Design
↳ Development
↳ Testing
↳ Marketing
↳ Launch

Но Projects не должны быть обязательными.

Для «Read 20 books» создавать Project бессмысленно.

## 14. Tasks

Task — конкретное действие.

Например: «Finish onboarding screen»

У задачи могут быть: Goal; Project; date; time; deadline; duration; priority; reminder; recurrence; metric; planned result; actual result; status.

Но большинство этих полей опциональны.

Быстрая задача:

> Finish onboarding → Tomorrow

и готово.

## 15. Activities

Activity — постоянная деятельность человека.

Например: 🥊 Boxing, 💻 Programming, 🇬🇧 English, 📚 Reading, 🧘 Meditation, 🏃 Running

Это не одноразовая задача.

Activity существует независимо от конкретной цели.

## 16. Goal и Activity — разные сущности

Например:

Activity: Boxing
Goal: 100 Hours Boxing

После достижения:

> 100/100 ✓

цель закрывается.

Но Activity продолжает существовать:

> Boxing — Lifetime: 347h

Можно поставить следующую цель:

> 500 Hours Boxing

История не обнуляется.

## 17. Sessions

Каждое фактическое занятие создаёт Session.

Например:

> Boxing
> 13 Sep — Planned: 60m, Actual: 60m, ✓ Done

Другой день:

> 15 Sep — Planned: 60m, Actual: 42m, ◐ Partial

Все Sessions формируют реальную историю Activity.

## 18. Автоматические связи

Это один из ключевых UX-принципов.

Если пользователь один раз связал:

> Boxing → 100 Hours Boxing

ему больше не нужно выбирать Goal при каждом занятии.

Он просто выполняет:

> Boxing ✓

Stepwise сам понимает:

> +1h Boxing
> +1h → 100 Hours Boxing

## 19. Никакого двойного ввода

Это обязательное правило.

Пользователь не должен:

1. отметить тренировку в Calendar;
2. потом записать её в Activity;
3. потом обновить Goal;
4. потом отметить Habit.

Одно фактическое действие должно создавать один Progress Event, от которого обновляется всё остальное.

## 20. Calendar / Plan

Полноценное планирование:

Day | Week | Month | Year

В календаре: Tasks; Activities; Habits; deadlines; milestones; events.

Например, Tuesday:

> 09:00 Work
> 18:00 English · 30m
> 19:00 Boxing · 1h

## 21. Recurring Activities

Пользователь один раз создаёт:

> Boxing — Tuesday + Thursday — 19:00 — 1h

Дальше расписание создаётся автоматически.

Не нужно каждую неделю заново создавать Boxing.

## 22. Habits

Привычки:

> Meditation · 15m/day
> Reading · 30m/day
> Water · 2.5L/day
> English · 30m/day

Могут быть связаны с Activity и Goal.

Например:

Goal: 200 Hours English
↓
Habit: English 30 min/day
↓
Completed 30m
↓
+30m English
↓
Goal automatically updated

## 23. Habits тоже поддерживают Partial

Например, Reading — 30m. Результат: 30/30 ✓, или 18/30 ◐, или 0/30 ○.

18 минут не исчезают из статистики.

## 24. Timer

Для временных Activities:

> START — 00:00:00 ↓ STOP — 01:17:32 — Save

Stepwise записывает: +1h 17m

Но таймер не обязателен. Можно просто: Enter manually → 1h 20m

## 25. Progress

Это аналитический центр.

Основные периоды: WEEK | MONTH | YEAR | ALL TIME

Например, THIS WEEK:

> 🥊 Boxing — 3h 20m
> 💻 Stepwise — 11h 45m
> 🇬🇧 English — 4h 30m
> 📚 Reading — 2h 10m
> 🧘 Meditation — 1h 05m

## 26. Planned vs Actual Statistics

Например, THIS WEEK:

> Planned: 27h
> Actual: 22h 50m
> Execution: 84.6%

Дополнительно:

> Done — 23, Partial — 4, Missed — 3, Rescheduled — 1, Cancelled — 1

## 27. Week / Month / Year / All Time

Это работает для Goals, Activities, Habits и других измеримых данных.

Например, Boxing: Week 3h 20m, Month 12h 40m, Year 103h 15m, All Time 347h 42m.

## 28. Activity Statistics

Карточка Boxing может показывать:

> 🥊 BOXING
> Goal: 72/100h
> This week: 3h 20m
> This month: 12h 40m
> This year: 103h
> Lifetime: 347h
> Sessions: 286
> Average session: 1h 13m
> Average/week: 3.1h

## 29. History

Полная история, например September:

> Sep 13 — 1h 20m ✓
> Sep 11 — 1h 30m ✓
> Sep 8 — 40m ◐
> Sep 6 — Missed
> Sep 4 — 1h ✓

Любую запись можно открыть. Если Actual был введён неправильно — исправить. Все связанные показатели пересчитываются.

## 30. Графики

Показываем динамику: Day → Week → Month → Year

Например: Jan 8h, Feb 11h, Mar 14h, Apr 9h, May 13h

Также: trend; calendar heatmap; consistency; cumulative progress.

Но графики находятся в Progress, а не мешают ежедневному Today.

## 31. Сравнение периодов

Например: Boxing — 12h 40m this month — ↑ 22% vs last month

или: Development — 38h — ↓ 7h vs previous month

Так Stepwise показывает направление движения.

## 32. Pace

Для целей с дедлайном:

> 100 Hours Boxing
> 72 / 100h
> Remaining: 28h
> Required: 2.1h/week
> Current: 3.0h/week
> 🟢 Ahead

Или: 🔴 Behind by 1.4h/week

## 33. Forecast

Stepwise прогнозирует дату достижения.

Например:

> Current pace
> Estimated completion: December 17
> Deadline: December 31
> 🟢 14 days ahead

Если темп падает:

> Estimated: January 21
> 🔴 21 days behind

## 34. Goal Impact

Не все задачи одинаково важны. Stepwise должен понимать связь действия с целью.

Например:

> 🔴 Finish payment integration — High Goal Impact
> 🟡 English lesson — Medium
> ⚪ Clean inbox — Low

Today может поднимать вверх действительно значимые действия.

Именно здесь появляется отличие: не «что у меня сегодня запланировано?», а «что сегодня сильнее всего двигает меня вперёд?»

## 35. Skills

Skills сохраняем, но не превращаем их в отдельную ежедневную обязанность.

Например: Product Development, Programming, English, Public Speaking

Skill может быть связан с Activities и Goals.

Но пользователь не должен после часа программирования делать: «Programming Skill +2%.»

Stepwise уже знает, что он занимался программированием.

Навыки должны максимально развиваться из существующих данных, а не требовать дополнительного учёта.

## 36. Personal Progress History

Со временем Stepwise становится персональной базой достижений.

Например, 2026:

> 🥊 Boxing — 126h
> 💻 Development — 684h
> 🇬🇧 English — 193h
> 📚 Books — 31
> 🧘 Meditation — 82h
> Goals completed — 14
> Projects completed — 9
> Overall execution — 81%

А через несколько лет:

> BOXING
> Lifetime: 864h
> Sessions: 691
> Started: March 2025
> Goals completed: 6

Это уже не todo history, а история развития человека.

## 37. Аналитика поведения

Stepwise анализирует накопленные данные.

Например: «Ты планируешь в среднем 5 тренировок в неделю, но выполняешь 3.2.»

Или: «За последние четыре недели время на Stepwise снизилось с 14h/week до 8h/week.»

Или: «Ты выполняешь 91% задач утром и только 54% после 19:00.»

Или: «На эту цель запланировано много задач, но фактический прогресс почти не меняется.»

## 38. Интеллектуальные рекомендации

Здесь автоматизация действительно имеет смысл.

Не универсальный чат ради модного слова AI, а анализ данных пользователя.

Например: «Goal deadline at risk.»

Причина: «Required: 5.1h/week, Current: 3.2h/week»

Предложение: «Add approximately 2h/week.»

И пользователь может принять изменение плана.

## 39. Quick Add

Кнопка ＋ должна быть доступна практически везде.

Через неё: Task, Goal, Activity, Habit, Session, Project, Progress

Но список можно делать контекстным.

Например внутри Boxing кнопка + сразу предполагает: «Add Boxing Session», а не заставляет снова выбирать Boxing.

## 40. Быстрое создание

Это ещё одно обязательное UX-правило.

Не делать форму из 15 обязательных полей.

Например задача: «Call designer — Tomorrow» → Save.

А уже при необходимости: More options, где находятся: Goal, Project, Priority, Duration, Reminder, Repeat, Notes, Metric и т.д.

## 41. Progressive Disclosure

Это должен быть фундамент интерфейса.

Новичок видит простую систему. По мере необходимости открывает более глубокие функции.

Например, Basic: Boxing — 1 hour — Tuesday

Advanced: Goal association, recurrence, target metric, reminder, priority, forecast settings, notes

То есть мощность приложения не означает сложный первый экран.

## 42. Автоматизация

Stepwise должен самостоятельно делать максимум очевидной работы.

Если пользователь создал «Boxing — Tue/Thu — 1h» и связал «100 Hours Boxing», дальше система знает связь.

Не спрашивать каждую неделю одно и то же.

## 43. Умные defaults

Если запланировано «Boxing — 1h» и пользователь нажал ✓: Actual = 1h.

Не открываем дополнительную форму.

Если результат отличается — пользователь сам выбирает Partial/Edit.

Это значительно сокращает количество действий.

## 44. Что пользователь делает сам

В идеале всего четыре типа действий:

Plan — Что я хочу сделать.
Do — Сделать.
Mark — Зафиксировать результат.
Review — Иногда посмотреть прогресс и скорректировать направление.

Всё остальное Stepwise должен максимально вычислять самостоятельно.

## 45. Что Stepwise делает автоматически

На основании одного действия система может: сохранить Session, обновить Actual, определить Status, обновить Activity, обновить Goal, обновить Milestone, обновить Week, обновить Month, обновить Year, обновить Lifetime, пересчитать Execution Rate, пересчитать Pace, пересчитать Forecast, обновить Charts, обновить Comparison, обновить Personal History.

Это очень важное архитектурное требование:

> Один пользовательский ввод → множество автоматических последствий.

## 46. Networking отсутствует

Networking полностью исключён из Stepwise.

Нет: CRM; contacts management; companies; relationship tracking; networking reminders.

Это отдельная продуктовая задача и она разрушала бы фокус Stepwise.

## 47. Главная петля продукта

В итоге Stepwise работает так:

GOAL — «Я хочу 100 часов бокса.»
↓
PLAN — «2 раза в неделю.»
↓
TODAY — «Boxing · 19:00 · 1h»
↓
DO — Пользователь тренируется.
↓
✓ — Одно нажатие.
↓
MEASURE — «Actual +1h»
↓
PROGRESS — «72 → 73h»
↓
STATISTICS — «Week / Month / Year / Lifetime»
↓
ANALYZE — «Current pace 3h/week»
↓
FORECAST — «Goal completion Dec 17»
↓
ADJUST — При необходимости меняем план.
↓
ACHIEVE — «100 / 100h ✓»
↓
Activity продолжает жить: «Boxing Lifetime: 375h»

## 48. Продуктовая формула Stepwise

> GOAL → PLAN → DO → TRACK → ANALYZE → ADJUST → ACHIEVE

А пользовательское обещание можно выразить ещё проще:

> Turn your goals into action.

И главный UX-принцип:

> Powerful underneath. Simple every day.

С этого момента каждая новая функция Stepwise проверяется двумя вопросами: (1) помогает ли она человеку достигать целей? (2) добавляет ли она лишнее действие в ежедневный сценарий? Если второе — сначала ищем способ автоматизировать или спрятать сложность, а не перекладываем её на пользователя.

## Product/engineering decisions made so far (outside the concept text)

- **Access requirement**: usable from phone and computer with shared data, no local binding. User will build a native Android app separately later; this repo currently covers the shared backend + web client.
- **Backend**: Supabase (Postgres + Auth + REST/Realtime + RLS) — one backend reachable identically from web now and Android later.
- **Web**: Next.js (TypeScript), deployed to Vercel.
- **Derived calculations** (execution rate, pace, forecast, rollups) live in Postgres views/functions, not duplicated in app code per client, so every client reads the same numbers.
- **Build order**: Phase 0 = concept analysis + data model + architecture scaffold (this phase). Phase 1 = MVP core loop Today → Goals → Progress. Later phases = Calendar/Habits, then Milestones/Projects/Forecast UI/Skills/History editing.
