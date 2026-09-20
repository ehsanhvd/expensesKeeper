package com.codex.expensekeeper;

/** Run with javac/java; exercises draft arithmetic without an Android runtime. */
public final class ExpenseSplitDraftTest {
    public static void main(String[] args) {
        check(ExpenseSplitDraft.parseAmount("۱۲۳٬۴۵۶") == 123456, "Persian digits and grouping");
        check(ExpenseSplitDraft.parseAmount("١٢٣,٤٥٦") == 123456, "Arabic digits and grouping");
        check(ExpenseSplitDraft.parseAmount(" 10 000 ") == 10000, "pasted spaces");
        check(ExpenseSplitDraft.parseAmount("\u2066۱۲۳٬۴۵۶\u2069") == 123456, "pasted directional isolation");
        for (String bad : new String[]{"-1", "12.5", "hello", "99999999999999999999", ","}) {
            check(ExpenseSplitDraft.parseAmount(bad) == -1, "reject invalid amount: " + bad);
        }
        ExpenseSplitDraft draft = new ExpenseSplitDraft(100001);
        check(!draft.balance().ready(), "empty draft cannot save");
        ExpenseSplitDraft.Part food = new ExpenseSplitDraft.Part("food", "60000");
        ExpenseSplitDraft.Part travel = new ExpenseSplitDraft.Part("travel", "");
        draft.parts.add(food);
        draft.parts.add(travel);
        check(draft.balance().remaining == 40001 && !draft.balance().ready(), "incomplete allocation");
        check(draft.remainingFor(travel) == 40001, "fill remaining");
        travel.amount = "40001";
        check(draft.balance().ready(), "exact allocation");
        check(draft.remainingFor(food) == 60000, "remaining replaces the selected amount");
        travel.amount = "50000";
        check(draft.balance().remaining == -9999 && !draft.balance().ready(), "over allocation");
        check(draft.divideEqually(), "equal allocation available");
        check(food.amount.equals("50001") && travel.amount.equals("50000") && draft.balance().ready(), "odd total retained");
        travel.categoryId = "";
        check(!draft.balance().ready() && draft.balance().missingCategory, "category required");
        travel.categoryId = "travel";
        travel.amount = "0";
        check(!draft.balance().ready() && draft.balance().missingAmount, "positive amounts required");
        travel.amount = "invalid";
        check(draft.remainingFor(food) == -1 && draft.balance().invalidAmount, "invalid other row prevents remainder shortcut");
        food.amount = Long.toString(Long.MAX_VALUE);
        travel.amount = Long.toString(Long.MAX_VALUE);
        check(draft.balance().overflow && !draft.balance().ready(), "sum overflow rejected");
        check(draft.remainingFor(travel) == -1, "overdrawn other row prevents remainder shortcut");
        draft.parts.remove(travel);
        food.amount = "100001";
        check(draft.balance().ready(), "one category allowed after removing a row");
        ExpenseSplitDraft tiny = new ExpenseSplitDraft(1);
        tiny.parts.add(new ExpenseSplitDraft.Part("a", ""));
        tiny.parts.add(new ExpenseSplitDraft.Part("b", ""));
        check(!tiny.divideEqually(), "cannot divide into zero amounts");
        System.out.println("ExpenseSplitDraft checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
