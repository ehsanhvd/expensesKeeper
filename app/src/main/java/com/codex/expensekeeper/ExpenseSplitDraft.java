package com.codex.expensekeeper;

import java.util.ArrayList;
import java.util.List;

/** Unsaved allocations. No expense is changed until the complete draft is committed. */
final class ExpenseSplitDraft {
    static final class Part {
        String categoryId;
        String amount;

        Part(String categoryId, String amount) {
            this.categoryId = categoryId;
            this.amount = amount;
        }
    }

    static final class Balance {
        long allocated, remaining;
        boolean invalidAmount, missingCategory, missingAmount, overflow;

        boolean ready() {
            return !invalidAmount && !missingCategory && !missingAmount && !overflow && remaining == 0;
        }
    }

    final long total;
    final List<Part> parts = new ArrayList<>();

    ExpenseSplitDraft(long total) { this.total = total; }

    Balance balance() {
        Balance result = new Balance();
        result.missingAmount = parts.isEmpty();
        for (Part part : parts) {
            result.missingCategory |= part.categoryId == null || part.categoryId.isEmpty();
            long value = parseAmount(part.amount);
            result.invalidAmount |= value < 0;
            result.missingAmount |= value == 0;
            if (value > 0) {
                if (Long.MAX_VALUE - result.allocated < value) result.overflow = true;
                else result.allocated += value;
            }
        }
        result.remaining = total - result.allocated;
        return result;
    }

    boolean divideEqually() {
        if (parts.isEmpty() || total < parts.size()) return false;
        long base = total / parts.size(), extra = total % parts.size();
        for (int i = 0; i < parts.size(); i++) parts.get(i).amount = String.valueOf(base + (i < extra ? 1 : 0));
        return true;
    }

    long remainingFor(Part target) {
        long remaining = total;
        for (Part part : parts) {
            if (part == target) continue;
            long value = parseAmount(part.amount);
            if (value < 0 || value > remaining) return -1;
            remaining -= value;
        }
        return remaining;
    }

    static long parseAmount(String input) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (Character.isWhitespace(ch) || Character.isSpaceChar(ch) || ch == ',' || ch == '٬'
                    || ch == '\u200e' || ch == '\u200f' || ch == '\u2066' || ch == '\u2069') continue;
            int digit = Character.digit(ch, 10);
            if (digit < 0) return -1;
            digits.append(digit);
        }
        if (digits.length() == 0) return input.trim().isEmpty() ? 0 : -1;
        try { return Long.parseLong(digits.toString()); }
        catch (NumberFormatException ignored) { return -1; }
    }
}
