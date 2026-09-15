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

public class SpendingSpeedWidgetProvider extends AppWidgetProvider {
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final long WINDOW_MS = 30L * DAY_MS;

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
        long now = System.currentTimeMillis();
        long currentTotal = store.totalBetween(now - WINDOW_MS, now);
        long previousTotal = store.totalBetween(now - (2L * WINDOW_MS), now - WINDOW_MS);
        long dailyAverage = Math.round(currentTotal / 30.0d);
        double previousDailyAverage = previousTotal / 30.0d;
        float speed = speed(dailyAverage, previousDailyAverage);
        int state = state(speed);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.spending_speed_widget);
        views.setInt(R.id.speed_widget_root, "setBackgroundResource", backgroundFor(state));
        views.setImageViewBitmap(R.id.speed_widget_gauge, drawGauge(speed));
        views.setTextViewText(R.id.speed_widget_title, localizedContext.getString(R.string.widget_spending_speed));
        views.setTextViewText(R.id.speed_widget_value, compactAmount(dailyAverage, fa));
        views.setTextViewText(R.id.speed_widget_caption, localizedContext.getString(R.string.widget_daily_average_30));
        views.setContentDescription(R.id.speed_widget_root,
                localizedContext.getString(R.string.widget_speed_description, ExpenseStore.money(dailyAverage, fa)));

        Intent launch = new Intent(context, MainActivity.class);
        launch.putExtra(MainActivity.EXTRA_FROM_WIDGET, true);
        launch.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 10000 + id, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.speed_widget_root, pendingIntent);
        manager.updateAppWidget(id, views);
    }

    private static float speed(long currentDailyAverage, double previousDailyAverage) {
        if (currentDailyAverage <= 0) return 0f;
        if (previousDailyAverage <= 0) return 1f;
        return clamp((float) (currentDailyAverage / (previousDailyAverage * 2.0d)));
    }

    private static int state(float speed) {
        if (speed < 0.425f) return 0;
        if (speed < 0.575f) return 1;
        return 2;
    }

    private static int backgroundFor(int state) {
        if (state == 0) return R.drawable.speed_widget_bg_green;
        if (state == 1) return R.drawable.speed_widget_bg_yellow;
        return R.drawable.speed_widget_bg_red;
    }

    private static Bitmap drawGauge(float speed) {
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
        drawSegment(canvas, paint, arc, 180f, 54f, Color.rgb(91, 231, 160));
        drawSegment(canvas, paint, arc, 243f, 54f, Color.rgb(255, 214, 92));
        drawSegment(canvas, paint, arc, 306f, 54f, Color.rgb(255, 116, 129));

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
