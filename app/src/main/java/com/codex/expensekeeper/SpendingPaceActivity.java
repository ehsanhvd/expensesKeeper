package com.codex.expensekeeper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.res.ColorStateList;

import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

/** A small spending cockpit: understand the pace, find a flexible expense, take one step. */
public class SpendingPaceActivity extends Activity {
    private ExpenseStore store;
    private boolean fa, dark;
    private int ink, muted, surface, surfaceAlt, background, accent, accent2, outline;
    private Typeface font;
    private LinearLayout content;
    private PaceGaugeView gauge;
    private int restoredScroll;
    private boolean helpExpanded;

    @Override protected void attachBaseContext(Context base) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(new Locale(new ExpenseStore(base).language()));
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            restoredScroll = state.getInt("pace_scroll");
            helpExpanded = state.getBoolean("pace_help");
        }
        store = new ExpenseStore(this);
        fa = "fa".equals(store.language());
        font = AppAppearance.font(this, fa);
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    @Override protected void onPause() {
        if (gauge != null) gauge.stopAnimation();
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("pace_scroll", content == null ? 0 : ((ScrollView) content.getParent()).getScrollY());
        state.putBoolean("pace_help", helpExpanded);
    }

    private void render() {
        int scrollY = content != null ? ((ScrollView) content.getParent()).getScrollY() : restoredScroll;
        if (gauge != null) gauge.stopAnimation();
        AppAppearance appearance = new AppAppearance(this, store.theme());
        dark = appearance.dark;
        background = appearance.background;
        surface = appearance.surface;
        surfaceAlt = appearance.surfaceAlt;
        ink = appearance.text;
        muted = appearance.muted;
        accent = appearance.accent;
        accent2 = appearance.accent2;
        outline = appearance.outline;
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(28));
        content.setLayoutDirection(fa ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        scroll.addView(content);
        setContentView(scroll);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(8), 0, dp(10));
        content.addView(header, new LinearLayout.LayoutParams(-1, -2));
        Button back = button(fa ? "›" : "‹", false);
        back.setTextDirection(View.TEXT_DIRECTION_LTR);
        back.setTextSize(28);
        back.setPadding(0, 0, 0, 0);
        back.setContentDescription(getString(R.string.pace_screen_back));
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        backParams.setMarginEnd(dp(10));
        header.addView(back, backParams);
        TextView heading = add(header, getString(R.string.pace_screen_title), 26, ink, true);
        heading.setPadding(0, 0, 0, 0);
        heading.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        add(content, getString(R.string.pace_screen_subtitle), 14, muted, false);
        long now = System.currentTimeMillis();
        SpendingPace pace = SpendingSpeedWidgetProvider.pace(store, now);
        boolean learning = pace.state == SpendingPace.LEARNING;

        LinearLayout hero = card();
        GradientDrawable heroBackground = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF243D5B, 0xFF232440});
        heroBackground.setCornerRadius(dp(28));
        hero.setBackground(heroBackground);
        int statusColor = learning ? 0xFFCAD7F4 : pace.state == SpendingPace.HIGH
                ? 0xFFFFAFC0 : pace.state == SpendingPace.STEADY ? 0xFFFFD481 : 0xFF98EACF;
        TextView badge = add(hero, SpendingSpeedWidgetProvider.status(this, pace), 14, statusColor, true);
        badge.setGravity(Gravity.CENTER);
        gauge = new PaceGaugeView(this, pace);
        hero.addView(gauge, new LinearLayout.LayoutParams(-1, -2));
        center(hero, money(pace.dailyAverage), 29, Color.WHITE, true);
        center(hero, getString(R.string.pace_screen_daily), 13, 0xFFC3CFE5, false);
        center(hero, learning ? getString(R.string.pace_screen_learning)
                : SpendingSpeedWidgetProvider.comparison(this, pace, fa), 19, statusColor, true);
        int message = learning ? R.string.pace_screen_learning_hint
                : pace.state == SpendingPace.GREEN ? R.string.pace_screen_green_hint
                : pace.state == SpendingPace.HIGH ? R.string.pace_screen_high_hint : R.string.pace_screen_steady_hint;
        center(hero, getString(message), 14, 0xFFE0E6F5, false);

        LinearLayout week = card();
        add(week, getString(R.string.pace_screen_week), 18, ink, true);
        double recent = pace.dailyAverage * 7;
        double usual = pace.usualDaily * 7;
        double max = Math.max(1, Math.max(recent, usual));
        metric(week, getString(R.string.pace_screen_recent), recent, recent / max, accent);
        if (!learning) {
            metric(week, getString(R.string.pace_screen_usual), usual, usual / max,
                    accent2);
            add(week, getString(recent <= usual ? R.string.pace_screen_less : R.string.pace_screen_more,
                    money(Math.abs(recent - usual))), 15, ink, true);
        }

        LinearLayout challenge = card();
        add(challenge, getString(R.string.pace_screen_challenge), 18, ink, true);
        if (learning) {
            add(challenge, getString(R.string.pace_screen_first_mission), 14, ink, false);
        } else {
            double target = usual * .95;
            add(challenge, getString(R.string.pace_screen_goal, money(target)), 21, accent, true);
            add(challenge, getString(recent <= target ? R.string.pace_screen_goal_met : R.string.pace_screen_goal_gap,
                    money(Math.max(0, recent - target))), 15, muted, false);
            add(challenge, getString(R.string.pace_screen_goal_hint), 13, muted, false);
        }
        add(challenge, getString(R.string.pace_screen_tip), 14, ink, false);

        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(now);
        start.add(Calendar.DAY_OF_YEAR, -7);
        Map<String, Long> totals = store.categoryTotals(start.getTimeInMillis() + 1, now + 1);
        if (!totals.isEmpty() && recent > 0) {
            LinearLayout categories = card();
            add(categories, getString(R.string.pace_screen_categories), 18, ink, true);
            add(categories, getString(R.string.pace_screen_categories_hint), 14, muted, false);
            int count = 0;
            for (Map.Entry<String, Long> entry : totals.entrySet()) {
                if (entry.getValue() <= 0) continue;
                String label = getString(R.string.pace_screen_uncategorized);
                for (ExpenseStore.Category category : store.categories()) {
                    if (category.id.equals(entry.getKey())) { label = category.label(fa); break; }
                }
                metric(categories, label, entry.getValue(), entry.getValue() / recent, accent);
                if (++count == 3) break;
            }
        }

        Button expenses = button(getString(R.string.pace_screen_expenses), true);
        expenses.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_FROM_WIDGET, true);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
        content.addView(expenses, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout help = card();
        Button explain = button(getString(R.string.pace_screen_help), false);
        help.addView(explain, new LinearLayout.LayoutParams(-1, -2));
        TextView explanation = add(help, getString(learning ? R.string.pace_screen_help_learning
                : R.string.pace_screen_help_detail), 14, muted, false);
        explanation.setVisibility(helpExpanded ? View.VISIBLE : View.GONE);
        explain.setText(helpExpanded ? R.string.pace_screen_help_close : R.string.pace_screen_help);
        explain.setOnClickListener(v -> {
            boolean opening = explanation.getVisibility() != View.VISIBLE;
            helpExpanded = opening;
            explanation.setVisibility(opening ? View.VISIBLE : View.GONE);
            explain.setText(opening ? R.string.pace_screen_help_close : R.string.pace_screen_help);
        });
        scroll.post(() -> scroll.scrollTo(0, scrollY));
        gauge.animateIn();
    }

    private String money(double value) { return ExpenseStore.money(Math.round(value), fa); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(surface);
        shape.setCornerRadius(dp(24));
        if (dark) shape.setStroke(dp(1), outline);
        card.setBackground(shape);
        card.setElevation(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        params.bottomMargin = dp(8);
        content.addView(card, params);
        return card;
    }

    private TextView add(LinearLayout parent, String label, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(font, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(2), 1f);
        if (Build.VERSION.SDK_INT >= 28 && bold && size >= 18) view.setAccessibilityHeading(true);
        view.setGravity(Gravity.START);
        view.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        view.setPadding(0, dp(bold ? 8 : 4), 0, dp(bold ? 8 : 10));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private void center(LinearLayout parent, String label, int size, int color, boolean bold) {
        add(parent, label, size, color, bold).setGravity(Gravity.CENTER);
    }

    private Button button(String label, boolean filled) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(font, Typeface.BOLD);
        button.setIncludeFontPadding(false);
        button.setLineSpacing(dp(2), 1f);
        button.setTextDirection(fa ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(56));
        button.setMinimumHeight(dp(56));
        button.setPadding(dp(18), dp(8), dp(18), dp(8));
        button.setTextColor(filled ? (dark ? background : Color.WHITE) : accent);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(filled ? accent : surfaceAlt);
        shape.setCornerRadius(dp(24));
        if (!filled && dark) shape.setStroke(dp(1), outline);
        button.setBackgroundTintList(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(dark ? 0x33FFFFFF : 0x22000000), shape, null));
        button.setElevation(0);
        button.setStateListAnimator(null);
        return button;
    }

    private void metric(LinearLayout parent, String label, double amount, double fraction, int color) {
        add(parent, label, 14, muted, false);
        add(parent, money(amount), 21, ink, true);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress((int) Math.round(Math.max(0, Math.min(1, fraction)) * 1000));
        bar.setProgressTintList(ColorStateList.valueOf(color));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(surfaceAlt));
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(8));
        params.bottomMargin = dp(12);
        parent.addView(bar, params);
    }
}
