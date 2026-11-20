package com.example.chesstactics.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    private static final String PREF_NAME = "chess_tactics_prefs";
    private static final String KEY_THEME = "selected_theme";
    private static final String KEY_SOUND = "sound_enabled";
    private static final String KEY_LANG = "app_language";

    private final SharedPreferences prefs;

    public PreferenceManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public String getThemeId() {
        return prefs.getString(KEY_THEME, "tournament_green");
    }

    public void setThemeId(String themeId) {
        prefs.edit().putString(KEY_THEME, themeId).apply();
    }

    public boolean isSoundEnabled() {
        return prefs.getBoolean(KEY_SOUND, true);
    }

    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply();
    }

    public String getLanguage() {
        return prefs.getString(KEY_LANG, "vi");
    }

    public void setLanguage(String lang) {
        prefs.edit().putString(KEY_LANG, lang).apply();
    }
}