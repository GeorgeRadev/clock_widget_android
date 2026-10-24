package org.widget.clock;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ClockWidgetProvider extends AppWidgetProvider {

    // Roughly two launcher rows: below this the widget is one row tall and
    // only the time is shown. Cell size varies per launcher, so this is an
    // approximation that works across common ones.
    private static final int DATE_VISIBLE_MIN_HEIGHT_DP = 120;

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        // Refresh when the clock changes (time, timezone, locale).
        if (Intent.ACTION_TIME_CHANGED.equals(intent.getAction())
                || Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())
                || Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) {
            updateAllWidgets(context);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
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
    public void onResize(Context context, int appWidgetId, Bundle options) {
        super.onResize(context, appWidgetId, options);
        updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, int appWidgetId, Bundle appWidgetOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetId, appWidgetOptions);
        updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId);
    }

    private static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, ClockWidgetProvider.class);
        int[] appWidgetIds = manager.getAppWidgetIds(component);
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, manager, appWidgetId);
        }
    }

    private static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // Min height (dp) is the safe side: text sized to fit the smallest
        // allowed height also fits when the user stretches the widget.
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

        // Scale font with the available height.
        int timeSize = showDate
                ? clamp(minHeightDp * 3 / 10, 26, 72)   // room for two lines
                : clamp(minHeightDp * 9 / 20, 24, 64);  // time fills the row
        int dateSize = clamp(minHeightDp / 6, 14, 40);
        views.setInt(R.id.widget_time, "setTextSize", timeSize);
        views.setInt(R.id.widget_date, "setTextSize", dateSize);

        views.setOnClickPendingIntent(R.id.widget_time, buildClockIntent(context, appWidgetId));
        views.setOnClickPendingIntent(R.id.widget_date, buildCalendarIntent(context, appWidgetId));

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static PendingIntent buildClockIntent(Context context, int appWidgetId) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .setClassName("com.android.deskclock", "com.android.deskclock.DeskClock");
        if (intent.resolveActivity(context.getPackageManager()) == null) {
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
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}
