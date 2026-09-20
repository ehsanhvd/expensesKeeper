package com.codex.expensekeeper;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.RemoteViews;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Calendar;
import java.util.Set;

public class SpendingSpeedWidgetProvider extends AppWidgetProvider {
    static final String EXTRA_PACE = "com.codex.expensekeeper.SPENDING_PACE";

    static SpendingPace pace(ExpenseStore store, long now) {
        long[] boundaries = new long[64];
        long[] days = new long[63];
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        for (int i = 0; i < boundaries.length; i++) {
            boundaries[i] = calendar.getTimeInMillis();
            calendar.add(Calendar.DAY_OF_YEAR, -1);
        }
        long oldest = now;
        Set<String> excluded = store.excludedCategoryIds();
        for (ExpenseStore.Expense expense : store.expenses()) {
            if (expense.investment || expense.time > now) continue;
            long amount = expense.splits.isEmpty() ? expense.amount : 0;
            for (ExpenseStore.Split split : expense.splits) {
                if (!excluded.contains(split.categoryId)) amount += split.amount;
            }
            if (amount <= 0) continue;
            oldest = Math.min(oldest, expense.time);
            for (int i = 0; i < days.length; i++) {
                if (expense.time > boundaries[i + 1] && expense.time <= boundaries[i]) {
                    days[i] += amount;
                    break;
                }
            }
        }
        int historyDays = 0;
        while (historyDays < 63 && oldest <= boundaries[historyDays + 1]) historyDays++;
        return new SpendingPace(days, historyDays);
    }

    static String status(Context context, SpendingPace pace) {
        int label = pace.state == SpendingPace.LEARNING ? R.string.pace_learning
                : pace.state == SpendingPace.GREEN ? R.string.pace_green
                : pace.state == SpendingPace.HIGH ? R.string.pace_high : R.string.pace_steady;
        return context.getString(label);
    }

    static String comparison(Context context, SpendingPace pace, boolean fa) {
        if (pace.state == SpendingPace.LEARNING) return context.getString(R.string.pace_keep_logging);
        String percent = String.valueOf(pace.percentChange());
        if (fa) percent = ExpenseStore.toPersianDigits(percent);
        return context.getString(pace.dailyAverage <= pace.usualDaily
                ? R.string.pace_below : R.string.pace_above, percent);
    }

    static String explanation(Context context, ExpenseStore store) {
        SpendingPace pace = pace(store, System.currentTimeMillis());
        boolean fa = "fa".equals(store.language());
        String message = status(context, pace) + "\n" + comparison(context, pace, fa)
                + "\n\n" + context.getString(R.string.pace_recent,
                ExpenseStore.money(Math.round(pace.dailyAverage), fa));
        if (pace.state != SpendingPace.LEARNING) {
            message += "\n" + context.getString(R.string.pace_usual,
                    ExpenseStore.money(Math.round(pace.usualDaily), fa),
                    fa ? ExpenseStore.toPersianDigits(String.valueOf(pace.baselineWeeks)) : String.valueOf(pace.baselineWeeks))
                    + "\n\n" + context.getString(R.string.pace_target,
                    ExpenseStore.money(Math.round(pace.usualDaily * 7 * .95), fa));
        }
        return message + "\n\n" + context.getString(pace.state == SpendingPace.LEARNING
                ? R.string.pace_learning_detail : R.string.pace_detail);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, SpendingSpeedWidgetProvider.class));
        for (int id : ids) update(context, manager, id);
    }

    private static void update(Context context, AppWidgetManager manager, int id) {
        ExpenseStore store = new ExpenseStore(context);
        boolean fa = "fa".equals(store.language());
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(new Locale(store.language()));
        Context localizedContext = context.createConfigurationContext(configuration);
        SpendingPace pace = pace(store, System.currentTimeMillis());

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.spending_speed_widget);
        views.setInt(R.id.speed_widget_root, "setBackgroundResource", backgroundFor(pace.state));
        views.setImageViewBitmap(R.id.speed_widget_gauge, drawGauge(pace.needle, pace.state == SpendingPace.LEARNING));
        views.setTextViewText(R.id.speed_widget_title, status(localizedContext, pace));
        views.setTextViewText(R.id.speed_widget_value, localizedContext.getString(R.string.pace_per_day,
                compactAmount(Math.round(pace.dailyAverage), fa)));
        views.setTextViewText(R.id.speed_widget_caption, comparison(localizedContext, pace, fa));
        views.setContentDescription(R.id.speed_widget_root,
                explanation(localizedContext, store));

        Intent launch = new Intent(context, SpendingPaceActivity.class);
        launch.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 10000 + id, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.speed_widget_root, pendingIntent);
        manager.updateAppWidget(id, views);
    }

    private static int backgroundFor(int state) {
        if (state == SpendingPace.LEARNING) return R.drawable.speed_widget_bg_learning;
        if (state == 0) return R.drawable.speed_widget_bg_green;
        if (state == 1) return R.drawable.speed_widget_bg_yellow;
        return R.drawable.speed_widget_bg_red;
    }

    private static Bitmap drawGauge(float speed, boolean learning) {
        int width = 360;
        int height = 174;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(25f);
        RectF arc = new RectF(42f, 27f, width - 42f, 303f);

        paint.setColor(Color.argb(45, 255, 255, 255));
        canvas.drawArc(arc, 180f, 180f, false, paint);
        drawSegment(canvas, paint, arc, 180f, 57f, learning ? Color.LTGRAY : Color.rgb(91, 231, 160));
        drawSegment(canvas, paint, arc, 241.5f, 57f, learning ? Color.LTGRAY : Color.rgb(255, 214, 92));
        drawSegment(canvas, paint, arc, 303f, 57f, learning ? Color.LTGRAY : Color.rgb(255, 116, 129));

        float centerX = width / 2f;
        float centerY = height - 9f;
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeWidth(3f);
        for (int i = 0; i <= 12; i++) {
            double angle = Math.toRadians(180d + (i * 15d));
            float outer = 126f;
            float inner = i % 3 == 0 ? 105f : 113f;
            paint.setColor(Color.argb(i % 3 == 0 ? 225 : 125, 255, 255, 255));
            canvas.drawLine(centerX + (float) Math.cos(angle) * inner,
                    centerY + (float) Math.sin(angle) * inner,
                    centerX + (float) Math.cos(angle) * outer,
                    centerY + (float) Math.sin(angle) * outer, paint);
        }

        double needleAngle = Math.toRadians(180d + (clamp(speed) * 180d));
        float needleLength = 91f;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(9f);
        paint.setColor(Color.argb(70, 0, 0, 0));
        canvas.drawLine(centerX + 3f, centerY + 4f,
                centerX + 3f + (float) Math.cos(needleAngle) * needleLength,
                centerY + 4f + (float) Math.sin(needleAngle) * needleLength, paint);
        paint.setColor(Color.WHITE);
        canvas.drawLine(centerX, centerY,
                centerX + (float) Math.cos(needleAngle) * needleLength,
                centerY + (float) Math.sin(needleAngle) * needleLength, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(centerX, centerY, 16f, paint);
        paint.setColor(Color.argb(150, 20, 24, 32));
        canvas.drawCircle(centerX, centerY, 7f, paint);
        return bitmap;
    }

    private static void drawSegment(Canvas canvas, Paint paint, RectF arc, float start, float sweep, int color) {
        paint.setColor(color);
        canvas.drawArc(arc, start, sweep, false, paint);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static String compactAmount(long amount, boolean fa) {
        double display = amount;
        String suffix = "";
        if (amount >= 1_000_000_000L) {
            display = amount / 1_000_000_000d;
            suffix = "B";
        } else if (amount >= 1_000_000L) {
            display = amount / 1_000_000d;
            suffix = "M";
        } else if (amount >= 1_000L) {
            display = amount / 1_000d;
            suffix = "K";
        }
        DecimalFormat format = new DecimalFormat(display >= 100 || suffix.isEmpty() ? "#,##0" : "0.#",
                new DecimalFormatSymbols(Locale.US));
        String value = format.format(display) + suffix;
        return fa ? ExpenseStore.toPersianDigits(value) : value;
    }
}
