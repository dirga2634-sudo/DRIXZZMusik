package com.webtools.optimizer.util;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsManager {

    private PrefsManager() {}

    private static final String PREFS_NAME = "game_booster_prefs";
    private static final String KEY_LAST_BOOSTED = "last_boosted_package";

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void saveLastBoosted(Context context, String packageName) {
        prefs(context).edit().putString(KEY_LAST_BOOSTED, packageName).apply();
    }

    public static String getLastBoosted(Context context) {
        return prefs(context).getString(KEY_LAST_BOOSTED, null);
    }
}
