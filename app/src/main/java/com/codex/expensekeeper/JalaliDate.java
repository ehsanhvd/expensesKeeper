package com.codex.expensekeeper;

import java.util.Calendar;

public class JalaliDate {
    public static final String[] EN_MONTHS = {
            "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
            "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand"
    };
    public static final String[] FA_MONTHS = {
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    };

    public final int year;
    public final int month;
    public final int day;

    public JalaliDate(int year, int month, int day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }

    public static JalaliDate today() {
        return fromMillis(System.currentTimeMillis());
    }

    public static JalaliDate fromMillis(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return fromGregorian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    public static JalaliDate fromGregorian(int gy, int gm, int gd) {
        int[] gdm = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        int gy2 = gm > 2 ? gy + 1 : gy;
        int days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100)
                + ((gy2 + 399) / 400) + gd + gdm[gm - 1];
        int jy = -1595 + (33 * (days / 12053));
        days %= 12053;
        jy += 4 * (days / 1461);
        days %= 1461;
        if (days > 365) {
            jy += (days - 1) / 365;
            days = (days - 1) % 365;
        }
        int jm;
        int jd;
        if (days < 186) {
            jm = 1 + days / 31;
            jd = 1 + days % 31;
        } else {
            jm = 7 + (days - 186) / 30;
            jd = 1 + (days - 186) % 30;
        }
        return new JalaliDate(jy, jm, jd);
    }

    public static int[] toGregorian(int jy, int jm, int jd) {
        jy += 1595;
        int days = -355668 + (365 * jy) + ((jy / 33) * 8) + (((jy % 33) + 3) / 4) + jd;
        if (jm < 7) {
            days += (jm - 1) * 31;
        } else {
            days += ((jm - 7) * 30) + 186;
        }
        int gy = 400 * (days / 146097);
        days %= 146097;
        if (days > 36524) {
            gy += 100 * (--days / 36524);
            days %= 36524;
            if (days >= 365) days++;
        }
        gy += 4 * (days / 1461);
        days %= 1461;
        if (days > 365) {
            gy += (days - 1) / 365;
            days = (days - 1) % 365;
        }
        int gd = days + 1;
        int[] salA = {0, 31, isLeapGregorian(gy) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int gm = 0;
        while (gm < 13 && gd > salA[gm]) {
            gd -= salA[gm];
            gm++;
        }
        return new int[]{gy, gm, gd};
    }

    public static long toMillisStartOfDay(int jy, int jm, int jd) {
        int[] g = toGregorian(jy, jm, jd);
        Calendar c = Calendar.getInstance();
        c.set(g[0], g[1] - 1, g[2], 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static Period periodFor(long millis, int startDay) {
        JalaliDate now = fromMillis(millis);
        int startYear = now.year;
        int startMonth = now.month;
        if (now.day < startDay) {
            startMonth--;
            if (startMonth == 0) {
                startMonth = 12;
                startYear--;
            }
        }
        int safeDay = Math.min(startDay, daysInMonth(startYear, startMonth));
        long start = toMillisStartOfDay(startYear, startMonth, safeDay);
        int endYear = startYear;
        int endMonth = startMonth + 1;
        if (endMonth == 13) {
            endMonth = 1;
            endYear++;
        }
        long end = toMillisStartOfDay(endYear, endMonth, Math.min(startDay, daysInMonth(endYear, endMonth)));
        return new Period(start, end, startYear, startMonth, startDay);
    }

    public static String periodLabel(boolean fa, int startYear, int startMonth, int startDay) {
        String[] months = fa ? FA_MONTHS : EN_MONTHS;
        int days = daysInMonth(startYear, startMonth);
        int next = nextMonth(startMonth);
        if (startDay <= days / 3) return months[startMonth - 1];
        if (startDay > (days * 2) / 3) return months[next - 1];
        return months[startMonth - 1] + "-" + months[next - 1];
    }

    public static int periodLabelYear(int startYear, int startMonth, int startDay) {
        int days = daysInMonth(startYear, startMonth);
        if (startDay > (days * 2) / 3 && startMonth == 12) return startYear + 1;
        return startYear;
    }

    public static int nextMonth(int month) {
        return month == 12 ? 1 : month + 1;
    }

    public static int daysInMonth(int year, int month) {
        if (month <= 6) return 31;
        if (month <= 11) return 30;
        return isLeapJalali(year) ? 30 : 29;
    }

    public static boolean isLeapJalali(int jy) {
        int mod = ((jy - 474) % 2820 + 2820) % 2820 + 474;
        return (((mod + 38) * 682) % 2816) < 682;
    }

    private static boolean isLeapGregorian(int gy) {
        return (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0;
    }

    public static class Period {
        public final long start;
        public final long end;
        public final int startYear;
        public final int startMonth;
        public final int startDay;

        Period(long start, long end, int startYear, int startMonth, int startDay) {
            this.start = start;
            this.end = end;
            this.startYear = startYear;
            this.startMonth = startMonth;
            this.startDay = startDay;
        }

        public String label(boolean fa) {
            return periodLabel(fa, startYear, startMonth, startDay);
        }
    }
}
