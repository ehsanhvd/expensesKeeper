package com.codex.expensekeeper;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;

/** Shared foundations for the main app and its dedicated screens. */
final class AppAppearance {
    final boolean dark;
    final int background, surface, surfaceAlt, text, muted, accent, accent2, accent3, outline;

    AppAppearance(Context context, String theme) {
        dark = "dark".equals(theme) || ("system".equals(theme)
                && (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES);
        background = dark ? Color.rgb(18, 19, 24) : Color.rgb(250, 248, 255);
        surface = dark ? Color.rgb(30, 31, 36) : Color.WHITE;
        surfaceAlt = dark ? Color.rgb(38, 40, 48) : Color.rgb(239, 244, 255);
        text = dark ? Color.rgb(232, 234, 240) : Color.rgb(31, 31, 31);
        muted = dark ? Color.rgb(188, 190, 199) : Color.rgb(95, 99, 104);
        accent = dark ? Color.rgb(168, 199, 250) : Color.rgb(26, 115, 232);
        accent2 = dark ? Color.rgb(244, 176, 203) : Color.rgb(217, 48, 105);
        accent3 = dark ? Color.rgb(250, 210, 118) : Color.rgb(251, 188, 4);
        outline = dark ? Color.rgb(68, 71, 78) : Color.rgb(218, 220, 224);
    }

    static Typeface font(Context context, boolean persian) {
        if (persian && Build.VERSION.SDK_INT >= 26) {
            try {
                return context.getResources().getFont(R.font.vazirmatn);
            } catch (Resources.NotFoundException ignored) {
                // Use the same fallback everywhere if the bundled font cannot be loaded.
            }
        }
        return Typeface.create("sans-serif", Typeface.NORMAL);
    }
}
