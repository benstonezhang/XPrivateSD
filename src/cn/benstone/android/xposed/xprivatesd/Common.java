package cn.benstone.android.xposed.xprivatesd;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class Common {

    public static final String[] MTP_APPS = {
            "com.android.mtp",
            "com.android.MtpApplication",
            "com.samsung.android.MtpApplication"
    };

    public static final String APP_NAME = "XPrivateSD";
    //public static final String APP_SETTINGS = "app_settings";
    public static final String INTERNAL_SDCARD_PATH = "internal_sdcard_path";
    public static final String PER_APP_PATH = "per_app_path";
    public static final String INCLUDE_SYSTEM_APPS = "include_system_apps";
    public static final String ENABLED_APPS = "enabled_apps";
    public static final String EXCLUDE_PATH = "exclude_path";

    public static final String DEFAULT_PER_APP_PATH = "AppSD";
    public static final String EMPTY_PATH = "";

    public static final String WRAP_STRING = "\n";
    public static final String PARENT_DIR = "..";

    private Common() {
    }

    public static String getInternalStoragePath() {
        try {
            File internalSdPath = Environment.getExternalStorageDirectory();
            return internalSdPath.getCanonicalPath();
        } catch (IOException e) {
            // do nothing
        }
        return null;
    }

    public static String getExternalStoragePath(Context context) {
        String externalSd = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            File[] dirs = context.getExternalMediaDirs();
            for (File dir : dirs) {
                if (dir == null || !dir.exists()) {
                    continue;
                }
                if (Environment.isExternalStorageRemovable(dir)) {
                    try {
                        String absolutePath = dir.getCanonicalPath();
                        int end = absolutePath.indexOf("/Android/");
                        externalSd = absolutePath.substring(0, end);
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }
        } else {
            String externalStorage = System.getenv("SECONDARY_STORAGE");
            if (externalStorage != null && !externalStorage.isEmpty()) {
                externalSd = externalStorage.split(":")[0];
            }
        }

        return externalSd;
    }

//    public static String appendFileSeparator(String path) {
//        if (!path.endsWith(File.separator)) {
//            path += File.separator;
//        }
//        return path;
//    }
//
//    public static String appendFilesSeparator(String path) {
//        String[] paths = path.split("\n");
//        for (int i=0; i<paths.length; i++) {
//            paths[i] = appendFileSeparator(paths[i]);
//        }
//        return TextUtils.join("\n", paths);
//    }

    public static boolean isAllowedApp(SharedPreferences prefs, String packageName, int appFlags) {
        if ((appFlags & ApplicationInfo.FLAG_SYSTEM) != 0) {
            boolean includeSystemApps = prefs.getBoolean(INCLUDE_SYSTEM_APPS, false);
            if (!includeSystemApps) {
                return false;
            }
        }
        if (Arrays.asList(MTP_APPS).contains(packageName)) {
            return false;
        }
        return true;
    }
}
