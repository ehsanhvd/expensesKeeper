package com.codex.expensekeeper;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.List;

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
        JalaliDate.Period period = JalaliDate.periodFor(now, store.periodStartDay());

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.expense_widget);
        boolean fa = "fa".equals(store.language());
        views.setTextViewText(R.id.widget_period_label, context.getString(R.string.period));
        views.setTextViewText(R.id.widget_period_value, ExpenseStore.money(store.totalBetween(period.start, period.end), fa));
        views.setTextViewText(R.id.widget_period_hint, period.label(fa));
        UncategorisedSummary uncategorised = uncategorisedSummary(store, period.start, period.end);
        views.setTextViewText(R.id.widget_uncategorized_label, context.getString(R.string.uncategorized));
        views.setTextViewText(R.id.widget_uncategorized_count, uncategorisedCount(uncategorised.count, fa));
        views.setTextViewText(R.id.widget_uncategorized_sum, ExpenseStore.money(uncategorised.sum, fa));
        Intent launch = new Intent(context, MainActivity.class);
        launch.putExtra(MainActivity.EXTRA_FROM_WIDGET, true);
        launch.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pi);
        views.setOnClickPendingIntent(R.id.widget_period, pi);
        views.setOnClickPendingIntent(R.id.widget_uncategorized, pi);
        manager.updateAppWidget(id, views);
    }

    private static UncategorisedSummary uncategorisedSummary(ExpenseStore store, long start, long end) {
        UncategorisedSummary summary = new UncategorisedSummary();
        List<ExpenseStore.Expense> expenses = store.expenses();
        for (ExpenseStore.Expense expense : expenses) {
            if (expense.investment || !expense.isUncategorized()) continue;
            if (expense.time < start || expense.time >= end) continue;
            summary.count++;
            summary.sum += expense.amount;
        }
        return summary;
    }

    private static String uncategorisedCount(int count, boolean fa) {
        String value = ExpenseStore.localNumber(count, fa);
        return fa ? value + " مورد" : value + (count == 1 ? " item" : " items");
    }

    private static class UncategorisedSummary {
        int count;
        long sum;
    }
}
