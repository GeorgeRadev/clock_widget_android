# Clock Widget

A minimal Android home-screen clock widget. Shows the current time (`HH:mm`, bold) and the date (`MMM dd`), rescales to fit whatever size you drag it to, and updates every minute without keeping any process alive.

## Features

- **Time + date display** — time in bold `HH:mm`, date below in `MMM dd` (locale-aware).
- **Size-aware layout** — font sizes scale with the widget height. If the widget is too short, the date row is hidden automatically and the time fills the space instead.
- **Minute-accurate refresh** — a self-rescheduling alarm keeps the displayed minute in sync with the wall clock.
- **Tap shortcuts**
  - tap the **time** → opens the system clock app (Samsung, Google, or AOSP DeskClock; falls back to a Play Store search if none is installed)
  - tap the **date** → opens today's view in the calendar app (falls back to a Play Store search)
- **No background service** — the app contains only a broadcast receiver; it does nothing until you place the widget.

## How the minute refresh works

On Android 12+ `updatePeriodMillis` is capped at 30 minutes for apps targeting API 31+, so the framework's periodic updates cannot drive a per-minute clock. Instead the widget uses a self-rescheduling `AlarmManager` alarm:

1. When a widget is placed (or after a reboot, via the system update broadcast), `onUpdate` schedules an `ELAPSED_REALTIME` alarm for the **next minute boundary** — only 1–60 s out, so the first tick never lags the real clock.
2. Each tick delivers a broadcast (`org.widget.clock.TICK`) that repaints all widget instances and reschedules the next alarm to the next minute boundary.
3. Because every reschedule targets a real `:00` boundary (rather than a flat +60 s), any lateness from the inexact-alarm batching window self-corrects on the next tick.
4. When the last widget is removed, `onDisabled` cancels the alarm.

The alarm uses `setAndAllowWhileIdle`, so ticks are delivered while the device is in Doze. Note that inexact alarms can be held by the system for up to ~45 s, so at worst the displayed minute lags by under a minute.

Time, timezone, and locale changes are picked up immediately via the standard `TIME_CHANGED` / `TIMEZONE_CHANGED` / `LOCALE_CHANGED` broadcasts.

## Project layout

```
app/src/main/java/org/widget/clock/ClockWidgetProvider.java   # the whole app
app/src/main/res/layout/clock_widget.xml                      # time + date text views
app/src/main/res/xml/clock_widget_info.xml                    # widget metadata
app/src/main/res/mipmap/...                                   # icon (derived from clock.svg)
```

Key constants in `ClockWidgetProvider`:

| Constant | Value | Meaning |
|---|---|---|
| `TIME_RATIO` | 0.95 | time font size / widget height, when the date is hidden |
| `TIME_DATE_RATIO` | 0.44 | time font size / widget height, when the date is shown |
| `DATE_RATIO` | 0.26 | date font size / widget height |
| `DATE_VISIBLE_MIN_HEIGHT_DP` | 120 | date row appears when the widget is at least this tall |

## Requirements

- minSdk **35** (Android 15), targetSdk **37**, compiled against API 37
- Java 11 source compatibility; build with JDK 21 and Android SDK Platform 37

## Build

```sh
./gradlew :app:assembleDebug
```

Install on a connected device/emulator:

```sh
./gradlew :app:installDebug
```

## Using the widget

1. On the home screen, long-press → **Widgets**.
2. Find **Clock Widget** and drag it onto the screen.
3. Resize it: a single-cell-tall widget shows only the time; at roughly 120 dp or taller the date appears.
4. Tap the time to open the clock app, tap the date to open the calendar.

## Notes

- `RemoteViews` text sizing must use `setFloat(viewId, "setTextSize", sp)`; the integer overload is rejected by `RemoteViews` and the widget fails to load.
- Size changes are delivered via `onAppWidgetOptionsChanged` (this SDK has no `onResize`), which triggers a repaint.
