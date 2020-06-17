package cn.benstone.android.xposed.xprivatesd;

import android.content.pm.ApplicationInfo;
import android.text.TextUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedModMain implements IXposedHookZygoteInit, IXposedHookLoadPackage {
    private static XSharedPreferences prefs;

    private static String appSdBase;
    private static int appSdBaseLength;
    private static String appSdBase2;
    private static String[] excludePaths;
    private static boolean log_debug;

    private String noMediaFile;

    private static void log(String s) {
        XposedBridge.log(Common.APP_NAME + ": " + s);
    }

//    private static void trace(String s) {
//        StringWriter sw = new StringWriter();
//        new Throwable(s).printStackTrace(new PrintWriter(sw));
//        log(sw.toString());
//    }

    @Override
    public void initZygote(StartupParam startupParam) {
        prefs = new XSharedPreferences(XposedModMain.class.getPackage().getName());
        prefs.makeWorldReadable();

        appSdBase = File.separator + prefs.getString(Common.PER_APP_PATH, Common.DEFAULT_PER_APP_PATH);
        log("app path base: " + appSdBase);
        appSdBaseLength = appSdBase.length();
        appSdBase2 = appSdBase.toLowerCase();

        final String excludePathStr = prefs.getString(Common.EXCLUDE_PATH, Common.EMPTY_PATH);
        if (!excludePathStr.isEmpty()) {
            excludePaths = excludePathStr.toLowerCase().split(Common.WRAP_STRING);
            log("exclude paths: " + TextUtils.join(File.pathSeparator, excludePaths));
        } else {
            excludePaths = null;
        }
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        prefs.reload();

        log_debug = prefs.getBoolean(Common.LOG_DEBUG, false);

        if (!isEnabledApp(lpparam)) {
            return;
        }

        final String internalSd = Common.getInternalStoragePath();
        final String appPkgPath = File.separator + lpparam.packageName.toLowerCase();
        File perAppPath = new File(internalSd + appSdBase + appPkgPath);
        if (log_debug) {
            log("app sd path is " + perAppPath.getAbsolutePath());
        }

        if (prefs.getBoolean(Common.NO_MEDIA_SCAN, true)) {
            noMediaFile = internalSd + appSdBase + appPkgPath + File.separator + Common.FILE_NOMEDIA;
        } else {
            noMediaFile = internalSd + appSdBase + appPkgPath + File.separator + Common.FILE_DUMMY;
        }

        // create missed dirs
        if (!perAppPath.exists()) {
            try {
                if (perAppPath.mkdirs() && log_debug) {
                    log("app folder created for " + lpparam.packageName);
                }
            } catch (Exception e) {
                log("create app SD folder failed for " + lpparam.packageName);
                return;
            }
        }

        XposedHelpers.findAndHookConstructor("java.io.File", lpparam.classLoader,
                String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String newPath = getPatchedPath((File) param.thisObject, internalSd, appPkgPath);
                        if (newPath != null) {
                            param.args[0] = newPath;
//                            log("invokeOriginalMethod Args: " + param.method + ", " +
//                                    param.thisObject + ", " + param.args[0]);
                            try {
                                XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                            } catch (IllegalAccessException e) {
                                log("IllegalAccessException: " + e);
                            } catch (InvocationTargetException e) {
                                log("InvocationTargetException: " + e.getTargetException());
                            }
                        }
                    }
                });

        XC_MethodHook fileHook2 = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                String newPath = getPatchedPath((File) param.thisObject, internalSd, appPkgPath);
                if (newPath != null) {
                    param.args[0] = null;
                    param.args[1] = newPath;
//                    log("invokeOriginalMethod Args: " + param.method + ", " +
//                            param.thisObject + ", " + param.args[1]);
                    try {
                        XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                    } catch (IllegalAccessException e) {
                        log("IllegalAccessException: " + e);
                    } catch (InvocationTargetException e) {
                        log("InvocationTargetException: " + e.getTargetException());
                    }
                }
            }
        };
        XposedHelpers.findAndHookConstructor("java.io.File", lpparam.classLoader,
                String.class, String.class, fileHook2);
        XposedHelpers.findAndHookConstructor("java.io.File", lpparam.classLoader,
                File.class, String.class, fileHook2);

        XposedHelpers.findAndHookConstructor("java.io.File", lpparam.classLoader,
                URI.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String newPath = getPatchedPath((File) param.thisObject, internalSd, appPkgPath);
                        if (newPath != null) {
                            try {
                                param.args[0] = new URI("file://" + newPath);
//                                log("invokeOriginalMethod Args: " + param.method + ", " +
//                                        param.thisObject + ", " + param.args[0]);
                                XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                            } catch (URISyntaxException e) {
                                log("URISyntaxException: " + e);
                            } catch (IllegalAccessException e) {
                                log("IllegalAccessException: " + e);
                            } catch (InvocationTargetException e) {
                                log("InvocationTargetException: " + e.getTargetException());
                            }
                        }
                    }
                });
    }

    private boolean isEnabledApp(LoadPackageParam lpparam) {
        if (log_debug) {
            log("checking package " + lpparam.packageName);
        }

        int appFlags = lpparam.appInfo == null ? ApplicationInfo.FLAG_SYSTEM : lpparam.appInfo.flags;
        if (!Common.isAppHookAllow(prefs, lpparam.packageName, appFlags)) {
            return false;
        }

        Set<String> enabledApps = prefs.getStringSet(Common.ENABLED_APPS, new HashSet<String>());
        return (!enabledApps.isEmpty()) && enabledApps.contains(lpparam.packageName);
    }

    private static boolean isExcludePath(String path, String[] excludeList) {
        for (String excludePath : excludeList) {
//            log("check exclude path " + excludePath);
            if (path.startsWith(excludePath)) {
                if (log_debug) {
                    log("exclude path matched, bypass: " + excludePath + ", " + path);
                }
                return true;
            }
        }
        return false;
    }

    private static String getRealPath(File f) {
        String path;
        try {
            path = f.getCanonicalPath();
        } catch (IOException e) {
            path = f.getAbsolutePath();
        }
        return path;
    }

    private String getPatchedPath(File oldFile, String internalSd, String appPkgPath) {
        String oldPath = getRealPath(oldFile);
        if (log_debug) {
            log("requested path: " + oldPath);
        }

        String newPath = null;
        if (oldPath.startsWith(internalSd)) {
            // path within sdcard
            String inSdPath = oldPath.substring(internalSd.length());
            String inSdPath2 = inSdPath.toLowerCase();

            if (inSdPath2.endsWith(File.separator + Common.FILE_NOMEDIA)) {
                // redirect all ".no_media" to one
                newPath = noMediaFile;
            } else if (inSdPath2.startsWith(appSdBase2)) {
                // within the home of sandbox
                int inSdPathLength = inSdPath2.length();
                int appPkgPathLength = appPkgPath.length();

                if (!inSdPath2.startsWith(appPkgPath, appSdBaseLength)) {
                    if (inSdPathLength > appSdBaseLength) {
                        if (inSdPath2.charAt(appSdBaseLength) == File.separatorChar) {
                            newPath = internalSd + appSdBase + appPkgPath + inSdPath.substring(appSdBaseLength);
                        } else {
                            newPath = internalSd + appSdBase + appPkgPath + inSdPath;
                        }
                    } else {
                        newPath = internalSd + appSdBase + appPkgPath;
                    }
                } else {
                    // check redundant app path
                    int subPathLength = appSdBaseLength + appPkgPathLength;
                    int offset = subPathLength;
                    while (inSdPath2.startsWith(appSdBase2 + appPkgPath, offset)) {
                        offset += subPathLength;
                    }
                    if (offset > subPathLength) {
                        newPath = internalSd + appSdBase + appPkgPath + inSdPath.substring(offset);
                    }
                }
            } else {
                // not in sandbox
                if ((excludePaths == null) || (!isExcludePath(inSdPath2, excludePaths))) {
                    // make File object within sandbox
                    newPath = internalSd + appSdBase + appPkgPath + inSdPath;
                }
            }
        }

        if (log_debug && (newPath != null)) {
            log("patched path: " + newPath);
        }

        return newPath;
    }
}
