package com.codex.expensekeeper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsExpenseReceiver extends BroadcastReceiver {
    private static final String TRANSFER_PREFS = "sms_transfer_guard";
    private static final long TRANSFER_MATCH_WINDOW_MS = 10 * 60 * 1000L;
    private static final Pattern LABELLED_AMOUNT = Pattern.compile("(?m)^\\s*مبلغ\\s*[:：]\\s*([0-9,۰-۹٠-٩]+)");
    private static final Pattern MINUS_AMOUNT = Pattern.compile("(?m)^\\s*-\\s*([0-9,۰-۹٠-٩]+)\\s*$");
    private static final Pattern SMS_TIME = Pattern.compile("(\\d{2}/\\d{2}/\\d{2}[_\\s]\\d{2}:\\d{2})");

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        StringBuilder body = new StringBuilder();
        for (SmsMessage msg : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
            body.append(msg.getMessageBody()).append('\n');
        }
        SmsEvent event = parseSmsEvent(body.toString());
        if (event == null || event.amountRial <= 0) return;

        ExpenseStore store = new ExpenseStore(context);
        if (event.deposit) {
            if (removeMatchingPendingWithdrawal(context, store, event)) {
                ExpenseWidgetProvider.updateAll(context);
                return;
            }
            rememberPending(context, "deposit", event, "");
            return;
        }
        if (hasMatchingPending(context, "deposit", event)) {
            clearPending(context, "deposit");
            return;
        }

        ExpenseStore.Expense expense = new ExpenseStore.Expense();
        expense.amount = event.amountRial / 10;
        expense.description = body.toString().trim();
        expense.source = "sms";
        store.addExpense(expense);
        rememberPending(context, "withdraw", event, expense.id);
        ExpenseWidgetProvider.updateAll(context);
    }

    public static Long parseExpenseAmount(String body) {
        SmsEvent event = parseSmsEvent(body);
        return event == null || !event.withdraw ? null : event.amountRial / 10;
    }

    private static SmsEvent parseSmsEvent(String body) {
        String normalized = normalizeText(body);
        Long amount = null;
        Matcher minus = MINUS_AMOUNT.matcher(normalized);
        boolean minusMatched = minus.find();
        if (minusMatched) {
            amount = parseNumber(minus.group(1));
        } else {
            Matcher labelled = LABELLED_AMOUNT.matcher(normalized);
            if (labelled.find()) amount = parseNumber(labelled.group(1));
        }
        if (amount == null) return null;

        boolean deposit = normalized.contains("واریز");
        boolean withdraw = normalized.contains("برداشت") || normalized.contains("خرید") || minusMatched;
        if (!deposit && !withdraw) return null;

        SmsEvent event = new SmsEvent();
        event.amountRial = amount;
        event.deposit = deposit && !withdraw;
        event.withdraw = withdraw;
        event.signature = amount + ":" + smsTimeToken(normalized);
        return event;
    }

    private static boolean hasMatchingPending(Context context, String kind, SmsEvent event) {
        SharedPreferences prefs = context.getSharedPreferences(TRANSFER_PREFS, Context.MODE_PRIVATE);
        long savedAt = prefs.getLong(kind + "_saved_at", 0);
        return event.signature.equals(prefs.getString(kind + "_signature", "")) && System.currentTimeMillis() - savedAt <= TRANSFER_MATCH_WINDOW_MS;
    }

    private static boolean removeMatchingPendingWithdrawal(Context context, ExpenseStore store, SmsEvent event) {
        SharedPreferences prefs = context.getSharedPreferences(TRANSFER_PREFS, Context.MODE_PRIVATE);
        long savedAt = prefs.getLong("withdraw_saved_at", 0);
        String expenseId = prefs.getString("withdraw_expense_id", "");
        if (!event.signature.equals(prefs.getString("withdraw_signature", "")) || System.currentTimeMillis() - savedAt > TRANSFER_MATCH_WINDOW_MS || expenseId.isEmpty()) {
            return false;
        }
        List<ExpenseStore.Expense> expenses = store.expenses();
        boolean removed = false;
        for (int i = expenses.size() - 1; i >= 0; i--) {
            if (expenseId.equals(expenses.get(i).id)) {
                expenses.remove(i);
                removed = true;
            }
        }
        if (removed) store.saveExpenses(expenses);
        clearPending(context, "withdraw");
        return removed;
    }

    private static void rememberPending(Context context, String kind, SmsEvent event, String expenseId) {
        SharedPreferences.Editor editor = context.getSharedPreferences(TRANSFER_PREFS, Context.MODE_PRIVATE).edit()
                .putString(kind + "_signature", event.signature)
                .putLong(kind + "_saved_at", System.currentTimeMillis());
        if (!expenseId.isEmpty()) editor.putString(kind + "_expense_id", expenseId);
        editor.apply();
    }

    private static void clearPending(Context context, String kind) {
        context.getSharedPreferences(TRANSFER_PREFS, Context.MODE_PRIVATE).edit()
                .remove(kind + "_signature")
                .remove(kind + "_saved_at")
                .remove(kind + "_expense_id")
                .apply();
    }

    private static String smsTimeToken(String body) {
        Matcher matcher = SMS_TIME.matcher(body);
        return matcher.find() ? matcher.group(1).replace(' ', '_') : "no_time";
    }

    private static long parseNumber(String raw) {
        return Long.parseLong(normalizeDigits(raw).replace(",", "").trim());
    }

    private static String normalizeText(String s) {
        return normalizeDigits(s).replace('ي', 'ی').replace('ك', 'ک');
    }

    private static String normalizeDigits(String s) {
        char[] fa = {'۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'};
        char[] ar = {'٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩'};
        for (int i = 0; i < fa.length; i++) {
            s = s.replace(fa[i], (char) ('0' + i));
            s = s.replace(ar[i], (char) ('0' + i));
        }
        return s;
    }

    private static class SmsEvent {
        long amountRial;
        boolean deposit;
        boolean withdraw;
        String signature;
    }
}
