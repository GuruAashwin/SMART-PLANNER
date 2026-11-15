# Smart Planner

A personal Android app for tasks, checklists, reminders, alarms, recurring schedules and habit tracking. Everything is stored on the phone (Room database), with no account and no internet needed.

## Install on your phone
1. Open the **Releases** section of this repo (or the latest run under **Actions**) and download `SmartPlanner.apk`.
2. Open the file on your phone. Allow "Install unknown apps" for your browser or file manager when asked.
3. Open Smart Planner, enter your name, and allow notifications.
4. In **Settings → Permissions**, tap **Fix** on anything marked red, especially **Unrestricted battery** on Xiaomi, Oppo, Vivo, Realme or Samsung phones.

Every push to this repo builds a new APK automatically. It's signed with the same key every time, so it installs as an update and keeps your data.

## Features
- **Home:** greeting, today's progress ("6 of 10 tasks completed"), next task, overdue tasks, today's habits
- **Tasks:** title, date, time, priority (High/Medium/Low, shown with an icon and text), category, notes, checklist
  - edit, delete, duplicate, postpone, reschedule, complete
  - the parent task completes automatically when every checklist item is done; completing the parent asks about the remaining items
- **Reminders:** several per task (5 min to 1 day before, or a custom time), shown as notifications with **Complete** / **Snooze** buttons
- **Alarms:** full-screen alarm over the lock screen with sound and vibration, plus **Snooze** (5/10/15/30/60 min), **Complete** and **Dismiss**
- **Recurring tasks:** daily, selected weekdays, weekly, monthly, every N days. Only the rule is stored, and the next occurrence is created when one is completed.
- **Overdue handling:** unfinished tasks never disappear and become **Overdue**. A recurring occurrence left undone is kept in history as *Missed*, and the next one is created at the start of your day.
- **Habits:** streak, best streak, this week, 30-day consistency; tap any of the last 7 days to log it
- **Calendar:** month view with dots on busy days; tap a day to see or add its tasks and log habits
- **Search and filters:** Today / Upcoming / Overdue / No date / Completed, plus priority and category
- **Quick add (natural language):** "Study Python today at 7 PM", "Gym tomorrow at 6:30 am", "Call mom on friday". It shows what it understood before saving, and asks AM or PM when that's unclear.
- **Settings:** name, light/dark/system theme, default reminder, snooze length, start of day, alarm sound, categories, permission checks, JSON backup export/restore

## How scheduling works (reliability)
- Alerts use Android `AlarmManager` (`setExactAndAllowWhileIdle`, `setAlarmClock` for alarms). No background service runs between alerts.
- Alarms are restored after a reboot, an app update, or a time or time-zone change (`BootReceiver`).
- Alerts that were missed while the phone was off don't fire later as duplicates. The task just shows as overdue.
- Completing a task cancels all its pending reminders and its alarm. A stale alert for a completed or deleted task is ignored.

## Build it yourself
Open the folder in Android Studio and press Run, or run `./gradlew assembleRelease`.
