package com.mysql.pocketsql;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
public class SettingsManager {

    private static final String PREFS_NAME = "pocketsql_secure_settings";

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

    private static volatile SharedPreferences cachedPrefs;
    private SharedPreferences prefs;

    public SettingsManager(Context context) {
        if (cachedPrefs != null) {
            prefs = cachedPrefs;
            return;
        }
        synchronized (SettingsManager.class) {
            if (cachedPrefs == null) {
                try {
                    String masterKeyAlias = androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC);
                    cachedPrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                        PREFS_NAME,
                        masterKeyAlias,
                        context.getApplicationContext(),
                        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    );
                } catch (Exception e) {
                    cachedPrefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                }
            }
            prefs = cachedPrefs;
        }
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

    // ── Font family ────────────────────────────────────────────────────────────

    public static final String KEY_FONT_FAMILY = "font_family";

    public static class FontOption {
        public final String label;
        public final String value;
        public FontOption(String label, String value) {
            this.label = label;
            this.value = value;
        }
        @Override
        public String toString() {
            return label;
        }
    }

    public String getFontFamily() {
        return prefs.getString(KEY_FONT_FAMILY, "jetbrains_mono");
    }

    public void setFontFamily(String family) {
        prefs.edit().putString(KEY_FONT_FAMILY, family).apply();
    }

    private static final java.util.Map<String, android.graphics.Typeface> typefaceCache = new java.util.HashMap<>();

    public static boolean isMonospaceFont(android.graphics.Typeface tf) {
        if (tf == null) return false;
        android.text.TextPaint paint = new android.text.TextPaint();
        paint.setTypeface(tf);
        paint.setTextSize(32f);
        float w1 = paint.measureText("i");
        float w2 = paint.measureText("W");
        float w3 = paint.measureText("m");
        return Math.abs(w1 - w2) < 0.2f && Math.abs(w2 - w3) < 0.2f;
    }

    public static void clearTypefaceCache() {
        typefaceCache.clear();
    }

    public android.graphics.Typeface getTypeface(Context context) {
        String family = getFontFamily();
        if (family == null || family.isEmpty()) family = "jetbrains_mono";

        if (typefaceCache.containsKey(family)) {
            android.graphics.Typeface cached = typefaceCache.get(family);
            if (cached != null) return cached;
        }

        android.graphics.Typeface tf = loadTypefaceUncached(context, family);
        if (tf != null) {
            typefaceCache.put(family, tf);
            return tf;
        }
        return android.graphics.Typeface.MONOSPACE;
    }

    public void applyFontToViewTree(android.view.View view) {
        if (view == null) return;
        android.graphics.Typeface tf = getTypeface(view.getContext());
        applyFontToViewTree(view, tf);
    }

    public static void applyFontToViewTree(android.view.View view, android.graphics.Typeface tf) {
        if (view == null || tf == null) return;

        if (view instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) view;
            android.graphics.Typeface currentTf = tv.getTypeface();
            int style = android.graphics.Typeface.NORMAL;
            if (currentTf != null) {
                style = currentTf.getStyle();
            }
            try {
                tv.setTypeface(android.graphics.Typeface.create(tf, style));
            } catch (Exception e) {
                tv.setTypeface(tf, style);
            }
        } else if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyFontToViewTree(vg.getChildAt(i), tf);
            }
        }
    }

    private android.graphics.Typeface loadTypefaceUncached(Context context, String family) {
        if ("monospace".equalsIgnoreCase(family)) {
            return android.graphics.Typeface.MONOSPACE;
        } else if ("sans_serif".equalsIgnoreCase(family)) {
            return android.graphics.Typeface.SANS_SERIF;
        } else if ("serif".equalsIgnoreCase(family)) {
            return android.graphics.Typeface.SERIF;
        } else if ("aboreto_regular".equalsIgnoreCase(family)) {
            try {
                android.graphics.Typeface tf = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.aboreto_regular);
                if (tf != null) return tf;
            } catch (Exception ignored) {}
        } else if ("barlowsemicondensed_regular".equalsIgnoreCase(family)) {
            try {
                android.graphics.Typeface tf = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.barlowsemicondensed_regular);
                if (tf != null) return tf;
            } catch (Exception ignored) {}
        } else if ("basic_regular".equalsIgnoreCase(family)) {
            try {
                android.graphics.Typeface tf = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.basic_regular);
                if (tf != null) return tf;
            } catch (Exception ignored) {}
        } else if ("dancingscript_regular".equalsIgnoreCase(family)) {
            try {
                android.graphics.Typeface tf = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.dancingscript_regular);
                if (tf != null) return tf;
            } catch (Exception ignored) {}
        } else if ("lobstertwo_regular".equalsIgnoreCase(family)) {
            try {
                android.graphics.Typeface tf = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.lobstertwo_regular);
                if (tf != null) return tf;
            } catch (Exception ignored) {}
        } else if ("rumraisin_regular".equalsIgnoreCase(family)) {
            try {
                android.graphics.Typeface tf = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.rumraisin_regular);
                if (tf != null) return tf;
            } catch (Exception ignored) {}
        }

        // Default fallback: JetBrains Mono
        try {
            android.graphics.Typeface tf = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.jetbrains_mono);
            if (tf != null) return tf;
        } catch (Exception e) {
            // fallback
        }
        return android.graphics.Typeface.MONOSPACE;
    }

    public List<FontOption> getAvailableFontOptions(Context context) {
        List<FontOption> options = new ArrayList<>();
        options.add(new FontOption("JetBrains Mono (Default)", "jetbrains_mono"));
        options.add(new FontOption("Aboreto", "aboreto_regular"));
        options.add(new FontOption("Barlow Semi Condensed", "barlowsemicondensed_regular"));
        options.add(new FontOption("Basic", "basic_regular"));
        options.add(new FontOption("Dancing Script", "dancingscript_regular"));
        options.add(new FontOption("Lobster Two", "lobstertwo_regular"));
        options.add(new FontOption("Rum Raisin", "rumraisin_regular"));
        options.add(new FontOption("System Monospace", "monospace"));
        options.add(new FontOption("System Sans-Serif", "sans_serif"));
        options.add(new FontOption("System Serif", "serif"));
        return options;
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
        return prefs.getString("last_username", com.mysql.pocketsql.engine.SecurityHelper.getDefaultUser());
    }

    public void setLastUsername(String username) {
        prefs.edit().putString("last_username", username).apply();
    }

    public String getLastHost() {
        return prefs.getString("last_host", com.mysql.pocketsql.engine.SecurityHelper.getDefaultHost());
    }

    public void setLastHost(String host) {
        prefs.edit().putString("last_host", host).apply();
    }

    public String getLastPassword() {
        String encrypted = prefs.getString("last_password", "");
        if (encrypted.isEmpty()) return "";
        try {
            return com.mysql.pocketsql.engine.SecurityHelper.decrypt(encrypted);
        } catch (Exception e) {
            return "";
        }
    }

    public void setLastPassword(String password) {
        if (password == null || password.isEmpty()) {
            prefs.edit().putString("last_password", "").apply();
            return;
        }
        try {
            String encrypted = com.mysql.pocketsql.engine.SecurityHelper.encrypt(password);
            prefs.edit().putString("last_password", encrypted).apply();
        } catch (Exception e) {
            prefs.edit().putString("last_password", password).apply();
        }
    }

    public boolean isAutoLogin() {
        return prefs.getBoolean("auto_login", true);
    }

    public void setAutoLogin(boolean enabled) {
        prefs.edit().putBoolean("auto_login", enabled).apply();
    }

    // ── Multiple Connections ───────────────────────────────────────────────────

    public JSONArray getSavedConnections() {
        String jsonStr = prefs.getString("saved_connections", "[]");
        try {
            return new JSONArray(jsonStr);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public void saveConnection(String username, String host, String password) {
        try {
            JSONArray arr = getSavedConnections();
            // Remove if exists to update
            JSONArray newArr = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (obj.optString("username").equals(username) && obj.optString("host").equals(host)) {
                    continue; // Skip the existing one
                }
                newArr.put(obj);
            }
            
            JSONObject newConn = new JSONObject();
            newConn.put("username", username);
            newConn.put("host", host);
            if (password == null || password.isEmpty()) {
                newConn.put("password", "");
            } else {
                newConn.put("password", com.mysql.pocketsql.engine.SecurityHelper.encrypt(password));
            }
            newArr.put(newConn);
            
            prefs.edit().putString("saved_connections", newArr.toString()).apply();
        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
        }
    }

    public void removeConnection(String username, String host) {
        try {
            JSONArray arr = getSavedConnections();
            JSONArray newArr = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (obj.optString("username").equals(username) && obj.optString("host").equals(host)) {
                    continue;
                }
                newArr.put(obj);
            }
            prefs.edit().putString("saved_connections", newArr.toString()).apply();
        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
        }
    }
}
