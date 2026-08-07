package com.webtools.optimizer;

import android.app.Application;

import com.webtools.optimizer.util.CrashHandler;

public class GameBoosterApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.install(this);
    }
}
