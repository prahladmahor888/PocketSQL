package com.mysql.pocketsql;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public class SettingsManager {

    private static final String PREFS_NAME = "pocketsql_settings";

    // ── Keys ──────────────────────────────────────────────────────────────────
    public static final String KEY_THEME         = "theme";
    public static final String KEY_FONT_SIZE_IDX = "font_size_idx";
    public static final String KEY_LINE_SPACING  = "line_spacing";
    public static final String KEY_PROMPT_STYLE  = "prompt_style";
    public static final String KEY_AUTO_SCROLL   = "auto_scroll";

    // ── Theme constants ────────────────────────────────────────────────────────
    public static final int THEME_CLASSIC = 0;  // Black / White
    public static final int THEME_MATRIX  = 1;  // Black / Green
    public static final int THEME_OCEAN   = 2;  // Dark-Blue / Cyan
    public static final int THEME_DRACULA = 3;  // #282A36 / #F8F8F2

    // ── Font sizes (sp) ────────────────────────────────────────────────────────
    public static final int[]    FONT_SIZES       = {12, 14, 16, 18};
    public static final String[] FONT_SIZE_LABELS = {"Small (12sp)", "Medium (14sp)", "Large (16sp)", "X-Large (18sp)"};

    // ── Line spacing extras (dp) ───────────────────────────────────────────────
    public static final int LINE_SPACING_COMPACT  = 0;
    public static final int LINE_SPACING_NORMAL   = 1;
    public static final int LINE_SPACING_RELAXED  = 2;
    public static final float[] LINE_SPACING_VALUES = {0f, 4f, 8f};

    // ── Prompt styles ─────────────────────────────────────────────────────────
    public static final String[] PROMPT_STYLES = {"mysql> ", "pocketsql> ", "$ ", "> "};

    // ─────────────────────────────────────────────────────────────────────────

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    public int getTheme() {
        return prefs.getInt(KEY_THEME, THEME_CLASSIC);
    }

    public void setTheme(int theme) {
        prefs.edit().putInt(KEY_THEME, theme).apply();
    }

    /** Terminal background color for the current theme. */
    public int getBackgroundColor() {
        switch (getTheme()) {
            case THEME_MATRIX:  return Color.parseColor("#000000");
            case THEME_OCEAN:   return Color.parseColor("#0D1B2A");
            case THEME_DRACULA: return Color.parseColor("#282A36");
            default:            return Color.parseColor("#000000");
        }
    }

    /** Primary text color for the current theme. */
    public int getTextColor() {
        switch (getTheme()) {
            case THEME_MATRIX:  return Color.parseColor("#00FF41");
            case THEME_OCEAN:   return Color.parseColor("#8ECAE6");
            case THEME_DRACULA: return Color.parseColor("#F8F8F2");
            default:            return Color.parseColor("#FFFFFF");
        }
    }

    /** Prompt / accent color for the current theme. */
    public int getPromptColor() {
        switch (getTheme()) {
            case THEME_MATRIX:  return Color.parseColor("#00FF41");
            case THEME_OCEAN:   return Color.parseColor("#219EBC");
            case THEME_DRACULA: return Color.parseColor("#BD93F9");
            default:            return Color.parseColor("#FFFFFF");
        }
    }

    /** Error text color for the current theme. */
    public int getErrorColor() {
        switch (getTheme()) {
            case THEME_MATRIX:  return Color.parseColor("#FF3C00");
            case THEME_OCEAN:   return Color.parseColor("#FF6B6B");
            case THEME_DRACULA: return Color.parseColor("#FF5555");
            default:            return Color.parseColor("#FF4444");
        }
    }

    /** Success / info text color for the current theme. */
    public int getSuccessColor() {
        switch (getTheme()) {
            case THEME_MATRIX:  return Color.parseColor("#00FF41");
            case THEME_OCEAN:   return Color.parseColor("#8ECAE6");
            case THEME_DRACULA: return Color.parseColor("#50FA7B");
            default:            return Color.parseColor("#00E5FF");
        }
    }

    // ── Font size ─────────────────────────────────────────────────────────────

    public int getFontSizeIndex() {
        return prefs.getInt(KEY_FONT_SIZE_IDX, 1); // default: 14sp
    }

    public void setFontSizeIndex(int index) {
        prefs.edit().putInt(KEY_FONT_SIZE_IDX, index).apply();
    }

    public int getFontSizeSp() {
        int idx = getFontSizeIndex();
        if (idx < 0 || idx >= FONT_SIZES.length) idx = 1;
        return FONT_SIZES[idx];
    }

    // ── Line spacing ──────────────────────────────────────────────────────────

    public int getLineSpacing() {
        return prefs.getInt(KEY_LINE_SPACING, LINE_SPACING_NORMAL);
    }

    public void setLineSpacing(int spacing) {
        prefs.edit().putInt(KEY_LINE_SPACING, spacing).apply();
    }

    public float getLineSpacingExtra() {
        int idx = getLineSpacing();
        if (idx < 0 || idx >= LINE_SPACING_VALUES.length) idx = LINE_SPACING_NORMAL;
        return LINE_SPACING_VALUES[idx];
    }

    // ── Prompt style ──────────────────────────────────────────────────────────

    public int getPromptStyleIndex() {
        return prefs.getInt(KEY_PROMPT_STYLE, 0); // default: mysql>
    }

    public void setPromptStyleIndex(int index) {
        prefs.edit().putInt(KEY_PROMPT_STYLE, index).apply();
    }

    public String getPromptString() {
        int idx = getPromptStyleIndex();
        if (idx < 0 || idx >= PROMPT_STYLES.length) idx = 0;
        return PROMPT_STYLES[idx];
    }

    // ── Auto-scroll ───────────────────────────────────────────────────────────

    public boolean isAutoScroll() {
        return prefs.getBoolean(KEY_AUTO_SCROLL, true);
    }

    public void setAutoScroll(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_SCROLL, enabled).apply();
    }

    // ── Auto-login credentials ──────────────────────────────────────────────────

    public String getLastUsername() {
        return prefs.getString("last_username", "root");
    }

    public void setLastUsername(String username) {
        prefs.edit().putString("last_username", username).apply();
    }

    public String getLastHost() {
        return prefs.getString("last_host", "localhost");
    }

    public void setLastHost(String host) {
        prefs.edit().putString("last_host", host).apply();
    }

    public String getLastPassword() {
        return prefs.getString("last_password", "");
    }

    public void setLastPassword(String password) {
        prefs.edit().putString("last_password", password).apply();
    }

    public boolean isAutoLogin() {
        return prefs.getBoolean("auto_login", true);
    }

    public void setAutoLogin(boolean enabled) {
        prefs.edit().putBoolean("auto_login", enabled).apply();
    }
}
