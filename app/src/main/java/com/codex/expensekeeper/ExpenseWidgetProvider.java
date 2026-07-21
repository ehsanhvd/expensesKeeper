package com.codex.expensekeeper;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.Calendar;

public class ExpenseWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ExpenseWidgetProvider.class));
        for (int id : ids) update(context, manager, id);
    }

    private static void update(Context context, AppWidgetManager manager, int id) {
        ExpenseStore store = new ExpenseStore(context);
        long now = System.currentTimeMillis();
        Calendar day = Calendar.getInstance();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        Calendar week = (Calendar) day.clone();
        week.add(Calendar.DAY_OF_YEAR, -6);
        JalaliDate.Period period = JalaliDate.periodFor(now, store.periodStartDay());

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.expense_widget);
        boolean fa = "fa".equals(store.language());
        views.setTextViewText(R.id.widget_daily, context.getString(R.string.daily) + "\n" + ExpenseStore.money(store.totalBetween(day.getTimeInMillis(), now + 1), fa));
        views.setTextViewText(R.id.widget_weekly, context.getString(R.string.weekly) + "\n" + ExpenseStore.money(store.totalBetween(week.getTimeInMillis(), now + 1), fa));
        views.setTextViewText(R.id.widget_period, context.getString(R.string.period) + "\n" + ExpenseStore.money(store.totalBetween(period.start, period.end), fa));
        Intent launch = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_period, pi);
        manager.updateAppWidget(id, views);
    }
}
