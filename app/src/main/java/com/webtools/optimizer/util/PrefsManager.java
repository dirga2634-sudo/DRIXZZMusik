package com.webtools.optimizer.util;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsManager {

    private PrefsManager() {}

    private static final String PREFS_NAME = "web_optimizer_prefs";
    private static final String KEY_LAST_URL = "last_url";

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void saveLastUrl(Context context, String url) {
        prefs(context).edit().putString(KEY_LAST_URL, url).apply();
    }

    public static String getLastUrl(Context context, String defaultUrl) {
        return prefs(context).getString(KEY_LAST_URL, defaultUrl);
    }
}
