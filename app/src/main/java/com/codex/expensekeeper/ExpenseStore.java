package com.codex.expensekeeper;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ExpenseStore {
    private static final String PREFS = "expense_store";
    private static final String KEY_EXPENSES = "expenses";
    private static final String KEY_CATEGORIES = "categories";
    private final SharedPreferences prefs;

    public ExpenseStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureDefaults();
        migrateSmsAmountsToToman();
    }

    public boolean isConfigured() {
        return prefs.getBoolean("configured", false);
    }

    public void setConfigured(boolean configured) {
        prefs.edit().putBoolean("configured", configured).apply();
    }

    public String language() {
        return prefs.getString("language", "fa");
    }

    public void setLanguage(String language) {
        prefs.edit().putString("language", language).commit();
    }

    public String theme() {
        return prefs.getString("theme", "system");
    }

    public void setTheme(String theme) {
        prefs.edit().putString("theme", theme).apply();
    }

    public int periodStartDay() {
        return prefs.getInt("periodStartDay", 1);
    }

    public void setPeriodStartDay(int day) {
        prefs.edit().putInt("periodStartDay", day).apply();
    }

    public List<Category> categories() {
        List<Category> categories = new ArrayList<>();
        JSONArray arr = readArray(KEY_CATEGORIES);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) categories.add(Category.fromJson(o));
        }
        return categories;
    }

    public void saveCategories(List<Category> categories) {
        JSONArray arr = new JSONArray();
        for (Category c : categories) arr.put(c.toJson());
        prefs.edit().putString(KEY_CATEGORIES, arr.toString()).apply();
    }

    public List<Expense> expenses() {
        List<Expense> expenses = new ArrayList<>();
        JSONArray arr = readArray(KEY_EXPENSES);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) expenses.add(Expense.fromJson(o));
        }
        return expenses;
    }

    public void saveExpenses(List<Expense> expenses) {
        JSONArray arr = new JSONArray();
        for (Expense e : expenses) arr.put(e.toJson());
        prefs.edit().putString(KEY_EXPENSES, arr.toString()).apply();
    }

    public void addExpense(Expense expense) {
        List<Expense> all = expenses();
        all.add(0, expense);
        saveExpenses(all);
    }

    public long totalBetween(long start, long end) {
        long total = 0;
        for (Expense e : expenses()) {
            if (e.investment) continue;
            if (e.time >= start && e.time < end) total += e.amount;
        }
        return total;
    }

    public Map<String, Long> categoryTotals(long start, long end) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (Expense e : expenses()) {
            if (e.investment) continue;
            if (e.time < start || e.time >= end) continue;
            if (e.splits.isEmpty()) {
                totals.put("uncategorized", totals.containsKey("uncategorized") ? totals.get("uncategorized") + e.amount : e.amount);
            } else {
                for (Split s : e.splits) {
                    totals.put(s.categoryId, totals.containsKey(s.categoryId) ? totals.get(s.categoryId) + s.amount : s.amount);
                }
            }
        }
        return totals;
    }

    public static String money(long amount) {
        return money(amount, true);
    }

    public static String money(long amount, boolean fa) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        String formatted = new DecimalFormat("#,###", symbols).format(amount);
        return fa ? toPersianDigits(formatted) + " تومان" : formatted + " Toman";
    }

    public static String localNumber(long value, boolean fa) {
        String s = String.valueOf(value);
        return fa ? toPersianDigits(s) : s;
    }

    public static String localPercent(int value, boolean fa) {
        return fa ? toPersianDigits(String.valueOf(value)) + "٪" : value + "%";
    }

    public static String toPersianDigits(String s) {
        char[] digits = {'۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'};
        for (int i = 0; i < digits.length; i++) s = s.replace((char) ('0' + i), digits[i]);
        return s;
    }

    private JSONArray readArray(String key) {
        try {
            return new JSONArray(prefs.getString(key, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void migrateSmsAmountsToToman() {
        if (prefs.getBoolean("sms_toman_migration_done", false)) return;
        List<Expense> current = expenses();
        boolean changed = false;
        for (Expense e : current) {
            if ("sms".equals(e.source) && e.amount >= 10) {
                e.amount = e.amount / 10;
                changed = true;
            }
        }
        if (changed) saveExpenses(current);
        prefs.edit().putBoolean("sms_toman_migration_done", true).apply();
    }

    private void ensureDefaults() {
        if (!prefs.contains(KEY_CATEGORIES)) {
            saveCategories(defaultCategories());
            return;
        }

        Map<String, Category> defaults = new LinkedHashMap<>();
        for (Category c : defaultCategories()) defaults.put(c.id, c);
        List<Category> current = categories();
        boolean changed = false;
        for (Category c : current) {
            Category clean = defaults.get(c.id);
            if (clean == null) continue;
            if (isBrokenText(c.fa) || c.fa.isEmpty()) {
                c.fa = clean.fa;
                changed = true;
            }
            if (c.en.isEmpty()) {
                c.en = clean.en;
                changed = true;
            }
        }
        if (changed) {
            saveCategories(current);
        }
    }

    private List<Category> defaultCategories() {
        List<Category> seed = new ArrayList<>();
        seed.add(new Category("food", "Food", "خوراک", 0xFFFF7A59, ""));
        seed.add(new Category("fruit", "Fruit", "میوه", 0xFFFFB703, "food"));
        seed.add(new Category("travel_food", "Travel food", "غذای سفر", 0xFFFB8500, "food"));
        seed.add(new Category("online", "Online service", "خدمات آنلاین", 0xFF7BDFF2, ""));
        seed.add(new Category("rent", "Rent", "اجاره", 0xFF9B5DE5, ""));
        seed.add(new Category("transport", "Transport", "رفت‌وآمد", 0xFF00BBF9, ""));
        seed.add(new Category("home", "Home", "خانه", 0xFF80ED99, ""));
        seed.add(new Category("health", "Health", "سلامت", 0xFFF15BB5, ""));
        seed.add(new Category("cigarette", "Cigarette", "سیگار", 0xFFADB5BD, ""));
        return seed;
    }

    private boolean isBrokenText(String s) {
        return s.contains("Ø") || s.contains("Ù") || s.contains("Û") || s.contains("Ú") || s.contains("�");
    }

    public static class Category {
        public String id;
        public String en;
        public String fa;
        public int color;
        public String parentId;

        public Category(String id, String en, String fa, int color, String parentId) {
            this.id = id;
            this.en = en;
            this.fa = fa;
            this.color = color;
            this.parentId = parentId;
        }

        public String label(boolean isFa) {
            return isFa ? fa : en;
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("en", en);
                o.put("fa", fa);
                o.put("color", color);
                o.put("parentId", parentId);
            } catch (JSONException ignored) {
            }
            return o;
        }

        static Category fromJson(JSONObject o) {
            return new Category(o.optString("id"), o.optString("en"), o.optString("fa"), o.optInt("color"), o.optString("parentId"));
        }
    }

    public static class Expense {
        public String id = UUID.randomUUID().toString();
        public long time = System.currentTimeMillis();
        public long amount;
        public String description = "";
        public String source = "manual";
        public boolean investment;
        public List<Split> splits = new ArrayList<>();

        public boolean isUncategorized() {
            return splits.isEmpty();
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            JSONArray arr = new JSONArray();
            for (Split s : splits) arr.put(s.toJson());
            try {
                o.put("id", id);
                o.put("time", time);
                o.put("amount", amount);
                o.put("description", description);
                o.put("source", source);
                o.put("investment", investment);
                o.put("splits", arr);
            } catch (JSONException ignored) {
            }
            return o;
        }

        static Expense fromJson(JSONObject o) {
            Expense e = new Expense();
            e.id = o.optString("id", UUID.randomUUID().toString());
            e.time = o.optLong("time", System.currentTimeMillis());
            e.amount = o.optLong("amount");
            e.description = o.optString("description");
            e.source = o.optString("source", "manual");
            e.investment = o.optBoolean("investment", false);
            JSONArray arr = o.optJSONArray("splits");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.optJSONObject(i);
                    if (s != null) e.splits.add(Split.fromJson(s));
                }
            }
            return e;
        }
    }

    public static class Split {
        public String categoryId;
        public long amount;

        public Split(String categoryId, long amount) {
            this.categoryId = categoryId;
            this.amount = amount;
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("categoryId", categoryId);
                o.put("amount", amount);
            } catch (JSONException ignored) {
            }
            return o;
        }

        static Split fromJson(JSONObject o) {
            return new Split(o.optString("categoryId"), o.optLong("amount"));
        }
    }
}
