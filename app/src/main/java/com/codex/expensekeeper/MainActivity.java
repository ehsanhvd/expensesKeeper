package com.codex.expensekeeper;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int SCREEN_NONE = 0;
    private static final int SCREEN_LANGUAGE = 1;
    private static final int SCREEN_THEME = 2;
    private static final int SCREEN_PERIOD = 3;
    private static final int SCREEN_DASHBOARD = 4;
    private static final int SCREEN_ADD = 5;
    private static final int SCREEN_CATEGORIES = 6;
    private static final int SCREEN_HISTORY = 7;
    private static final int SCREEN_SETTINGS = 8;
    private static final int SCREEN_EXPENSE_DETAILS = 9;
    private static final int ICON_ADD = 1;
    private static final int ICON_CATEGORY = 2;
    private static final int ICON_SUBCATEGORY = 3;
    private static final int ICON_HISTORY = 4;
    private static final int ICON_SETTINGS = 5;
    private static final int ICON_UNCATEGORIZED = 6;
    private static final int ICON_BACK = 7;
    private static final int ICON_LANGUAGE = 8;
    private static final int ICON_THEME = 9;
    private static final int ICON_PERIOD = 10;
    private static final int ICON_PARENT = 11;

    private ExpenseStore store;
    private boolean fa;
    private int bg;
    private int surface;
    private int surfaceAlt;
    private int text;
    private int muted;
    private int accent;
    private int accent2;
    private int accent3;
    private int outline;
    private boolean dark;
    private Typeface font;
    private int currentScreen = SCREEN_NONE;
    private boolean navigatingBack;
    private boolean replacingScreen;
    private boolean settingsSubscreen;
    private long detailStart;
    private long detailEnd;
    private String detailLabel = "";
    private final ArrayList<Integer> backStack = new ArrayList<>();

    private interface CategoryCallback {
        void onPicked(ExpenseStore.Category category);
    }

    private static class ExpenseLine {
        String expenseId;
        int splitIndex;
        String categoryId;
        long time;
        long amount;
        String description;

        ExpenseLine(String expenseId, int splitIndex, String categoryId, long time, long amount, String description) {
            this.expenseId = expenseId;
            this.splitIndex = splitIndex;
            this.categoryId = categoryId;
            this.time = time;
            this.amount = amount;
            this.description = description;
        }
    }

    private String mainCategoryLabel() {
        return fa ? "دسته اصلی" : "Main category";
    }

    private String moreSplitsLabel() {
        return fa ? "تقسیم بیشتر" : "More splits";
    }

    private String parentCategoryLabel() {
        return fa ? "دسته والد" : "Parent category";
    }

    private String noParentLabel() {
        return fa ? "بدون والد" : "No parent";
    }

    private String cancelLabel() {
        return fa ? "لغو" : "Cancel";
    }

    private String colorLabel() {
        return fa ? "رنگ" : "Color";
    }

    private String noExpensesLabel() {
        return fa ? "هنوز خرجی ثبت نشده" : "No expenses";
    }

    private String chartItemsLabel() {
        return fa ? "دسته" : "items";
    }

    private String uncategorizedLabel() {
        return fa ? "بدون دسته" : "Uncategorized";
    }

    private String deleteLabel() {
        return fa ? "حذف" : "Delete";
    }

    private String deleteCategoryLabel() {
        return fa ? "حذف دسته" : "Delete category";
    }

    private String deleteCategoryMessage() {
        return fa ? "این دسته حذف می‌شود. زیر‌دسته‌ها بدون والد می‌شوند و خرج‌ها باقی می‌مانند." : "This category will be removed. Child categories become top-level and expenses are kept.";
    }

    private String markInvestmentLabel() {
        return fa ? "ثبت به عنوان سرمایه‌گذاری" : "Mark as investment";
    }

    private String categorizeLabel() {
        return fa ? "دسته‌بندی خرج" : "Categorize expense";
    }

    private String assignCategoryLabel() {
        return fa ? "انتخاب یک دسته" : "Assign one category";
    }

    private String splitExpenseLabel() {
        return fa ? "تقسیم بین چند دسته" : "Split into categories";
    }

    private String addSplitLabel() {
        return fa ? "افزودن دسته" : "Add category";
    }

    private String changeCategoryLabel() {
        return fa ? "تغییر دسته" : "Change category";
    }

    private String totalLabel(long amount) {
        return (fa ? "کل مبلغ: " : "Total: ") + ExpenseStore.money(amount, fa);
    }

    private String remainingLabel(long amount) {
        return (fa ? "باقی‌مانده: " : "Remaining: ") + ExpenseStore.money(amount, fa);
    }

    private String investmentMessage() {
        return fa ? "سرمایه‌گذاری از جمع خرج‌ها، نمودارها و لیست جزئیات حذف می‌شود." : "Investments are excluded from expense totals, charts, and detail lists.";
    }

    private String sourceLabel(String source) {
        if ("manual".equals(source)) return fa ? "ثبت دستی" : "Manual";
        if ("sms".equals(source)) return fa ? "پیامک بانکی" : "Bank SMS";
        return source == null || source.isEmpty() ? (fa ? "بدون توضیح" : "No description") : source;
    }

    @Override
    protected void attachBaseContext(Context base) {
        ExpenseStore s = new ExpenseStore(base);
        Locale locale = new Locale(s.language());
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ExpenseStore(this);
        fa = "fa".equals(store.language());
        font = Typeface.create("sans-serif", Typeface.NORMAL);
        if (fa && Build.VERSION.SDK_INT >= 26) {
            try {
                font = getResources().getFont(R.font.vazirmatn);
            } catch (Throwable ignored) {
                font = Typeface.create("sans-serif", Typeface.NORMAL);
            }
        }
        applyPalette();
        if (store.isConfigured()) {
            if (getIntent().getBooleanExtra("show_settings_after_language", false)) {
                getIntent().removeExtra("show_settings_after_language");
                replacingScreen = true;
                showSettings();
                replacingScreen = false;
            } else {
                showDashboardHome();
            }
            askSmsPermission();
        } else {
            showLanguageStep();
        }
    }

    private void applyPalette() {
        String theme = store.theme();
        dark = "dark".equals(theme);
        if ("system".equals(theme)) {
            dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        }
        bg = dark ? Color.rgb(18, 19, 24) : Color.rgb(250, 248, 255);
        surface = dark ? Color.rgb(30, 31, 36) : Color.rgb(255, 255, 255);
        surfaceAlt = dark ? Color.rgb(38, 40, 48) : Color.rgb(239, 244, 255);
        text = dark ? Color.rgb(232, 234, 240) : Color.rgb(31, 31, 31);
        muted = dark ? Color.rgb(188, 190, 199) : Color.rgb(95, 99, 104);
        accent = dark ? Color.rgb(168, 199, 250) : Color.rgb(26, 115, 232);
        accent2 = dark ? Color.rgb(244, 176, 203) : Color.rgb(217, 48, 105);
        accent3 = dark ? Color.rgb(250, 210, 118) : Color.rgb(251, 188, 4);
        outline = dark ? Color.rgb(68, 71, 78) : Color.rgb(218, 220, 224);
        getWindow().setStatusBarColor(bg);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void showLanguageStep() {
        boolean fromSettings = store != null && store.isConfigured();
        if (fromSettings && currentScreen == SCREEN_SETTINGS) settingsSubscreen = true;
        enterScreen(SCREEN_LANGUAGE);
        LinearLayout root = base();
        root.addView(title(getString(R.string.setup_language), 30));
        root.addView(subtitle("Expense-only tracking. No income, no balance noise."));
        root.addView(option(getString(R.string.persian), () -> {
            store.setLanguage("fa");
            getIntent().putExtra("show_settings_after_language", store.isConfigured());
            recreate();
        }));
        root.addView(option(getString(R.string.english), () -> {
            store.setLanguage("en");
            getIntent().putExtra("show_settings_after_language", store.isConfigured());
            recreate();
        }));
        if (!fromSettings) root.addView(option(getString(R.string.continue_label), this::showThemeStep));
        setContentView(wrap(root));
    }

    private void showThemeStep() {
        if (store != null && store.isConfigured() && currentScreen == SCREEN_SETTINGS) settingsSubscreen = true;
        enterScreen(SCREEN_THEME);
        LinearLayout root = base();
        root.addView(title(getString(R.string.setup_theme), 30));
        root.addView(option(getString(R.string.system_theme), () -> {
            store.setTheme("system");
            applyPalette();
            afterThemePicked();
        }));
        root.addView(option(getString(R.string.light_theme), () -> {
            store.setTheme("light");
            applyPalette();
            afterThemePicked();
        }));
        root.addView(option(getString(R.string.dark_theme), () -> {
            store.setTheme("dark");
            applyPalette();
            afterThemePicked();
        }));
        setContentView(wrap(root));
    }

    private void afterThemePicked() {
        if (store.isConfigured()) {
            replaceWithSettings();
        } else {
            showPeriodStep();
        }
    }

    private void replaceWithSettings() {
        settingsSubscreen = false;
        if (!backStack.isEmpty() && backStack.get(backStack.size() - 1) == SCREEN_SETTINGS) {
            backStack.remove(backStack.size() - 1);
        }
        replacingScreen = true;
        showSettings();
        replacingScreen = false;
    }

    private void showPeriodStep() {
        if (store != null && store.isConfigured() && currentScreen == SCREEN_SETTINGS) settingsSubscreen = true;
        enterScreen(SCREEN_PERIOD);
        LinearLayout root = base();
        root.addView(title(getString(R.string.setup_period), 30));
        JalaliDate now = JalaliDate.today();
        root.addView(subtitle((fa ? "روز شروع دوره در ماه جلالی" : "Pick the Jalali month day that starts each period")));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int day = 1;
        for (int row = 0; row < 5; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER);
            for (int col = 0; col < 7 && day <= JalaliDate.daysInMonth(now.year, now.month); col++) {
                final int picked = day++;
                TextView chip = chip(ExpenseStore.localNumber(picked, fa));
                chip.setOnClickListener(v -> {
                    store.setPeriodStartDay(picked);
                    store.setConfigured(true);
                    if (settingsSubscreen) {
                        replaceWithSettings();
                        return;
                    }
                    showDashboardHome();
                    askSmsPermission();
                });
                line.addView(chip);
            }
            grid.addView(line);
        }
        root.addView(grid);
        setContentView(wrap(root));
    }

    private void showDashboard() {
        enterScreen(SCREEN_DASHBOARD);
        fa = "fa".equals(store.language());
        applyPalette();
        LinearLayout root = base();
        root.addView(homeHeader());
        JalaliDate.Period period = JalaliDate.periodFor(System.currentTimeMillis(), store.periodStartDay());
        root.addView(summaryCard(period));
        root.addView(homeActions());
        root.addView(sectionLabel(getString(R.string.uncategorized)));
        addUncategorized(root);
        setContentView(wrap(root));
        ExpenseWidgetProvider.updateAll(this);
    }

    private void showDashboardHome() {
        backStack.clear();
        replacingScreen = true;
        showDashboard();
        replacingScreen = false;
    }

    private View summaryCard(JalaliDate.Period period) {
        LinearLayout card = card();
        card.setOnClickListener(v -> showExpenseDetails(period.label(fa), period.start, period.end));
        card.setPadding(dp(22), dp(20), dp(22), dp(16));
        card.setBackground(new HeroDrawable(accent, accent2, accent3, dark));
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));
        TextView periodTitle = labelText(period.label(fa), Color.WHITE);
        periodTitle.setTextColor(Color.WHITE);
        card.addView(periodTitle);
        long total = store.totalBetween(period.start, period.end);
        TextView amount = title(ExpenseStore.money(total, fa), 36);
        amount.setTextColor(Color.WHITE);
        amount.setPadding(0, dp(8), 0, dp(2));
        card.addView(amount);
        TextView caption = subtitle(getString(R.string.this_period));
        caption.setTextColor(Color.argb(230, 255, 255, 255));
        caption.setPadding(0, 0, 0, dp(8));
        card.addView(caption);
        Map<String, Long> totals = store.categoryTotals(period.start, period.end);
        card.addView(new ChartView(this, totals, store.categories(), fa, Color.WHITE, font));
        return card;
    }

    private View homeHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(2), dp(8), dp(2), dp(10));
        TextView app = labelText(getString(R.string.app_name), accent);
        header.addView(app);
        TextView headline = title(getString(R.string.dashboard), 32);
        headline.setPadding(0, dp(6), 0, dp(2));
        header.addView(headline);
        TextView hint = subtitle(fa ? "خرج ها، دسته ها و دوره ها در یک نگاه" : "Expenses, categories, and periods at a glance");
        hint.setPadding(0, 0, 0, dp(4));
        header.addView(hint);
        return header;
    }

    private LinearLayout homeActions() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(0, dp(8), 0, dp(2));
        group.addView(primaryAction(getString(R.string.add_expense), fa ? "ثبت سریع خرج جدید" : "Capture a new expense quickly", this::showAddExpense));
        LinearLayout row = navLine();
        row.addView(destinationTile(getString(R.string.categories), ICON_CATEGORY, accent2, this::showCategories));
        row.addView(destinationTile(getString(R.string.history), ICON_HISTORY, accent3, this::showHistory));
        row.addView(destinationTile(getString(R.string.settings), ICON_SETTINGS, accent, this::showSettings));
        group.addView(row);
        return group;
    }

    private LinearLayout navLine() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private void addUncategorized(LinearLayout root) {
        List<ExpenseStore.Expense> all = store.expenses();
        int count = 0;
        for (ExpenseStore.Expense e : all) {
            if (e.investment) continue;
            if (!e.isUncategorized()) continue;
            LinearLayout item = card();
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.addView(iconBadge(ICON_UNCATEGORIZED, accent2, Color.WHITE));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            copyLp.setMargins(fa ? 0 : dp(12), 0, fa ? dp(12) : 0, 0);
            copy.setLayoutParams(copyLp);
            copy.addView(title(ExpenseStore.money(e.amount, fa), 19));
            copy.addView(subtitle(shortText(e.description.isEmpty() ? sourceLabel(e.source) : e.description)));
            item.addView(copy);
            item.setOnClickListener(v -> showUncategorizedExpenseActions(e));
            root.addView(item);
            count++;
            if (count == 8) break;
        }
        if (count == 0) root.addView(subtitle(fa ? "خرج بدون دسته ندارید." : "No uncategorized expenses."));
    }

    private void showAddExpense() {
        enterScreen(SCREEN_ADD);
        LinearLayout box = base();
        box.addView(topBar(getString(R.string.add_expense)));
        EditText amount = input(getString(R.string.amount), InputType.TYPE_CLASS_NUMBER);
        EditText desc = input(getString(R.string.description), InputType.TYPE_CLASS_TEXT);
        LinearLayout form = card();
        form.addView(labelText(fa ? "جزئیات خرج" : "Expense details", accent));
        form.addView(amount);
        form.addView(amountWordsView(amount));
        form.addView(desc);
        box.addView(form);
        box.addView(option(getString(R.string.save), () -> {
            ExpenseStore.Expense e = new ExpenseStore.Expense();
            e.amount = parseLong(amount.getText().toString());
            e.description = desc.getText().toString();
            if (e.amount > 0) store.addExpense(e);
            showDashboardHome();
        }));
        setContentView(wrap(box));
    }

    private void showUncategorizedExpenseActions(ExpenseStore.Expense expense) {
        Dialog dialog = new Dialog(this);
        LinearLayout root = dialogRoot(ExpenseStore.money(expense.amount, fa));
        root.addView(subtitle(shortText(expense.description.isEmpty() ? sourceLabel(expense.source) : expense.description)));
        root.addView(option(assignCategoryLabel(), () -> {
            dialog.dismiss();
            showAssignCategoryPicker(expense, true);
        }));
        root.addView(option(splitExpenseLabel(), () -> {
            dialog.dismiss();
            expense.splits.clear();
            showSplitDialog(expense);
        }));
        root.addView(subtitle(investmentMessage()));
        root.addView(deleteButton(markInvestmentLabel(), () -> {
            markExpenseAsInvestment(expense.id);
            dialog.dismiss();
            showDashboardHome();
        }));
        showMaterialDialog(dialog, root);
    }

    private void markExpenseAsInvestment(String expenseId) {
        List<ExpenseStore.Expense> expenses = store.expenses();
        for (ExpenseStore.Expense e : expenses) {
            if (e.id.equals(expenseId)) {
                e.investment = true;
                e.splits.clear();
                break;
            }
        }
        store.saveExpenses(expenses);
        ExpenseWidgetProvider.updateAll(this);
    }

    private void showSplitDialog(ExpenseStore.Expense expense) {
        if (store != null) {
            Dialog dialog = new Dialog(this);
            LinearLayout root = dialogRoot(splitExpenseLabel());
            root.addView(subtitle(totalLabel(expense.amount)));
            root.addView(subtitle(remainingLabel(Math.max(0, expense.amount - splitSum(expense)))));
            for (ExpenseStore.Split split : expense.splits) {
                root.addView(labelText(categoryName(split.categoryId) + "  " + ExpenseStore.money(split.amount, fa), accent));
            }
            LinearLayout actions = dialogActions();
            actions.addView(dialogButton(cancelLabel(), false, dialog::dismiss));
            if (expense.amount - splitSum(expense) > 0) {
                actions.addView(dialogButton(addSplitLabel(), false, () -> {
                    dialog.dismiss();
                    showCategoryPicker(splitExpenseLabel(), store.categories(), false, "", null, category -> askSplitAmount(expense, category));
                }));
            }
            if (!expense.splits.isEmpty() && expense.amount - splitSum(expense) <= 0) {
                actions.addView(dialogButton(getString(R.string.done), true, () -> {
                    dialog.dismiss();
                    saveExpenseAndShowDashboard(expense);
                }));
            }
            root.addView(actions);
            showMaterialDialog(dialog, root);
            return;
        }
        List<ExpenseStore.Category> cats = store.categories();
        String[] labels = new String[cats.size()];
        for (int i = 0; i < cats.size(); i++) labels[i] = cats.get(i).label(fa);
        new AlertDialog.Builder(this)
                .setTitle(fa ? "دسته اصلی" : "Main category")
                .setItems(labels, (dialog, which) -> askSplitAmount(expense, cats.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void askSplitAmount(ExpenseStore.Expense expense, ExpenseStore.Category category) {
        if (store != null) {
            Dialog dialog = new Dialog(this);
            LinearLayout box = dialogRoot(splitExpenseLabel());
            long remaining = Math.max(0, expense.amount - splitSum(expense));
            box.addView(labelText(category.label(fa), accent));
            box.addView(subtitle(remainingLabel(remaining)));
            EditText amount = input(getString(R.string.amount), InputType.TYPE_CLASS_NUMBER);
            amount.setText(ExpenseStore.localNumber(remaining, fa));
            box.addView(amount);
            box.addView(amountWordsView(amount));
            LinearLayout actions = dialogActions();
            actions.addView(dialogButton(cancelLabel(), false, dialog::dismiss));
            actions.addView(dialogButton(moreSplitsLabel(), false, () -> {
                long value = parseLong(amount.getText().toString());
                value = Math.min(value, Math.max(0, expense.amount - splitSum(expense)));
                if (value > 0) expense.splits.add(new ExpenseStore.Split(category.id, value));
                dialog.dismiss();
                if (expense.amount - splitSum(expense) > 0) {
                    showSplitDialog(expense);
                } else {
                    saveExpenseAndShowDashboard(expense);
                }
            }));
            actions.addView(dialogButton(getString(R.string.save), true, () -> {
                long value = parseLong(amount.getText().toString());
                value = Math.min(value, Math.max(0, expense.amount - splitSum(expense)));
                if (value > 0) expense.splits.add(new ExpenseStore.Split(category.id, value));
                dialog.dismiss();
                if (expense.amount - splitSum(expense) > 0) {
                    showSplitDialog(expense);
                } else {
                    saveExpenseAndShowDashboard(expense);
                }
            }));
            box.addView(actions);
            showMaterialDialog(dialog, box);
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText amount = input(getString(R.string.amount), InputType.TYPE_CLASS_NUMBER);
        EditText desc = input(getString(R.string.description), InputType.TYPE_CLASS_TEXT);
        amount.setText(ExpenseStore.localNumber(expense.amount - splitSum(expense), fa));
        desc.setText(expense.description);
        box.addView(amount);
        box.addView(amountWordsView(amount));
        box.addView(desc);
        new AlertDialog.Builder(this)
                .setTitle(category.label(fa))
                .setView(box)
                .setPositiveButton(getString(R.string.save), (d, w) -> {
                    long value = parseLong(amount.getText().toString());
                    expense.description = desc.getText().toString();
                    if (value > 0) expense.splits.add(new ExpenseStore.Split(category.id, value));
                    List<ExpenseStore.Expense> all = store.expenses();
                    for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(expense.id)) all.set(i, expense);
                    store.saveExpenses(all);
                    showDashboardHome();
                })
                .setNeutralButton(fa ? "تقسیم بیشتر" : "More splits", (d, w) -> {
                    long value = parseLong(amount.getText().toString());
                    expense.description = desc.getText().toString();
                    if (value > 0) expense.splits.add(new ExpenseStore.Split(category.id, value));
                    showSplitDialog(expense);
                })
                .show();
    }

    private long splitSum(ExpenseStore.Expense expense) {
        long sum = 0;
        for (ExpenseStore.Split s : expense.splits) sum += s.amount;
        return sum;
    }

    private void showAssignCategoryPicker(ExpenseStore.Expense expense, boolean dashboardAfterSave) {
        showCategoryPicker(assignCategoryLabel(), store.categories(), false, "", null, category -> {
            expense.splits.clear();
            expense.splits.add(new ExpenseStore.Split(category.id, expense.amount));
            if (dashboardAfterSave) {
                saveExpenseAndShowDashboard(expense);
            } else {
                saveExpenseAndShowDetails(expense);
            }
        });
    }

    private void saveExpenseAndShowDashboard(ExpenseStore.Expense expense) {
        saveExpense(expense);
        showDashboardHome();
    }

    private void saveExpenseAndShowDetails(ExpenseStore.Expense expense) {
        saveExpense(expense);
        showExpenseDetails(detailLabel, detailStart, detailEnd);
    }

    private void saveExpense(ExpenseStore.Expense expense) {
        List<ExpenseStore.Expense> all = store.expenses();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(expense.id)) {
                all.set(i, expense);
                break;
            }
        }
        store.saveExpenses(all);
        ExpenseWidgetProvider.updateAll(this);
    }

    private void showCategories() {
        enterScreen(SCREEN_CATEGORIES);
        LinearLayout root = base();
        root.addView(topBar(getString(R.string.categories)));
        root.addView(primaryAction(fa ? "دسته تازه" : "New category", fa ? "رنگ ها برای نمودار و لیست خرج ها استفاده می شوند" : "Colors shape charts and expense lists", () -> editCategory(new ExpenseStore.Category("cat_" + System.currentTimeMillis(), "", "", Color.rgb(123, 223, 242), ""))));
        for (ExpenseStore.Category c : store.categories()) {
            LinearLayout row = card();
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(iconBadge(c.parentId.isEmpty() ? ICON_CATEGORY : ICON_SUBCATEGORY, c.color, contrast(c.color)));
            TextView label = title((c.parentId.isEmpty() ? "" : (fa ? "  ‹  " : "  >  ")) + c.label(fa), 17);
            label.setSingleLine(false);
            label.setMaxLines(2);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            labelLp.setMargins(fa ? 0 : dp(12), 0, fa ? dp(12) : 0, 0);
            label.setLayoutParams(labelLp);
            row.addView(label);
            row.setOnClickListener(v -> editCategory(c));
            root.addView(row);
        }
        setContentView(wrap(root));
    }

    private void editCategory(ExpenseStore.Category category) {
        if (store != null) {
            Dialog dialog = new Dialog(this);
            LinearLayout box = dialogRoot(getString(R.string.categories));
            EditText en = input("English", InputType.TYPE_CLASS_TEXT);
            EditText faName = input(fa ? "فارسی" : "Persian", InputType.TYPE_CLASS_TEXT);
            forceLeftToRight(en);
            en.setText(category.en);
            faName.setText(category.fa);
            final String[] parentId = {category.parentId == null ? "" : category.parentId};
            final int[] pickedColor = {category.color};

            TextView parentPicker = pickerRow(ICON_PARENT, parentCategoryLabel(), parentLabel(parentId[0], category.id));
            parentPicker.setOnClickListener(v -> showCategoryPicker(parentCategoryLabel(), parentChoices(category.id), true, parentId[0], category.id, picked -> {
                parentId[0] = picked == null ? "" : picked.id;
                parentPicker.setText(localText(parentCategoryLabel() + "\n" + parentLabel(parentId[0], category.id)));
            }));

            LinearLayout colors = new LinearLayout(this);
            colors.setOrientation(LinearLayout.VERTICAL);
            colors.setGravity(Gravity.CENTER);
            ArrayList<TextView> swatches = new ArrayList<>();
            int[] palette = categoryPalette(pickedColor[0]);
            LinearLayout colorRow = null;
            for (int color : palette) {
                if (colorRow == null || colorRow.getChildCount() == 6) {
                    colorRow = new LinearLayout(this);
                    colorRow.setOrientation(LinearLayout.HORIZONTAL);
                    colorRow.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams colorRowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    colorRowLp.setMargins(0, dp(2), 0, dp(2));
                    colorRow.setLayoutParams(colorRowLp);
                    colors.addView(colorRow);
                }
                TextView swatch = new TextView(this);
                swatch.setTextSize(18);
                swatch.setTypeface(font, Typeface.BOLD);
                swatch.setGravity(Gravity.CENTER);
                swatch.setIncludeFontPadding(false);
                setColorSwatchState(swatch, color, color == pickedColor[0]);
                LinearLayout.LayoutParams swatchLp = new LinearLayout.LayoutParams(dp(36), dp(36));
                swatchLp.setMargins(dp(4), dp(5), dp(4), dp(5));
                swatch.setLayoutParams(swatchLp);
                swatch.setOnClickListener(v -> {
                    pickedColor[0] = color;
                    for (TextView item : swatches) {
                        int itemColor = (Integer) item.getTag();
                        setColorSwatchState(item, itemColor, itemColor == pickedColor[0]);
                    }
                });
                swatch.setTag(color);
                swatches.add(swatch);
                colorRow.addView(swatch);
            }

            box.addView(en);
            box.addView(faName);
            box.addView(parentPicker);
            box.addView(labelText(colorLabel(), accent));
            box.addView(colors);
            if (categoryExists(category.id)) {
                box.addView(deleteButton(deleteCategoryLabel(), () -> confirmDeleteCategory(category, dialog)));
            }
            LinearLayout actions = dialogActions();
            actions.addView(dialogButton(cancelLabel(), false, dialog::dismiss));
            actions.addView(dialogButton(getString(R.string.save), true, () -> {
                category.en = en.getText().toString();
                category.fa = faName.getText().toString();
                category.parentId = parentId[0];
                category.color = pickedColor[0];
                List<ExpenseStore.Category> cats = store.categories();
                boolean replaced = false;
                for (int i = 0; i < cats.size(); i++) {
                    if (cats.get(i).id.equals(category.id)) {
                        cats.set(i, category);
                        replaced = true;
                    }
                }
                if (!replaced) cats.add(category);
                store.saveCategories(cats);
                dialog.dismiss();
                showCategories();
            }));
            box.addView(actions);
            showMaterialDialog(dialog, box);
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText en = input("English", InputType.TYPE_CLASS_TEXT);
        EditText faName = input("فارسی", InputType.TYPE_CLASS_TEXT);
        EditText parent = input(fa ? "شناسه والد" : "Parent id", InputType.TYPE_CLASS_TEXT);
        forceLeftToRight(en);
        en.setText(category.en);
        faName.setText(category.fa);
        parent.setText(category.parentId);
        box.addView(en);
        box.addView(faName);
        box.addView(parent);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.categories))
                .setView(box)
                .setPositiveButton(getString(R.string.save), (d, w) -> {
                    category.en = en.getText().toString();
                    category.fa = faName.getText().toString();
                    category.parentId = parent.getText().toString();
                    List<ExpenseStore.Category> cats = store.categories();
                    boolean replaced = false;
                    for (int i = 0; i < cats.size(); i++) {
                        if (cats.get(i).id.equals(category.id)) {
                            cats.set(i, category);
                            replaced = true;
                        }
                    }
                    if (!replaced) cats.add(category);
                    store.saveCategories(cats);
                    showCategories();
                })
                .show();
    }

    private void showHistory() {
        enterScreen(SCREEN_HISTORY);
        LinearLayout root = base();
        root.addView(topBar(getString(R.string.history)));
        JalaliDate now = JalaliDate.today();
        int month = now.month;
        int year = now.year;
        int visiblePeriods = 0;
        for (int i = 0; i < 12; i++) {
            int startMonth = month - i;
            int startYear = year;
            while (startMonth <= 0) {
                startMonth += 12;
                startYear--;
            }
            int labelMonth = JalaliDate.nextMonth(startMonth);
            long start = JalaliDate.toMillisStartOfDay(startYear, startMonth, Math.min(store.periodStartDay(), JalaliDate.daysInMonth(startYear, startMonth)));
            int endMonth = startMonth + 1;
            int endYear = startYear;
            if (endMonth == 13) {
                endMonth = 1;
                endYear++;
            }
            long end = JalaliDate.toMillisStartOfDay(endYear, endMonth, Math.min(store.periodStartDay(), JalaliDate.daysInMonth(endYear, endMonth)));
            Map<String, Long> totals = store.categoryTotals(start, end);
            long total = 0;
            for (long value : totals.values()) total += value;
            if (total == 0) continue;
            LinearLayout card = card();
            card.addView(title((fa ? JalaliDate.FA_MONTHS[labelMonth - 1] : JalaliDate.EN_MONTHS[labelMonth - 1]) + " " + ExpenseStore.localNumber(startYear, fa), 20));
            card.addView(subtitle(ExpenseStore.money(total, fa)));
            card.addView(new ChartView(this, totals, store.categories(), fa, text, font));
            final long detailStart = start;
            final long detailEnd = end;
            final String label = (fa ? JalaliDate.FA_MONTHS[labelMonth - 1] : JalaliDate.EN_MONTHS[labelMonth - 1]) + " " + ExpenseStore.localNumber(startYear, fa);
            card.setOnClickListener(v -> showExpenseDetails(label, detailStart, detailEnd));
            root.addView(card);
            visiblePeriods++;
        }
        if (visiblePeriods == 0) root.addView(subtitle(noExpensesLabel()));
        setContentView(wrap(root));
    }

    private void showExpenseDetails(String label, long start, long end) {
        detailLabel = label;
        detailStart = start;
        detailEnd = end;
        enterScreen(SCREEN_EXPENSE_DETAILS);
        LinearLayout root = base();
        root.addView(topBar(label));
        root.addView(sectionLabel(fa ? "جزئیات خرج‌ها" : "Expense details"));
        Map<String, Long> totals = store.categoryTotals(start, end);
        Map<String, ArrayList<ExpenseLine>> groups = expenseGroups(start, end);
        if (groups.isEmpty()) {
            root.addView(subtitle(noExpensesLabel()));
        } else {
            for (Map.Entry<String, ArrayList<ExpenseLine>> entry : groups.entrySet()) {
                addExpenseGroup(root, entry.getKey(), totals.containsKey(entry.getKey()) ? totals.get(entry.getKey()) : 0, entry.getValue());
            }
        }
        setContentView(wrap(root));
    }

    private void addExpenseGroup(LinearLayout root, String categoryId, long total, ArrayList<ExpenseLine> lines) {
        LinearLayout card = card();
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ExpenseStore.Category category = categoryById(categoryId);
        int color = category == null ? accent2 : category.color;
        header.addView(iconBadge(category == null ? ICON_UNCATEGORIZED : ICON_CATEGORY, color, category == null ? Color.WHITE : contrast(color)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        copyLp.setMargins(fa ? 0 : dp(12), 0, fa ? dp(12) : 0, 0);
        copy.setLayoutParams(copyLp);
        copy.addView(title(categoryName(categoryId), 17));
        copy.addView(subtitle(ExpenseStore.money(total, fa)));
        header.addView(copy);
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(24);
        arrow.setTextColor(muted);
        arrow.setGravity(Gravity.CENTER);
        arrow.setTypeface(font, Typeface.BOLD);
        header.addView(arrow);
        card.addView(header);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(View.GONE);
        for (ExpenseLine line : lines) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(12), dp(10), dp(12), dp(10));
            item.setBackground(rounded(surfaceAlt, dp(16), Color.TRANSPARENT, 0));
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            itemLp.setMargins(0, dp(5), 0, dp(5));
            item.setLayoutParams(itemLp);
            item.addView(title(ExpenseStore.money(line.amount, fa), 15));
            item.addView(subtitle(formatDateTime(line.time)));
            item.addView(subtitle(shortText(line.description == null || line.description.isEmpty() ? (fa ? "بدون توضیح" : "No description") : line.description)));
            item.addView(labelText(changeCategoryLabel(), accent));
            item.setOnClickListener(v -> showChangeExpenseCategory(line));
            details.addView(item);
        }
        card.addView(details);
        header.setOnClickListener(v -> {
            boolean open = details.getVisibility() != View.VISIBLE;
            details.setVisibility(open ? View.VISIBLE : View.GONE);
            arrow.setText(open ? "⌄" : "›");
        });
        root.addView(card);
    }

    private Map<String, ArrayList<ExpenseLine>> expenseGroups(long start, long end) {
        Map<String, ArrayList<ExpenseLine>> groups = new LinkedHashMap<>();
        for (ExpenseStore.Category c : store.categories()) groups.put(c.id, new ArrayList<>());
        groups.put("uncategorized", new ArrayList<>());
        for (ExpenseStore.Expense e : store.expenses()) {
            if (e.investment) continue;
            if (e.time < start || e.time >= end) continue;
            if (e.splits.isEmpty()) {
                addExpenseLine(groups, "uncategorized", new ExpenseLine(e.id, -1, "uncategorized", e.time, e.amount, e.description.isEmpty() ? sourceLabel(e.source) : e.description));
            } else {
                for (int i = 0; i < e.splits.size(); i++) {
                    ExpenseStore.Split split = e.splits.get(i);
                    addExpenseLine(groups, split.categoryId, new ExpenseLine(e.id, i, split.categoryId, e.time, split.amount, e.description.isEmpty() ? sourceLabel(e.source) : e.description));
                }
            }
        }
        for (String key : new ArrayList<>(groups.keySet())) {
            if (groups.get(key).isEmpty()) groups.remove(key);
        }
        return groups;
    }

    private void addExpenseLine(Map<String, ArrayList<ExpenseLine>> groups, String categoryId, ExpenseLine line) {
        if (!groups.containsKey(categoryId)) groups.put(categoryId, new ArrayList<>());
        groups.get(categoryId).add(line);
    }

    private void showChangeExpenseCategory(ExpenseLine line) {
        Dialog dialog = new Dialog(this);
        LinearLayout root = dialogRoot(changeCategoryLabel());
        root.addView(subtitle(ExpenseStore.money(line.amount, fa)));
        root.addView(subtitle(shortText(line.description == null || line.description.isEmpty() ? (fa ? "بدون توضیح" : "No description") : line.description)));
        root.addView(option(changeCategoryLabel(), () -> {
            dialog.dismiss();
            showCategoryPicker(changeCategoryLabel(), store.categories(), false, line.categoryId, null, category -> changeExpenseCategory(line, category.id));
        }));
        LinearLayout actions = dialogActions();
        actions.addView(dialogButton(cancelLabel(), false, dialog::dismiss));
        root.addView(actions);
        showMaterialDialog(dialog, root);
    }

    private void changeExpenseCategory(ExpenseLine line, String categoryId) {
        List<ExpenseStore.Expense> expenses = store.expenses();
        for (ExpenseStore.Expense expense : expenses) {
            if (!expense.id.equals(line.expenseId)) continue;
            if (line.splitIndex >= 0 && line.splitIndex < expense.splits.size()) {
                expense.splits.get(line.splitIndex).categoryId = categoryId;
            } else {
                expense.splits.clear();
                expense.splits.add(new ExpenseStore.Split(categoryId, expense.amount));
            }
            break;
        }
        store.saveExpenses(expenses);
        ExpenseWidgetProvider.updateAll(this);
        showExpenseDetails(detailLabel, detailStart, detailEnd);
    }

    private ExpenseStore.Category categoryById(String id) {
        for (ExpenseStore.Category c : store.categories()) if (c.id.equals(id)) return c;
        return null;
    }

    private String categoryName(String id) {
        ExpenseStore.Category category = categoryById(id);
        return category == null ? uncategorizedLabel() : category.label(fa);
    }

    private String formatDateTime(long millis) {
        if (fa) {
            JalaliDate date = JalaliDate.fromMillis(millis);
            String time = new SimpleDateFormat("HH:mm", Locale.US).format(new Date(millis));
            return toPersianDigits(date.year + "/" + two(date.month) + "/" + two(date.day) + "  " + time);
        }
        return new SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.US).format(new Date(millis));
    }

    private String two(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private String toPersianDigits(String s) {
        return ExpenseStore.toPersianDigits(s);
    }

    private String localText(String s) {
        return fa && s != null ? toPersianDigits(s) : s;
    }

    private String directionArrow(boolean forward) {
        return forward ? (fa ? "‹" : "›") : (fa ? "›" : "‹");
    }

    private void applyTextDirection(TextView view) {
        view.setTextDirection(fa ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        if (view.getGravity() == Gravity.NO_GRAVITY) {
            view.setGravity((fa ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        }
    }

    private void showSettings() {
        enterScreen(SCREEN_SETTINGS);
        LinearLayout root = base();
        root.addView(topBar(getString(R.string.settings)));
        root.addView(settingsItem(getString(R.string.setup_language), ICON_LANGUAGE, accent, this::showLanguageStep));
        root.addView(settingsItem(getString(R.string.setup_theme), ICON_THEME, accent2, this::showThemeStep));
        root.addView(settingsItem(getString(R.string.setup_period), ICON_PERIOD, accent3, this::showPeriodStep));
        root.addView(sectionLabel("SMS"));
        root.addView(subtitle(getString(R.string.sms_permission)));
        root.addView(option(fa ? "فعال کردن دسترسی پیامک" : "Allow SMS access", this::askSmsPermission));
        setContentView(wrap(root));
    }

    @Override
    public void onBackPressed() {
        if (!backStack.isEmpty()) {
            int previous = backStack.remove(backStack.size() - 1);
            navigatingBack = true;
            showScreen(previous);
            navigatingBack = false;
            return;
        }
        if (currentScreen != SCREEN_DASHBOARD && store != null && store.isConfigured()) {
            showDashboardHome();
            return;
        }
        super.onBackPressed();
    }

    private void enterScreen(int screen) {
        if (!navigatingBack && !replacingScreen && currentScreen != SCREEN_NONE && currentScreen != screen) {
            backStack.add(currentScreen);
        }
        currentScreen = screen;
    }

    private void showScreen(int screen) {
        switch (screen) {
            case SCREEN_LANGUAGE:
                showLanguageStep();
                break;
            case SCREEN_THEME:
                showThemeStep();
                break;
            case SCREEN_PERIOD:
                showPeriodStep();
                break;
            case SCREEN_ADD:
                showAddExpense();
                break;
            case SCREEN_CATEGORIES:
                showCategories();
                break;
            case SCREEN_HISTORY:
                showHistory();
                break;
            case SCREEN_SETTINGS:
                showSettings();
                break;
            case SCREEN_EXPENSE_DETAILS:
                showExpenseDetails(detailLabel, detailStart, detailEnd);
                break;
            case SCREEN_DASHBOARD:
            default:
                showDashboard();
                break;
        }
    }

    private void askSmsPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, 7);
        }
    }

    private LinearLayout base() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(28));
        root.setBackground(new PatternDrawable(bg, surfaceAlt, accent, accent2, accent3));
        root.setWillNotDraw(false);
        if (fa) root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return root;
    }

    private ScrollView wrap(View view) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        scroll.addView(view);
        return scroll;
    }

    private View topBar(String label) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(8), 0, dp(10));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.setMargins(0, 0, 0, dp(6));
        bar.setLayoutParams(barLp);

        if (currentScreen != SCREEN_DASHBOARD) {
            ImageView back = iconButton(ICON_BACK, surfaceAlt, accent, 44, 20);
            LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(44), dp(44));
            backLp.setMargins(fa ? dp(10) : 0, 0, fa ? 0 : dp(10), 0);
            back.setLayoutParams(backLp);
            back.setOnClickListener(v -> onBackPressed());
            bar.addView(back);
        }

        TextView t = title(label, 26);
        t.setTextColor(text);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        t.setLayoutParams(titleLp);
        bar.addView(t);
        return bar;
    }

    private TextView title(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(localText(s));
        t.setTextSize(sp);
        t.setTextColor(text);
        t.setTypeface(font, Typeface.BOLD);
        t.setIncludeFontPadding(false);
        t.setPadding(0, dp(8), 0, dp(8));
        t.setLineSpacing(dp(2), 1.0f);
        applyTextDirection(t);
        return t;
    }

    private TextView subtitle(String s) {
        TextView t = new TextView(this);
        t.setText(localText(s));
        t.setTextSize(14);
        t.setTextColor(muted);
        t.setTypeface(font);
        t.setIncludeFontPadding(false);
        t.setPadding(0, dp(4), 0, dp(10));
        t.setLineSpacing(dp(2), 1.0f);
        applyTextDirection(t);
        return t;
    }

    private TextView sectionLabel(String s) {
        TextView t = title(s, 18);
        t.setPadding(0, dp(20), 0, dp(6));
        t.setTextColor(text);
        return t;
    }

    private TextView labelText(String s, int color) {
        TextView t = new TextView(this);
        t.setText(localText(s));
        t.setTextSize(12);
        t.setTextColor(color);
        t.setTypeface(font, Typeface.BOLD);
        t.setIncludeFontPadding(false);
        t.setPadding(0, dp(2), 0, dp(2));
        applyTextDirection(t);
        return t;
    }

    private View primaryAction(String label, String caption, final Runnable action) {
        LinearLayout actionCard = new LinearLayout(this);
        actionCard.setOrientation(LinearLayout.HORIZONTAL);
        actionCard.setGravity(Gravity.CENTER_VERTICAL);
        actionCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        actionCard.setBackground(rounded(accent, dp(28), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(8));
        actionCard.setLayoutParams(lp);
        actionCard.setOnClickListener(v -> action.run());

        actionCard.addView(iconBadge(ICON_ADD, Color.WHITE, accent));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        copyLp.setMargins(fa ? 0 : dp(14), 0, fa ? dp(14) : 0, 0);
        copy.setLayoutParams(copyLp);

        TextView title = new TextView(this);
        title.setText(localText(label));
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);
        title.setTypeface(font, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        title.setSingleLine(false);
        title.setMaxLines(2);
        applyTextDirection(title);
        copy.addView(title);

        TextView sub = new TextView(this);
        sub.setText(localText(caption));
        sub.setTextSize(13);
        sub.setTextColor(Color.argb(225, 255, 255, 255));
        sub.setTypeface(font);
        sub.setIncludeFontPadding(false);
        sub.setPadding(0, dp(5), 0, 0);
        sub.setSingleLine(false);
        sub.setMaxLines(2);
        applyTextDirection(sub);
        copy.addView(sub);

        actionCard.addView(copy);
        return actionCard;
    }

    private View destinationTile(String label, int icon, int color, final Runnable action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(10), dp(8), dp(10));
        tile.setBackground(rounded(surface, dp(24), outline, dark ? dp(1) : 0));
        tile.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(104), 1);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        tile.setLayoutParams(lp);
        if (Build.VERSION.SDK_INT >= 21) tile.setElevation(dp(1));

        tile.addView(iconBadge(icon, color, Color.WHITE));
        TextView textView = new TextView(this);
        textView.setText(localText(label));
        textView.setTextSize(13);
        textView.setTextColor(text);
        textView.setTypeface(font, Typeface.BOLD);
        textView.setGravity(Gravity.CENTER);
        textView.setIncludeFontPadding(false);
        textView.setSingleLine(false);
        textView.setMaxLines(2);
        textView.setPadding(0, dp(8), 0, 0);
        applyTextDirection(textView);
        tile.addView(textView);
        return tile;
    }

    private View settingsItem(String label, int icon, int color, final Runnable action) {
        LinearLayout item = card();
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setOnClickListener(v -> action.run());
        item.addView(iconBadge(icon, color, Color.WHITE));

        TextView title = new TextView(this);
        title.setText(localText(label));
        title.setTextSize(16);
        title.setTextColor(text);
        title.setTypeface(font, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        title.setSingleLine(false);
        title.setMaxLines(2);
        applyTextDirection(title);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleLp.setMargins(fa ? 0 : dp(12), 0, fa ? dp(12) : 0, 0);
        title.setLayoutParams(titleLp);
        item.addView(title);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(20);
        arrow.setTextColor(muted);
        arrow.setGravity(Gravity.CENTER);
        arrow.setTypeface(font, Typeface.BOLD);
        item.addView(arrow);
        return item;
    }

    private TextView pickerRow(int icon, String label, String value) {
        TextView row = new TextView(this);
        row.setText(localText(label + "\n" + value));
        row.setTextSize(15);
        row.setTextColor(text);
        row.setTypeface(font, Typeface.BOLD);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setIncludeFontPadding(false);
        row.setSingleLine(false);
        row.setMaxLines(3);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.setCompoundDrawablePadding(dp(12));
        row.setCompoundDrawablesRelativeWithIntrinsicBounds(new IconDrawable(icon, accent, fa), null, null, null);
        row.setBackground(rounded(surfaceAlt, dp(18), Color.TRANSPARENT, 0));
        applyTextDirection(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72));
        lp.setMargins(0, dp(8), 0, dp(8));
        row.setLayoutParams(lp);
        return row;
    }

    private LinearLayout dialogRoot(String heading) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(16));
        root.setBackground(rounded(surface, dp(28), outline, dark ? dp(1) : 0));
        TextView title = title(heading, 22);
        title.setPadding(0, 0, 0, dp(14));
        root.addView(title);
        return root;
    }

    private LinearLayout dialogActions() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        row.setPadding(0, dp(10), 0, 0);
        return row;
    }

    private TextView dialogButton(String label, boolean filled, final Runnable action) {
        TextView button = new TextView(this);
        button.setText(localText(label));
        button.setTextSize(14);
        button.setTextColor(filled ? Color.WHITE : accent);
        button.setTypeface(font, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setSingleLine(false);
        button.setMaxLines(2);
        button.setPadding(dp(14), 0, dp(14), 0);
        applyTextDirection(button);
        button.setBackground(rounded(filled ? accent : surfaceAlt, dp(18), Color.TRANSPARENT, 0));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
        lp.setMargins(dp(4), 0, dp(4), 0);
        button.setLayoutParams(lp);
        return button;
    }

    private TextView deleteButton(String label, final Runnable action) {
        TextView button = dialogButton(label, true, action);
        button.setBackground(rounded(accent2, dp(18), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.setMargins(0, dp(10), 0, dp(4));
        button.setLayoutParams(lp);
        return button;
    }

    private void showMaterialDialog(Dialog dialog, View content) {
        dialog.setContentView(content);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = getResources().getDisplayMetrics().widthPixels - dp(32);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showCategoryPicker(String heading, List<ExpenseStore.Category> categories, boolean includeNone, String selectedId, String excludeId, CategoryCallback callback) {
        Dialog dialog = new Dialog(this);
        LinearLayout root = dialogRoot(heading);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        if (includeNone) {
            list.addView(categoryPickerItem(null, noParentLabel(), selectedId == null || selectedId.isEmpty(), dialog, callback));
        }
        for (ExpenseStore.Category c : categories) {
            if (excludeId != null && excludeId.equals(c.id)) continue;
            list.addView(categoryPickerItem(c, c.label(fa), c.id.equals(selectedId), dialog, callback));
        }
        scroll.addView(list);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360));
        scroll.setLayoutParams(scrollLp);
        root.addView(scroll);
        LinearLayout actions = dialogActions();
        actions.addView(dialogButton(cancelLabel(), false, dialog::dismiss));
        root.addView(actions);
        showMaterialDialog(dialog, root);
    }

    private View categoryPickerItem(ExpenseStore.Category category, String label, boolean selected, Dialog dialog, CategoryCallback callback) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(rounded(selected ? surfaceAlt : surface, dp(18), selected ? accent : outline, selected ? dp(2) : (dark ? dp(1) : 0)));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(rowLp);
        int color = category == null ? accent : category.color;
        row.addView(iconBadge(category == null ? ICON_PARENT : (category.parentId.isEmpty() ? ICON_CATEGORY : ICON_SUBCATEGORY), color, category == null ? Color.WHITE : contrast(color)));
        TextView tv = new TextView(this);
        tv.setText(localText(label));
        tv.setTextSize(15);
        tv.setTextColor(text);
        tv.setTypeface(font, Typeface.BOLD);
        tv.setIncludeFontPadding(false);
        tv.setSingleLine(false);
        tv.setMaxLines(2);
        applyTextDirection(tv);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tvLp.setMargins(fa ? 0 : dp(12), 0, fa ? dp(12) : 0, 0);
        tv.setLayoutParams(tvLp);
        row.addView(tv);
        row.setOnClickListener(v -> {
            dialog.dismiss();
            callback.onPicked(category);
        });
        return row;
    }

    private List<ExpenseStore.Category> parentChoices(String excludeId) {
        List<ExpenseStore.Category> choices = new ArrayList<>();
        for (ExpenseStore.Category c : store.categories()) {
            if (c.id.equals(excludeId)) continue;
            choices.add(c);
        }
        return choices;
    }

    private String parentLabel(String parentId, String ownId) {
        if (parentId == null || parentId.isEmpty()) return noParentLabel();
        for (ExpenseStore.Category c : store.categories()) {
            if (c.id.equals(parentId) && !c.id.equals(ownId)) return c.label(fa);
        }
        return noParentLabel();
    }

    private boolean categoryExists(String id) {
        for (ExpenseStore.Category c : store.categories()) {
            if (c.id.equals(id)) return true;
        }
        return false;
    }

    private void confirmDeleteCategory(ExpenseStore.Category category, Dialog editDialog) {
        Dialog dialog = new Dialog(this);
        LinearLayout root = dialogRoot(deleteCategoryLabel());
        root.addView(subtitle(deleteCategoryMessage()));
        TextView name = labelText(category.label(fa), accent2);
        name.setTextSize(16);
        name.setPadding(0, dp(8), 0, dp(12));
        root.addView(name);
        LinearLayout actions = dialogActions();
        actions.addView(dialogButton(cancelLabel(), false, dialog::dismiss));
        actions.addView(dialogButton(deleteLabel(), true, () -> {
            deleteCategory(category.id);
            dialog.dismiss();
            editDialog.dismiss();
            showCategories();
        }));
        root.addView(actions);
        showMaterialDialog(dialog, root);
    }

    private void deleteCategory(String id) {
        List<ExpenseStore.Category> categories = store.categories();
        for (int i = categories.size() - 1; i >= 0; i--) {
            ExpenseStore.Category c = categories.get(i);
            if (c.id.equals(id)) {
                categories.remove(i);
            } else if (id.equals(c.parentId)) {
                c.parentId = "";
            }
        }
        store.saveCategories(categories);

        List<ExpenseStore.Expense> expenses = store.expenses();
        for (ExpenseStore.Expense e : expenses) {
            for (int i = e.splits.size() - 1; i >= 0; i--) {
                if (id.equals(e.splits.get(i).categoryId)) {
                    e.splits.remove(i);
                }
            }
        }
        store.saveExpenses(expenses);
        ExpenseWidgetProvider.updateAll(this);
    }

    private ImageView iconBadge(int icon, int bgColor, int iconColor) {
        return iconButton(icon, bgColor, iconColor, 44, 18);
    }

    private ImageView iconButton(int icon, int bgColor, int iconColor, int sizeDp, int radiusDp) {
        ImageView view = new ImageView(this);
        view.setImageDrawable(new IconDrawable(icon, iconColor, fa));
        view.setBackground(rounded(bgColor, dp(radiusDp), Color.TRANSPARENT, 0));
        view.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        view.setLayoutParams(lp);
        return view;
    }

    private int[] categoryPalette(int selectedColor) {
        int[] base = new int[]{
                Color.rgb(229, 57, 53), Color.rgb(244, 67, 54), Color.rgb(251, 140, 0), Color.rgb(251, 188, 4),
                Color.rgb(253, 216, 53), Color.rgb(124, 179, 66), Color.rgb(52, 168, 83), Color.rgb(0, 150, 136),
                Color.rgb(0, 137, 123), Color.rgb(0, 172, 193), Color.rgb(3, 169, 244), Color.rgb(26, 115, 232),
                Color.rgb(57, 73, 171), Color.rgb(94, 53, 177), Color.rgb(123, 31, 162), Color.rgb(142, 36, 170),
                Color.rgb(217, 48, 105), Color.rgb(236, 64, 122), Color.rgb(121, 85, 72), Color.rgb(96, 125, 139),
                Color.rgb(117, 117, 117), Color.rgb(33, 150, 243), Color.rgb(0, 188, 212), Color.rgb(139, 195, 74)
        };
        for (int color : base) {
            if (color == selectedColor) return base;
        }
        int[] palette = new int[base.length + 1];
        palette[0] = selectedColor;
        System.arraycopy(base, 0, palette, 1, base.length);
        return palette;
    }

    private void setColorSwatchState(TextView swatch, int color, boolean selected) {
        swatch.setText(selected ? "✓" : "");
        swatch.setTextColor(contrast(color));
        swatch.setBackground(rounded(color, dp(18), selected ? contrast(color) : Color.TRANSPARENT, selected ? dp(3) : 0));
    }

    private TextView miniBadge(String s, int bgColor, int textColor) {
        TextView badge = new TextView(this);
        badge.setText(localText(s));
        badge.setGravity(Gravity.CENTER);
        badge.setTextSize(20);
        badge.setTextColor(textColor);
        badge.setTypeface(font, Typeface.BOLD);
        badge.setIncludeFontPadding(false);
        applyTextDirection(badge);
        badge.setBackground(rounded(bgColor, dp(18), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        badge.setLayoutParams(lp);
        return badge;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(rounded(surface, dp(24), outline, dark ? dp(1) : 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(8));
        card.setLayoutParams(lp);
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(1));
        return card;
    }

    private TextView option(String s, final Runnable action) {
        TextView b = new TextView(this);
        b.setText(localText(s));
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(font, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setSingleLine(false);
        b.setMaxLines(2);
        b.setMinWidth(0);
        b.setMinHeight(0);
        applyTextDirection(b);
        b.setBackground(rounded(accent, dp(24), Color.TRANSPARENT, 0));
        b.setOnClickListener(v -> action.run());
        b.setPadding(dp(18), dp(8), dp(18), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        if (Build.VERSION.SDK_INT >= 21) {
            b.setElevation(0);
            b.setStateListAnimator(null);
        }
        return b;
    }

    private TextView smallButton(String s, final Runnable action) {
        TextView b = option(s, action);
        b.setTextColor(accent);
        b.setTextSize(13);
        b.setBackground(rounded(surfaceAlt, dp(18), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(58), 1);
        lp.setMargins(dp(4), dp(5), dp(4), dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private TextView chip(String s) {
        TextView t = new TextView(this);
        t.setText(localText(s));
        t.setGravity(Gravity.CENTER);
        t.setTextSize(15);
        t.setTypeface(font, Typeface.BOLD);
        t.setIncludeFontPadding(false);
        t.setSingleLine(false);
        t.setMaxLines(2);
        t.setTextColor(text);
        applyTextDirection(t);
        t.setBackground(rounded(surfaceAlt, dp(20), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(38), dp(42));
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        t.setLayoutParams(lp);
        if (Build.VERSION.SDK_INT >= 21) t.setElevation(0);
        return t;
    }

    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(localText(hint));
        e.setInputType(type);
        e.setTextColor(text);
        e.setHintTextColor(muted);
        e.setTypeface(font);
        e.setTextSize(16);
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setSingleLine(false);
        e.setTextDirection(fa ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        e.setGravity((fa ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        e.setBackground(rounded(surface, dp(16), outline, dp(1)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        lp.setMargins(0, dp(7), 0, dp(7));
        e.setLayoutParams(lp);
        return e;
    }

    private void forceLeftToRight(EditText input) {
        input.setTextDirection(View.TEXT_DIRECTION_LTR);
        input.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
    }

    private TextView amountWordsView(EditText input) {
        TextView words = subtitle("");
        words.setTextColor(accent);
        words.setPadding(dp(4), 0, dp(4), dp(8));
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                long value = parseLong(s.toString());
                words.setText(localText(value > 0 ? numberToWords(value) : ""));
            }
        });
        long value = parseLong(input.getText().toString());
        words.setText(localText(value > 0 ? numberToWords(value) : ""));
        return words;
    }

    private String numberToWords(long value) {
        if (fa) return persianNumberToWords(value);
        if (value == 0) return "zero";
        if (value < 0) return "minus " + numberToWords(-value);
        String[] scale = new String[]{"", "thousand", "million", "billion", "trillion"};
        StringBuilder out = new StringBuilder();
        int group = 0;
        while (value > 0 && group < scale.length) {
            int part = (int) (value % 1000);
            if (part > 0) {
                String words = underThousand(part);
                if (!scale[group].isEmpty()) words += " " + scale[group];
                if (out.length() > 0) out.insert(0, " ");
                out.insert(0, words);
            }
            value /= 1000;
            group++;
        }
        return out.toString();
    }

    private String persianNumberToWords(long value) {
        if (value == 0) return "صفر";
        if (value < 0) return "منفی " + persianNumberToWords(-value);
        String[] scales = new String[]{"", "هزار", "میلیون", "میلیارد", "تریلیون"};
        ArrayList<String> parts = new ArrayList<>();
        int group = 0;
        while (value > 0 && group < scales.length) {
            int part = (int) (value % 1000);
            if (part > 0) {
                String words = persianUnderThousand(part);
                if (!scales[group].isEmpty()) words += " " + scales[group];
                parts.add(0, words);
            }
            value /= 1000;
            group++;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(" و ");
            out.append(parts.get(i));
        }
        return out.toString();
    }

    private String persianUnderThousand(int value) {
        String[] ones = new String[]{"", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه"};
        String[] teens = new String[]{"ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده"};
        String[] tens = new String[]{"", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود"};
        String[] hundreds = new String[]{"", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد"};
        ArrayList<String> parts = new ArrayList<>();
        if (value >= 100) {
            parts.add(hundreds[value / 100]);
            value %= 100;
        }
        if (value >= 20) {
            parts.add(tens[value / 10]);
            value %= 10;
            if (value > 0) parts.add(ones[value]);
        } else if (value >= 10) {
            parts.add(teens[value - 10]);
        } else if (value > 0) {
            parts.add(ones[value]);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(" و ");
            out.append(parts.get(i));
        }
        return out.toString();
    }

    private String underThousand(int value) {
        String[] ones = new String[]{"", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
        String[] tens = new String[]{"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
        StringBuilder out = new StringBuilder();
        if (value >= 100) {
            out.append(ones[value / 100]).append(" hundred");
            value %= 100;
            if (value > 0) out.append(" ");
        }
        if (value >= 20) {
            out.append(tens[value / 10]);
            value %= 10;
            if (value > 0) out.append(" ").append(ones[value]);
        } else if (value > 0) {
            out.append(ones[value]);
        }
        return out.toString();
    }

    private String shortText(String s) {
        s = s.replace('\n', ' ');
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }

    private long parseLong(String s) {
        try {
            return Long.parseLong(normalizeDigits(s).replace(",", "").replace("٬", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private String normalizeDigits(String s) {
        if (s == null) return "";
        char[] persian = {'۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'};
        char[] arabic = {'٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩'};
        for (int i = 0; i < 10; i++) {
            s = s.replace(persian[i], (char) ('0' + i));
            s = s.replace(arabic[i], (char) ('0' + i));
        }
        return s;
    }

    private int contrast(int color) {
        int y = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000;
        return y >= 140 ? Color.rgb(24, 29, 36) : Color.WHITE;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private GradientDrawable gradient(int start, int end, int radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int soften(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private class PatternDrawable extends android.graphics.drawable.Drawable {
        private final int backgroundColor;
        private final int washColor;
        private final int colorA;
        private final int colorB;
        private final int colorC;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        PatternDrawable(int backgroundColor, int washColor, int colorA, int colorB, int colorC) {
            this.backgroundColor = backgroundColor;
            this.washColor = washColor;
            this.colorA = colorA;
            this.colorB = colorB;
            this.colorC = colorC;
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawColor(backgroundColor);
            p.setStyle(Paint.Style.FILL);
            p.setColor(soften(washColor, dark ? 46 : 120));
            canvas.drawRoundRect(dp(10), dp(72), getBounds().right - dp(10), dp(126), dp(28), dp(28), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(soften(colorA, dark ? 44 : 30));
            int step = dp(28);
            for (int x = -getBounds().bottom; x < getBounds().right + getBounds().bottom; x += step) {
                canvas.drawLine(x, dp(152), x + getBounds().bottom, getBounds().bottom, p);
            }
            p.setColor(soften(colorB, dark ? 42 : 28));
            for (int y = dp(94); y < getBounds().bottom + dp(60); y += dp(72)) {
                for (int x = dp(20); x < getBounds().right + dp(40); x += dp(72)) {
                    canvas.drawRoundRect(x, y, x + dp(14), y + dp(14), dp(5), dp(5), p);
                }
            }
        }

        @Override
        public void setAlpha(int alpha) {
            p.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            p.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.OPAQUE;
        }
    }

    private class HeroDrawable extends android.graphics.drawable.Drawable {
        private final int colorA;
        private final int colorB;
        private final int colorC;
        private final boolean darkMode;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        HeroDrawable(int colorA, int colorB, int colorC, boolean darkMode) {
            this.colorA = colorA;
            this.colorB = colorB;
            this.colorC = colorC;
            this.darkMode = darkMode;
        }

        @Override
        public void draw(Canvas canvas) {
            GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{colorA, colorB, colorC});
            base.setBounds(getBounds());
            base.setCornerRadius(dp(28));
            base.draw(canvas);

            p.setStyle(Paint.Style.FILL);
            p.setColor(soften(Color.WHITE, darkMode ? 20 : 34));
            canvas.drawCircle(getBounds().right - dp(42), getBounds().top + dp(34), dp(84), p);
            p.setColor(soften(Color.WHITE, darkMode ? 16 : 28));
            canvas.drawCircle(getBounds().left + dp(34), getBounds().bottom - dp(22), dp(70), p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(soften(Color.WHITE, darkMode ? 38 : 58));
            for (int y = getBounds().top + dp(34); y < getBounds().bottom; y += dp(26)) {
                canvas.drawLine(getBounds().left + dp(18), y, getBounds().right - dp(18), y + dp(54), p);
            }

            p.setStyle(Paint.Style.FILL);
            p.setColor(soften(Color.WHITE, darkMode ? 34 : 52));
            for (int x = getBounds().left + dp(24); x < getBounds().right - dp(20); x += dp(34)) {
                canvas.drawRoundRect(x, getBounds().bottom - dp(26), x + dp(18), getBounds().bottom - dp(21), dp(3), dp(3), p);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            p.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            p.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.OPAQUE;
        }
    }

    private class IconDrawable extends android.graphics.drawable.Drawable {
        private final int icon;
        private final int color;
        private final boolean rtl;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        IconDrawable(int icon, int color, boolean rtl) {
            this.icon = icon;
            this.color = color;
            this.rtl = rtl;
        }

        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            float l = getBounds().left;
            float t = getBounds().top;
            float cx = l + w / 2f;
            float cy = t + h / 2f;
            float s = Math.min(w, h);
            p.setColor(color);
            p.setStrokeWidth(Math.max(2f, s * 0.09f));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStyle(Paint.Style.STROKE);

            if (icon == ICON_ADD) {
                canvas.drawLine(cx, t + s * 0.24f, cx, t + s * 0.76f, p);
                canvas.drawLine(l + s * 0.24f, cy, l + s * 0.76f, cy, p);
            } else if (icon == ICON_CATEGORY) {
                p.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(l + s * 0.20f, t + s * 0.20f, l + s * 0.44f, t + s * 0.44f, s * 0.07f, s * 0.07f, p);
                canvas.drawRoundRect(l + s * 0.56f, t + s * 0.20f, l + s * 0.80f, t + s * 0.44f, s * 0.07f, s * 0.07f, p);
                canvas.drawRoundRect(l + s * 0.20f, t + s * 0.56f, l + s * 0.44f, t + s * 0.80f, s * 0.07f, s * 0.07f, p);
                canvas.drawRoundRect(l + s * 0.56f, t + s * 0.56f, l + s * 0.80f, t + s * 0.80f, s * 0.07f, s * 0.07f, p);
            } else if (icon == ICON_SUBCATEGORY) {
                canvas.drawRoundRect(l + s * 0.18f, t + s * 0.22f, l + s * 0.50f, t + s * 0.54f, s * 0.08f, s * 0.08f, p);
                canvas.drawRoundRect(l + s * 0.50f, t + s * 0.46f, l + s * 0.82f, t + s * 0.78f, s * 0.08f, s * 0.08f, p);
                canvas.drawLine(l + s * 0.43f, t + s * 0.50f, l + s * 0.55f, t + s * 0.50f, p);
            } else if (icon == ICON_HISTORY) {
                canvas.drawCircle(cx, cy, s * 0.30f, p);
                canvas.drawLine(cx, cy, cx, t + s * 0.34f, p);
                canvas.drawLine(cx, cy, l + s * 0.66f, t + s * 0.62f, p);
                canvas.drawLine(l + s * 0.22f, t + s * 0.26f, l + s * 0.22f, t + s * 0.48f, p);
                canvas.drawLine(l + s * 0.22f, t + s * 0.26f, l + s * 0.42f, t + s * 0.26f, p);
            } else if (icon == ICON_SETTINGS) {
                canvas.drawLine(l + s * 0.24f, t + s * 0.30f, l + s * 0.76f, t + s * 0.30f, p);
                canvas.drawLine(l + s * 0.24f, t + s * 0.50f, l + s * 0.76f, t + s * 0.50f, p);
                canvas.drawLine(l + s * 0.24f, t + s * 0.70f, l + s * 0.76f, t + s * 0.70f, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(l + s * 0.42f, t + s * 0.30f, s * 0.08f, p);
                canvas.drawCircle(l + s * 0.62f, t + s * 0.50f, s * 0.08f, p);
                canvas.drawCircle(l + s * 0.36f, t + s * 0.70f, s * 0.08f, p);
            } else if (icon == ICON_UNCATEGORIZED) {
                Path path = new Path();
                path.moveTo(l + s * 0.25f, t + s * 0.24f);
                path.lineTo(l + s * 0.62f, t + s * 0.24f);
                path.lineTo(l + s * 0.80f, t + s * 0.42f);
                path.lineTo(l + s * 0.42f, t + s * 0.80f);
                path.lineTo(l + s * 0.24f, t + s * 0.62f);
                path.close();
                canvas.drawPath(path, p);
                canvas.drawCircle(l + s * 0.44f, t + s * 0.42f, s * 0.035f, p);
            } else if (icon == ICON_BACK) {
                Path path = new Path();
                if (rtl) {
                    path.moveTo(l + s * 0.36f, t + s * 0.24f);
                    path.lineTo(l + s * 0.66f, cy);
                    path.lineTo(l + s * 0.36f, t + s * 0.76f);
                } else {
                    path.moveTo(l + s * 0.64f, t + s * 0.24f);
                    path.lineTo(l + s * 0.34f, cy);
                    path.lineTo(l + s * 0.64f, t + s * 0.76f);
                }
                canvas.drawPath(path, p);
            } else if (icon == ICON_LANGUAGE) {
                canvas.drawCircle(cx, cy, s * 0.30f, p);
                canvas.drawLine(l + s * 0.22f, cy, l + s * 0.78f, cy, p);
                canvas.drawLine(cx, t + s * 0.22f, cx, t + s * 0.78f, p);
                canvas.drawOval(new RectF(l + s * 0.34f, t + s * 0.22f, l + s * 0.66f, t + s * 0.78f), p);
            } else if (icon == ICON_THEME) {
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, s * 0.22f, p);
                p.setStyle(Paint.Style.STROKE);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4.0;
                    float x1 = cx + (float) Math.cos(a) * s * 0.34f;
                    float y1 = cy + (float) Math.sin(a) * s * 0.34f;
                    float x2 = cx + (float) Math.cos(a) * s * 0.43f;
                    float y2 = cy + (float) Math.sin(a) * s * 0.43f;
                    canvas.drawLine(x1, y1, x2, y2, p);
                }
            } else if (icon == ICON_PERIOD) {
                canvas.drawRoundRect(l + s * 0.22f, t + s * 0.26f, l + s * 0.78f, t + s * 0.78f, s * 0.08f, s * 0.08f, p);
                canvas.drawLine(l + s * 0.22f, t + s * 0.40f, l + s * 0.78f, t + s * 0.40f, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(l + s * 0.40f, t + s * 0.58f, s * 0.05f, p);
                canvas.drawCircle(l + s * 0.60f, t + s * 0.58f, s * 0.05f, p);
            } else if (icon == ICON_PARENT) {
                canvas.drawLine(cx, t + s * 0.22f, cx, t + s * 0.58f, p);
                canvas.drawLine(cx, t + s * 0.58f, l + s * 0.34f, t + s * 0.76f, p);
                canvas.drawLine(cx, t + s * 0.58f, l + s * 0.66f, t + s * 0.76f, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, t + s * 0.20f, s * 0.08f, p);
                canvas.drawCircle(l + s * 0.34f, t + s * 0.78f, s * 0.08f, p);
                canvas.drawCircle(l + s * 0.66f, t + s * 0.78f, s * 0.08f, p);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            p.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            p.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return dp(24);
        }

        @Override
        public int getIntrinsicHeight() {
            return dp(24);
        }
    }

    public static class ChartView extends View {
        private final Map<String, Long> totals;
        private final Map<String, ExpenseStore.Category> categories = new LinkedHashMap<>();
        private final boolean fa;
        private final int textColor;
        private final Typeface font;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public ChartView(Context context, Map<String, Long> totals, List<ExpenseStore.Category> cats, boolean fa, int textColor, Typeface font) {
            super(context);
            this.totals = totals;
            this.fa = fa;
            this.textColor = textColor;
            this.font = font;
            for (ExpenseStore.Category c : cats) categories.put(c.id, c);
            setMinimumHeight((int) (190 * context.getResources().getDisplayMetrics().density));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(198), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (totals != null) {
                drawDonut(canvas);
                return;
            }
            long sum = 0;
            for (long v : totals.values()) sum += v;
            paint.setTextSize(dp(12));
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            if (sum == 0) {
                paint.setColor(textColor);
                canvas.drawText(fa ? "هنوز خرجی ثبت نشده" : "No expenses yet", dp(8), dp(36), paint);
                return;
            }
            int left = dp(8);
            int top = dp(16);
            int width = getWidth() - dp(16);
            int y = top;
            for (Map.Entry<String, Long> entry : totals.entrySet()) {
                ExpenseStore.Category c = categories.get(entry.getKey());
                int color = c == null ? Color.rgb(173, 181, 189) : c.color;
                String label = c == null ? (fa ? "بدون دسته" : "Uncategorized") : c.label(fa);
                int bar = (int) (width * (entry.getValue() / (float) sum));
                paint.setColor(color);
                canvas.drawRoundRect(left, y, left + Math.max(dp(8), bar), y + dp(18), dp(9), dp(9), paint);
                paint.setColor(textColor);
                canvas.drawText(label + "  " + ExpenseStore.money(entry.getValue(), fa), left, y + dp(38), paint);
                y += dp(52);
                if (y > getHeight() - dp(20)) break;
            }
        }

        private void drawDonut(Canvas canvas) {
            long sum = 0;
            for (long v : totals.values()) sum += v;
            paint.setTypeface(font);
            paint.setStrokeCap(Paint.Cap.BUTT);
            int chartSize = Math.min(dp(124), Math.max(dp(96), getWidth() / 3));
            int chartInset = dp(14);
            int top = dp(26);
            int left = fa ? getWidth() - chartSize - chartInset : chartInset;
            RectF oval = new RectF(left, top, left + chartSize, top + chartSize);
            int centerX = left + chartSize / 2;
            int centerY = top + chartSize / 2;

            if (sum == 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(18));
                paint.setColor(Color.argb(90, 255, 255, 255));
                canvas.drawArc(oval, -90, 360, false, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(Typeface.create(font, Typeface.BOLD));
                paint.setTextSize(dp(12));
                paint.setColor(textColor);
                canvas.drawText(fa ? "هنوز خرجی ثبت نشده" : "No expenses", centerX, centerY + dp(4), paint);
                return;
            }

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(20));
            float start = -90f;
            float drawn = 0f;
            ArrayList<Map.Entry<String, Long>> entries = new ArrayList<>(totals.entrySet());
            for (int i = 0; i < entries.size(); i++) {
                Map.Entry<String, Long> entry = entries.get(i);
                ExpenseStore.Category c = categories.get(entry.getKey());
                int color = c == null ? Color.rgb(173, 181, 189) : c.color;
                float sweep = i == entries.size() - 1 ? 360f - drawn : 360f * entry.getValue() / (float) sum;
                paint.setColor(color);
                canvas.drawArc(oval, start, sweep, false, paint);
                start += sweep;
                drawn += sweep;
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(55, 255, 255, 255));
            canvas.drawCircle(centerX, centerY, chartSize / 2f - dp(24), paint);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(font, Typeface.BOLD));
            paint.setTextSize(dp(13));
            paint.setColor(textColor);
            canvas.drawText(ExpenseStore.localNumber(totals.size(), fa), centerX, centerY - dp(2), paint);
            paint.setTextSize(dp(10));
            canvas.drawText(fa ? "دسته" : "items", centerX, centerY + dp(14), paint);

            int legendLeft = fa ? dp(8) : left + chartSize + dp(22);
            int y = dp(28);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTypeface(font);
            paint.setTextSize(dp(11));
            int count = 0;
            int visibleLimit = totals.size() > 5 ? 4 : 5;
            for (Map.Entry<String, Long> entry : totals.entrySet()) {
                ExpenseStore.Category c = categories.get(entry.getKey());
                int color = c == null ? Color.rgb(173, 181, 189) : c.color;
                String label = c == null ? (fa ? "بدون دسته" : "Uncategorized") : c.label(fa);
                int percent = Math.round(100f * entry.getValue() / (float) sum);
                paint.setColor(color);
                canvas.drawRoundRect(legendLeft, y, legendLeft + dp(14), y + dp(14), dp(7), dp(7), paint);
                paint.setColor(textColor);
                canvas.drawText(shortCanvas(label, 16) + "  " + ExpenseStore.localPercent(percent, fa), legendLeft + dp(22), y + dp(12), paint);
                y += dp(26);
                count++;
                if (count == visibleLimit || y > getHeight() - dp(38)) break;
            }
            int extra = totals.size() - count;
            if (extra > 0 && y <= getHeight() - dp(12)) {
                paint.setColor(Color.argb(150, Color.red(textColor), Color.green(textColor), Color.blue(textColor)));
                String more = fa ? ExpenseStore.localNumber(extra, true) + " مورد دیگر" : "+" + extra + " more";
                canvas.drawText(more, legendLeft + dp(22), y + dp(12), paint);
            }
        }

        private String shortCanvas(String s, int max) {
            return s.length() > max ? s.substring(0, max - 1) + "..." : s;
        }

        private int dp(int v) {
            return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}
