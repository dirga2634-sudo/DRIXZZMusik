package com.drizzx.musik;

import android.app.Application;

public class App extends Application {
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        MusicManager.getInstance().init(this);
    }

    public static App getInstance() { return instance; }
}
