package com.webtools.optimizer.model;

import android.graphics.drawable.Drawable;

public class AppInfo {
    public final String label;
    public final String packageName;
    public final Drawable icon;
    public final boolean isGame;

    public AppInfo(String label, String packageName, Drawable icon, boolean isGame) {
        this.label = label;
        this.packageName = packageName;
        this.icon = icon;
        this.isGame = isGame;
    }
}
