package com.codex.expensekeeper;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ExpenseStore {
    public static final int BACKUP_SCHEMA_VERSION = 1;
    private static final String BACKUP_FORMAT = "expensekeeper-backup";
    private static final String PREFS = "expense_store";
    private static final String KEY_EXPENSES = "expenses";
    private static final String KEY_CATEGORIES = "categories";
    private static final String KEY_EXCLUDED_CATEGORIES = "excluded_categories";
    private static final String KEY_DELETED_DEFAULT_CATEGORIES = "deleted_default_categories";
    private static final String KEY_SMS_TOMAN_MIGRATION_DONE = "sms_toman_migration_done";
    private static final String KEY_SMS_TOMAN_RECONCILIATION_DONE = "sms_toman_reconciliation_done_v2";
    private static final String KEY_EXPENSE_SORT_MODE = "expense_sort_mode";
    private static final String CATEGORY_INVESTMENT = "investment";
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

    public int expenseSortMode() {
        int mode = prefs.getInt(KEY_EXPENSE_SORT_MODE, 0);
        return mode >= 0 && mode <= 3 ? mode : 0;
    }

    public void setExpenseSortMode(int mode) {
        prefs.edit().putInt(KEY_EXPENSE_SORT_MODE, mode >= 0 && mode <= 3 ? mode : 0).apply();
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
        prefs.edit().putString(KEY_CATEGORIES, arr.toString()).commit();
    }

    public void markDefaultCategoryDeleted(String categoryId) {
        Set<String> ids = readStringSet(KEY_DELETED_DEFAULT_CATEGORIES);
        ids.add(categoryId);
        saveStringSet(KEY_DELETED_DEFAULT_CATEGORIES, ids);
        Set<String> excluded = excludedCategoryIds();
        excluded.remove(categoryId);
        saveExcludedCategoryIds(excluded);
    }

    public Set<String> excludedCategoryIds() {
        return readStringSet(KEY_EXCLUDED_CATEGORIES);
    }

    public boolean isCategoryExcluded(String categoryId) {
        return excludedCategoryIds().contains(categoryId);
    }

    public void setCategoryExcluded(String categoryId, boolean excluded) {
        Set<String> ids = excludedCategoryIds();
        if (excluded) {
            ids.add(categoryId);
        } else {
            ids.remove(categoryId);
        }
        saveExcludedCategoryIds(ids);
    }

    public void includeAllCategories() {
        saveExcludedCategoryIds(new HashSet<>());
    }

    private void saveExcludedCategoryIds(Set<String> ids) {
        saveStringSet(KEY_EXCLUDED_CATEGORIES, ids);
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
        prefs.edit().putString(KEY_EXPENSES, arr.toString()).commit();
    }

    public void addExpense(Expense expense) {
        List<Expense> all = expenses();
        all.add(0, expense);
        saveExpenses(all);
    }

    public String createBackup(String appVersionName, long appVersionCode) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", BACKUP_FORMAT);
        root.put("schemaVersion", BACKUP_SCHEMA_VERSION);
        root.put("createdAt", System.currentTimeMillis());

        JSONObject app = new JSONObject();
        app.put("versionName", appVersionName);
        app.put("versionCode", appVersionCode);
        root.put("app", app);

        JSONObject data = new JSONObject();
        data.put("configured", isConfigured());
        data.put("language", language());
        data.put("theme", theme());
        data.put("periodStartDay", periodStartDay());

        JSONArray categories = new JSONArray();
        for (Category category : categories()) categories.put(category.toJson());
        data.put("categories", categories);

        JSONArray expenses = new JSONArray();
        for (Expense expense : expenses()) expenses.put(expense.toJson());
        data.put("expenses", expenses);
        data.put("excludedCategoryIds", stringArray(excludedCategoryIds()));
        data.put("deletedDefaultCategoryIds", stringArray(readStringSet(KEY_DELETED_DEFAULT_CATEGORIES)));
        root.put("data", data);
        return root.toString(2);
    }

    public RestoreSummary restoreBackup(String backupText) throws BackupException {
        try {
            JSONObject root = new JSONObject(backupText);
            if (!BACKUP_FORMAT.equals(root.optString("format"))) {
                throw new BackupException(BackupException.INVALID_FORMAT);
            }
            int schemaVersion = root.getInt("schemaVersion");
            if (schemaVersion > BACKUP_SCHEMA_VERSION) {
                throw new BackupException(BackupException.NEWER_VERSION);
            }
            if (schemaVersion < 1) {
                throw new BackupException(BackupException.UNSUPPORTED_VERSION);
            }
            root.getLong("createdAt");

            JSONObject app = root.getJSONObject("app");
            requiredString(app, "versionName");
            app.getLong("versionCode");

            // Parse each schema explicitly so future versions can add migrations here.
            JSONObject data = root.getJSONObject("data");
            boolean configured = data.getBoolean("configured");
            String language = requiredString(data, "language");
            String theme = requiredString(data, "theme");
            int periodStartDay = data.getInt("periodStartDay");
            if (!("fa".equals(language) || "en".equals(language))
                    || !("system".equals(theme) || "light".equals(theme) || "dark".equals(theme))
                    || periodStartDay < 1 || periodStartDay > 31) {
                throw new BackupException(BackupException.INVALID_FORMAT);
            }

            JSONArray categoryJson = data.getJSONArray("categories");
            JSONArray expenseJson = data.getJSONArray("expenses");
            JSONArray excludedJson = data.getJSONArray("excludedCategoryIds");
            JSONArray deletedJson = data.getJSONArray("deletedDefaultCategoryIds");
            validateCategories(categoryJson);
            validateExpenses(expenseJson);
            validateStringArray(excludedJson);
            validateStringArray(deletedJson);

            int localSortMode = expenseSortMode();
            SharedPreferences.Editor editor = prefs.edit().clear()
                    .putBoolean("configured", configured)
                    .putInt(KEY_EXPENSE_SORT_MODE, localSortMode)
                    .putString("language", language)
                    .putString("theme", theme)
                    .putInt("periodStartDay", periodStartDay)
                    .putString(KEY_CATEGORIES, categoryJson.toString())
                    .putString(KEY_EXPENSES, expenseJson.toString())
                    .putString(KEY_EXCLUDED_CATEGORIES, excludedJson.toString())
                    .putString(KEY_DELETED_DEFAULT_CATEGORIES, deletedJson.toString())
                    // Schema 1 stores SMS expenses in Toman, so never migrate them again.
                    .putBoolean(KEY_SMS_TOMAN_MIGRATION_DONE, true)
                    .putBoolean(KEY_SMS_TOMAN_RECONCILIATION_DONE, true);
            if (!editor.commit()) throw new BackupException(BackupException.WRITE_FAILED);
            return new RestoreSummary(expenseJson.length(), categoryJson.length());
        } catch (BackupException e) {
            throw e;
        } catch (JSONException | RuntimeException e) {
            throw new BackupException(BackupException.INVALID_FORMAT);
        }
    }

    private JSONArray stringArray(Set<String> values) {
        List<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        JSONArray result = new JSONArray();
        for (String value : sorted) result.put(value);
        return result;
    }

    private static String requiredString(JSONObject object, String key) throws JSONException, BackupException {
        if (!object.has(key) || object.isNull(key)) throw new BackupException(BackupException.INVALID_FORMAT);
        return object.getString(key);
    }

    private static void validateCategories(JSONArray categories) throws JSONException, BackupException {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.getJSONObject(i);
            String id = requiredString(category, "id");
            requiredString(category, "en");
            requiredString(category, "fa");
            category.getInt("color");
            requiredString(category, "parentId");
            if (id.isEmpty() || !ids.add(id)) throw new BackupException(BackupException.INVALID_FORMAT);
        }
    }

    private static void validateExpenses(JSONArray expenses) throws JSONException, BackupException {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < expenses.length(); i++) {
            JSONObject expense = expenses.getJSONObject(i);
            String id = requiredString(expense, "id");
            expense.getLong("time");
            expense.getLong("amount");
            requiredString(expense, "description");
            requiredString(expense, "source");
            expense.getBoolean("investment");
            if (id.isEmpty() || !ids.add(id)) throw new BackupException(BackupException.INVALID_FORMAT);
            JSONArray splits = expense.getJSONArray("splits");
            for (int j = 0; j < splits.length(); j++) {
                JSONObject split = splits.getJSONObject(j);
                if (requiredString(split, "categoryId").isEmpty()) {
                    throw new BackupException(BackupException.INVALID_FORMAT);
                }
                split.getLong("amount");
            }
        }
    }

    private static void validateStringArray(JSONArray values) throws JSONException, BackupException {
        for (int i = 0; i < values.length(); i++) {
            if (!(values.get(i) instanceof String)) throw new BackupException(BackupException.INVALID_FORMAT);
        }
    }

    public static class RestoreSummary {
        public final int expenseCount;
        public final int categoryCount;

        RestoreSummary(int expenseCount, int categoryCount) {
            this.expenseCount = expenseCount;
            this.categoryCount = categoryCount;
        }
    }

    public static class BackupException extends Exception {
        public static final int INVALID_FORMAT = 1;
        public static final int NEWER_VERSION = 2;
        public static final int UNSUPPORTED_VERSION = 3;
        public static final int WRITE_FAILED = 4;
        public final int reason;

        BackupException(int reason) {
            this.reason = reason;
        }
    }

    public long totalBetween(long start, long end) {
        long total = 0;
        Set<String> excluded = excludedCategoryIds();
        for (Expense e : expenses()) {
            if (e.investment) continue;
            if (e.time < start || e.time >= end) continue;
            if (e.splits.isEmpty()) {
                total += e.amount;
            } else {
                for (Split s : e.splits) {
                    if (!excluded.contains(s.categoryId)) total += s.amount;
                }
            }
        }
        return total;
    }

    public Map<String, Long> categoryTotals(long start, long end) {
        Map<String, Long> totals = new LinkedHashMap<>();
        Set<String> excluded = excludedCategoryIds();
        for (Expense e : expenses()) {
            if (e.investment) continue;
            if (e.time < start || e.time >= end) continue;
            if (e.splits.isEmpty()) {
                totals.put("uncategorized", totals.containsKey("uncategorized") ? totals.get("uncategorized") + e.amount : e.amount);
            } else {
                for (Split s : e.splits) {
                    if (excluded.contains(s.categoryId)) continue;
                    totals.put(s.categoryId, totals.containsKey(s.categoryId) ? totals.get(s.categoryId) + s.amount : s.amount);
                }
            }
        }
        return sortedTotals(totals);
    }

    private Map<String, Long> sortedTotals(Map<String, Long> totals) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(totals.entrySet());
        Collections.sort(entries, (a, b) -> Long.compare(b.getValue(), a.getValue()));
        Map<String, Long> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : entries) sorted.put(entry.getKey(), entry.getValue());
        return sorted;
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
        if (prefs.getBoolean(KEY_SMS_TOMAN_RECONCILIATION_DONE, false)) return;
        List<Expense> current = expenses();
        boolean changed = false;
        for (Expense e : current) {
            if (!"sms".equals(e.source) || e.amount < 10) continue;

            Long parsedToman = SmsExpenseReceiver.parseExpenseAmount(e.description);
            if (parsedToman != null && parsedToman > 0) {
                if (e.amount != parsedToman) {
                    e.amount = parsedToman;
                    changed = true;
                }
            } else if (!prefs.getBoolean(KEY_SMS_TOMAN_MIGRATION_DONE, false)) {
                e.amount = e.amount / 10;
                changed = true;
            }
        }
        if (changed) saveExpenses(current);
        prefs.edit()
                .putBoolean(KEY_SMS_TOMAN_MIGRATION_DONE, true)
                .putBoolean(KEY_SMS_TOMAN_RECONCILIATION_DONE, true)
                .apply();
    }

    private void ensureDefaults() {
        if (!prefs.contains(KEY_CATEGORIES)) {
            saveCategories(defaultCategories());
            setCategoryExcluded(CATEGORY_INVESTMENT, true);
            return;
        }

        Map<String, Category> defaults = new LinkedHashMap<>();
        for (Category c : defaultCategories()) defaults.put(c.id, c);
        List<Category> current = categories();
        Map<String, Category> currentById = new LinkedHashMap<>();
        for (Category c : current) currentById.put(c.id, c);
        Set<String> deletedDefaults = readStringSet(KEY_DELETED_DEFAULT_CATEGORIES);
        boolean changed = false;
        boolean addedInvestment = false;
        for (Category clean : defaults.values()) {
            if (!currentById.containsKey(clean.id) && !deletedDefaults.contains(clean.id) && CATEGORY_INVESTMENT.equals(clean.id)) {
                current.add(clean);
                changed = true;
                if (CATEGORY_INVESTMENT.equals(clean.id)) addedInvestment = true;
            }
        }
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
        if (addedInvestment) setCategoryExcluded(CATEGORY_INVESTMENT, true);
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
        seed.add(new Category(CATEGORY_INVESTMENT, "Investment", "سرمایه‌گذاری", 0xFF5E60CE, ""));
        return seed;
    }

    private boolean isBrokenText(String s) {
        return s.contains("Ø") || s.contains("Ù") || s.contains("Û") || s.contains("Ú") || s.contains("�");
    }

    private Set<String> readStringSet(String key) {
        Set<String> ids = new HashSet<>();
        JSONArray arr = readArray(key);
        for (int i = 0; i < arr.length(); i++) {
            String id = arr.optString(i, "");
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    private void saveStringSet(String key, Set<String> ids) {
        JSONArray arr = new JSONArray();
        for (String id : ids) arr.put(id);
        prefs.edit().putString(key, arr.toString()).commit();
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
