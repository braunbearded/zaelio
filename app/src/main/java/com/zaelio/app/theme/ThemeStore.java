package com.zaelio.app.theme;

import android.content.res.Configuration;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import com.zaelio.app.I18n;

public final class ThemeStore {
    private static final String PREFS_NAME = "app_settings";
    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_ACCENT_INDEX = "accent_index";
    private static final String KEY_FONT_SCALE_INDEX = "font_scale_index";
    private static final String KEY_FIELD_SIZE_INDEX = "field_size_index";
    private static final String KEY_SESSION_FIELDS_COLLAPSED = "session_fields_collapsed";
    private static final String KEY_LANGUAGE = "language";

    private static final int[] ACCENT_COLORS = {
            0xff2563eb,
            0xff0f766e,
            0xff15803d,
            0xffea580c,
            0xffdc2626,
            0xff7c3aed,
            0xffdb2777,
            0xff4f46e5
    };

    private static final String[] ACCENT_NAMES = {
            "Blau", "Teal", "Grün", "Orange", "Rot", "Violett", "Pink", "Indigo"
    };

    private static final float[] FONT_SCALES = {
            0.90f,
            1.00f,
            1.12f,
            1.25f
    };

    private static final String[] FONT_SCALE_NAMES = {
            "Klein",
            "Standard",
            "Groß",
            "Sehr groß"
    };

    private static final String[] FIELD_SIZE_KEYS = {
            "compact",
            "standard",
            "large"
    };

    private static final String[] FIELD_SIZE_NAMES = {
            "Kompakt",
            "Standard",
            "Groß"
    };

    private final Context appContext;
    private final SharedPreferences prefs;

    public ThemeStore(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean darkMode() {
        return themeMode() == THEME_DARK || (themeMode() == THEME_SYSTEM && isSystemDark());
    }

    public int themeMode() {
        int value = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM);
        if (value < THEME_SYSTEM || value > THEME_DARK) {
            return THEME_SYSTEM;
        }
        return value;
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    public String language() {
        String value = prefs.getString(KEY_LANGUAGE, I18n.SYSTEM);
        return I18n.GERMAN.equals(value) || I18n.ENGLISH.equals(value) || I18n.SPANISH.equals(value) ? value : I18n.SYSTEM;
    }

    public void setLanguage(String language) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
    }

    public String resolvedLanguage() {
        return I18n.resolveLanguage(language());
    }

    public boolean isSystemDark() {
        int nightMode = appContext.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public int accentIndex() {
        int value = prefs.getInt(KEY_ACCENT_INDEX, 0);
        return value < 0 || value >= ACCENT_COLORS.length ? 0 : value;
    }

    public void setAccentIndex(int index) {
        prefs.edit().putInt(KEY_ACCENT_INDEX, index).apply();
    }

    public int accentColor() {
        return ACCENT_COLORS[accentIndex()];
    }

    public int accentColor(int index) {
        if (index < 0 || index >= ACCENT_COLORS.length) {
            return ACCENT_COLORS[0];
        }
        return ACCENT_COLORS[index];
    }

    public String accentName(int index) {
        return ACCENT_NAMES[index];
    }

    public int accentCount() {
        return ACCENT_COLORS.length;
    }

    public int fontScaleIndex() {
        int value = prefs.getInt(KEY_FONT_SCALE_INDEX, 1);
        return value < 0 || value >= FONT_SCALES.length ? 1 : value;
    }

    public void setFontScaleIndex(int index) {
        prefs.edit().putInt(KEY_FONT_SCALE_INDEX, index).apply();
    }

    public float fontScale() {
        return FONT_SCALES[fontScaleIndex()];
    }

    public String fontScaleName(int index) {
        return FONT_SCALE_NAMES[index];
    }

    public int fontScaleCount() {
        return FONT_SCALES.length;
    }

    public int fieldSizeIndex() {
        int value = prefs.getInt(KEY_FIELD_SIZE_INDEX, 1);
        return value < 0 || value >= FIELD_SIZE_KEYS.length ? 1 : value;
    }

    public void setFieldSizeIndex(int index) {
        prefs.edit().putInt(KEY_FIELD_SIZE_INDEX, index).apply();
    }

    public String fieldSize() {
        return FIELD_SIZE_KEYS[fieldSizeIndex()];
    }

    public String fieldSizeName(int index) {
        return FIELD_SIZE_NAMES[index];
    }

    public int fieldSizeCount() {
        return FIELD_SIZE_KEYS.length;
    }

    public boolean sessionFieldsCollapsed() {
        return prefs.getBoolean(KEY_SESSION_FIELDS_COLLAPSED, false);
    }

    public void setSessionFieldsCollapsed(boolean collapsed) {
        prefs.edit().putBoolean(KEY_SESSION_FIELDS_COLLAPSED, collapsed).apply();
    }

    public int accentSoftColor() {
        return withAlpha(accentColor(), darkMode() ? 0x33 : 0x18);
    }

    public int accentSoftColor(int index) {
        return withAlpha(accentColor(index), darkMode() ? 0x33 : 0x18);
    }

    public int backgroundColor() {
        return darkMode() ? 0xff0b1220 : 0xfff8fafc;
    }

    public int surfaceColor() {
        return darkMode() ? 0xff111827 : 0xffffffff;
    }

    public int surfaceAltColor() {
        return darkMode() ? 0xff1f2937 : 0xfff9fafb;
    }

    public int primaryTextColor() {
        return darkMode() ? 0xfff8fafc : 0xff101828;
    }

    public int secondaryTextColor() {
        return darkMode() ? 0xffcbd5e1 : 0xff475467;
    }

    public int mutedTextColor() {
        return darkMode() ? 0xff94a3b8 : 0xff667085;
    }

    public int borderColor() {
        return darkMode() ? 0xff334155 : 0xffe4e7ec;
    }

    public int cautionFillColor() {
        return darkMode() ? 0xff3f1d1d : 0xfffff5f5;
    }

    public int cautionStrokeColor() {
        return darkMode() ? 0xff7f1d1d : 0xfffecdca;
    }

    public int navigationBarColor() {
        return darkMode() ? 0xff0f172a : 0xffffffff;
    }

    public int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
