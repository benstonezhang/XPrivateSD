package cn.benstone.android.xposed.xprivatesd;

//import android.content.Context;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
//import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

class Common {

    static final String[] MTP_APPS = {
            "com.android.mtp",
            "com.android.MtpApplication",
            "com.samsung.android.MtpApplication"
    };
    static final List mtp_apps = Arrays.asList(MTP_APPS);

    static final String APP_NAME = "XPrivateSD";
    static final String INTERNAL_SDCARD_PATH = "internal_sdcard_path";
    static final String PER_APP_PATH = "per_app_path";
    static final String INCLUDE_SYSTEM_APPS = "include_system_apps";
    static final String EXCLUDE_MTP = "exclude_mtp";
    static final String ENABLED_APPS = "enabled_apps";
    static final String EXCLUDE_PATH = "exclude_path";
    static final String NO_MEDIA_SCAN = "no_media_scan";
    static final String LOG_DEBUG = "log_debug";

    static final String DEFAULT_PER_APP_PATH = "AppSD";
    static final String EMPTY_PATH = "";
    static final String FILE_NOMEDIA = ".nomedia";
    static final String FILE_DUMMY = ".dummy";

    static final String WRAP_STRING = "\n";

    private Common() {
    }

    static String getInternalStoragePath() {
        try {
            File internalSdPath = Environment.getExternalStorageDirectory();
            return internalSdPath.getCanonicalPath();
        } catch (IOException e) {
            // do nothing
        }
        return null;
    }

//    static String getExternalStoragePath(Context context) {
//        String externalSd = null;
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            File[] dirs = context.getExternalMediaDirs();
//            for (File dir : dirs) {
//                if (dir == null || !dir.exists()) {
//                    continue;
//                }
//                if (Environment.isExternalStorageRemovable(dir)) {
//                    try {
//                        String absolutePath = dir.getCanonicalPath();
//                        int end = absolutePath.indexOf("/Android/");
//                        externalSd = absolutePath.substring(0, end);
//                    } catch (IOException e) {
//                        // do nothing
//                    }
//                }
//            }
//        } else {
//            String externalStorage = System.getenv("SECONDARY_STORAGE");
//            if (externalStorage != null && !externalStorage.isEmpty()) {
//                externalSd = externalStorage.split(":")[0];
//            }
//        }
//
//        return externalSd;
//    }

    static boolean isAppHookAllow(SharedPreferences prefs, String appName, int appFlags) {
        return ((appFlags & ApplicationInfo.FLAG_SYSTEM) == 0) ||
                ((prefs.getBoolean(INCLUDE_SYSTEM_APPS, false)) &&
                        (!prefs.getBoolean(EXCLUDE_MTP, true) ||
                                !mtp_apps.contains(appName)));
    }
}
