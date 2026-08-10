package com.webtools.optimizer.util;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsManager {

    private PrefsManager() {}

    private static final String PREFS_NAME = "game_booster_prefs";
    private static final String KEY_LAST_BOOSTED = "last_boosted_package";
    private static final String KEY_CROSSHAIR_COLOR = "crosshair_color";
    private static final String KEY_CROSSHAIR_SHAPE = "crosshair_shape";
    private static final String KEY_CROSSHAIR_SIZE = "crosshair_size";

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void saveLastBoosted(Context context, String packageName) {
        prefs(context).edit().putString(KEY_LAST_BOOSTED, packageName).apply();
    }

    public static String getLastBoosted(Context context) {
        return prefs(context).getString(KEY_LAST_BOOSTED, null);
    }

    public static void saveCrosshairStyle(Context context, int color, int shape, float size) {
        prefs(context).edit()
                .putInt(KEY_CROSSHAIR_COLOR, color)
                .putInt(KEY_CROSSHAIR_SHAPE, shape)
                .putFloat(KEY_CROSSHAIR_SIZE, size)
                .apply();
    }

    public static int getCrosshairColor(Context context) {
        return prefs(context).getInt(KEY_CROSSHAIR_COLOR, 0xFFFFFFFF);
    }

    public static int getCrosshairShape(Context context) {
        return prefs(context).getInt(KEY_CROSSHAIR_SHAPE, 0);
    }

    public static float getCrosshairSize(Context context) {
        return prefs(context).getFloat(KEY_CROSSHAIR_SIZE, 44f);
    }
}
