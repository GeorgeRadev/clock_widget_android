package org.widget.clock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ClockWidgetProvider extends AppWidgetProvider {

    private static final int DATE_VISIBLE_MIN_HEIGHT_DP = 120;

    // Font sizes as a fraction of the widget height (in sp).
    private static final float TIME_RATIO = 0.95f;        // time, date hidden
    private static final float TIME_DATE_RATIO = 0.44f;   // time, date visible
    private static final float DATE_RATIO = 0.26f;        // date

    // Refresh cadence. Android 12+ ignores updatePeriodMillis below 30 min
    // for apps targeting API 31+, so a self-rescheduling alarm drives the
    // minute refresh instead. The first tick lands on the next minute
    // boundary so the widget never lags the real clock.
    private static final String ACTION_TICK = "org.widget.clock.TICK";
    private static final int TICK_REQUEST_CODE = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        // Refresh when the clock changes (time, timezone, locale).
        if (Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_LOCALE_CHANGED.equals(action)
                || ACTION_TICK.equals(action)) {
            if (ACTION_TICK.equals(action)) {
                // Re-align to the next minute boundary: alarms are inexact
                // and can land late, so a fixed +60 s reschedule would drift
                // further behind the wall clock with every tick.
                scheduleTick(context, delayUntilNextMinute());
            }
            updateAllWidgets(context);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // (Re)start the minute refresh; also covers the update broadcast
        // the system sends after a reboot, when alarms have been lost.
        scheduleTick(context, delayUntilNextMinute());
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onEnabled(Context context) {
        // First widget placed: make sure it shows the current time.
        updateAllWidgets(context);
    }

    @Override
    public void onDisabled(Context context) {
        // Last widget removed: no need to keep ticking.
        cancelTick(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId, Bundle appWidgetOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, appWidgetOptions);
        updateWidget(context, appWidgetManager, appWidgetId);
    }

    private static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, ClockWidgetProvider.class);
        int[] appWidgetIds = manager.getAppWidgetIds(component);
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, manager, appWidgetId);
        }
    }

    private static void scheduleTick(Context context, long delayMs) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + delayMs, tickPendingIntent(context));
    }

    /** 1-60 s until the wall clock rolls to the next minute. */
    private static long delayUntilNextMinute() {
        return (60 - (System.currentTimeMillis() / 1000) % 60) * 1000L;
    }

    private static void cancelTick(Context context) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(tickPendingIntent(context));
    }

    private static PendingIntent tickPendingIntent(Context context) {
        return PendingIntent.getBroadcast(context, TICK_REQUEST_CODE,
                new Intent(context, ClockWidgetProvider.class).setAction(ACTION_TICK),
                pendingFlags());
    }

    private static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // Scale fonts with the available height. Min height is the safe side:
        // text sized to the smallest allowed height also fits when the user
        // stretches the widget.
        Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
        int minHeightDp = options != null
                ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                : 0;
        boolean showDate = minHeightDp >= DATE_VISIBLE_MIN_HEIGHT_DP;

        Date now = new Date();
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.clock_widget);
        views.setTextViewText(R.id.widget_time, timeFormat.format(now));
        views.setTextViewText(R.id.widget_date, dateFormat.format(now));
        views.setInt(R.id.widget_date, "setVisibility", showDate ? View.VISIBLE : View.GONE);

        if (minHeightDp > 0) {
            // Font size = ratio of the widget height, clamped so extreme
            // widget sizes stay readable.
            // setFloat is required: RemoteViews rejects setTextSize(int),
            // the float overload defaults to sp.
            int timeSize = showDate
                    ? clamp((int) (minHeightDp * TIME_DATE_RATIO), 20, 72)
                    : clamp((int) (minHeightDp * TIME_RATIO), 24, 64);
            views.setFloat(R.id.widget_time, "setTextSize", timeSize);
            views.setFloat(R.id.widget_date, "setTextSize",
                    clamp((int) (minHeightDp * DATE_RATIO), 12, 40));
        }

        views.setOnClickPendingIntent(R.id.widget_time, buildClockIntent(context, appWidgetId));
        views.setOnClickPendingIntent(R.id.widget_date, buildCalendarIntent(context, appWidgetId));

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static PendingIntent buildClockIntent(Context context, int appWidgetId) {
        // No generic "clock app" intent exists, so try the known
        // implementations in order and use the first one installed.
        String[][] candidates = {
                {"com.sec.android.app.clockpackage", "com.sec.android.app.clockpackage.ClockPackage"},
                {"com.google.android.deskclock", "com.android.deskclock.DeskClock"},
                {"com.android.deskclock", "com.android.deskclock.DeskClock"},
                {"com.sec.deskclock", "com.sec.android.app.clocklauncher.ClockMainActivity"},
        };
        Intent intent = null;
        for (String[] candidate : candidates) {
            Intent candidateIntent = new Intent(Intent.ACTION_MAIN)
                    .setClassName(candidate[0], candidate[1]);
            if (candidateIntent.resolveActivity(context.getPackageManager()) != null) {
                intent = candidateIntent;
                break;
            }
        }
        if (intent == null) {
            // Fall back to Play Store search when no clock app is installed.
            intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://search?q=clock&c=apps"));
            intent.setPackage("com.android.vending");
        }
        return PendingIntent.getActivity(context, appWidgetId * 2, intent, pendingFlags());
    }

    private static PendingIntent buildCalendarIntent(Context context, int appWidgetId) {
        // URI authority routes to the calendar app; time/0 = today's view.
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("content://com.android.calendar/time/0"));
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://search?q=calendar&c=apps"));
            intent.setPackage("com.android.vending");
        }
        return PendingIntent.getActivity(context, appWidgetId * 2 + 1, intent, pendingFlags());
    }

    private static int pendingFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT
                | PendingIntent.FLAG_IMMUTABLE;
    }
}
