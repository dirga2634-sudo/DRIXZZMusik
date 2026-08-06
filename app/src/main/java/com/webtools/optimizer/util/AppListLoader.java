package com.webtools.optimizer.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.webtools.optimizer.model.AppInfo;

import java.util.ArrayList;
import java.util.List;

public class AppListLoader {

    private AppListLoader() {}

    /**
     * Mengambil daftar aplikasi yang punya launcher icon (bisa dibuka dari home screen),
     * pakai <queries> resmi -- bukan QUERY_ALL_PACKAGES. Aplikasi berkategori "game"
     * (ApplicationInfo.CATEGORY_GAME) diprioritaskan tampil di atas.
     */
    public static List<AppInfo> loadLaunchableApps(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(launcherIntent, 0);
        List<AppInfo> apps = new ArrayList<>();
        String selfPackage = context.getPackageName();

        for (ResolveInfo info : resolveInfos) {
            String packageName = info.activityInfo.packageName;
            if (packageName.equals(selfPackage)) continue;
            String label = info.loadLabel(pm).toString();
            boolean isGame = info.activityInfo.applicationInfo.category == ApplicationInfo.CATEGORY_GAME;
            apps.add(new AppInfo(label, packageName, info.loadIcon(pm), isGame));
        }

        apps.sort((a, b) -> {
            if (a.isGame != b.isGame) return a.isGame ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        return apps;
    }
}
