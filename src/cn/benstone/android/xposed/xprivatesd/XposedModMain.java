package cn.benstone.android.xposed.xprivatesd;

import android.text.TextUtils;
import android.util.StringBuilderPrinter;

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
    private static XSharedPreferences prefs = null;

    private boolean log_debug;

    private String userSd;
    private int userSdLength;

    private int perAppBaseLength;
    private String perAppBase2;

    private String appPath;
    private int appPathLength;

    private String perAppPath;
    private String noMediaFile;
    private String[] excludePaths = null;

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
        if (prefs == null) {
            prefs = new XSharedPreferences(XposedModMain.class.getPackage().getName());
            prefs.makeWorldReadable();
        }
        userSd = Common.getInternalStoragePath();
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        prefs.reload();
        log_debug = prefs.getBoolean(Common.LOG_DEBUG, false);

        if (log_debug) {
            if (userSd != null) {
                log("userSD: " + userSd);
            } else {
                log("userSD: N/A");
            }

            if (lpparam.packageName != null) {
                log("packageName: " + lpparam.packageName);
            } else {
                log("packageName: N/A");
            }

            if (lpparam.processName != null) {
                log("processName: " + lpparam.processName);
            } else {
                log("processName: N/A");
            }

            if (lpparam.isFirstApplication) {
                log("isFirstApplication: true");
            } else {
                log("isFirstApplication: false");
            }

            if (lpparam.appInfo != null) {
                StringBuilder builder = new StringBuilder();
                lpparam.appInfo.dump(new StringBuilderPrinter(builder), "");
                log("appInfo: " + builder.toString());
            } else {
                log("appInfo: N/A");
            }
        }

        if (userSd == null) {
            // system services
            return;
        }

        String appName = lpparam.isFirstApplication ? lpparam.packageName : lpparam.processName;
        int appFlags = lpparam.appInfo != null ? lpparam.appInfo.flags : 0;

        if (!isEnabledApp(appName, appFlags)) {
            return;
        }

        userSdLength = userSd.length();

        String perAppBase = File.separator + prefs.getString(Common.PER_APP_PATH, Common.DEFAULT_PER_APP_PATH);
        perAppBaseLength = perAppBase.length();
        perAppBase2 = perAppBase.toLowerCase();

        appPath = File.separator + appName.toLowerCase();
        appPathLength = appPath.length();

        File perAppPathFile = new File(userSd + perAppBase + appPath);
        perAppPath = perAppPathFile.getAbsolutePath();
        if (log_debug) {
            log("sandbox: " + perAppPath);
        }
        if (!perAppPathFile.exists()) {
            // create missed dirs
            try {
                if (perAppPathFile.mkdirs() && log_debug) {
                    log("sandbox created for " + lpparam.packageName);
                }
            } catch (Exception e) {
                log("create sandbox failed for " + lpparam.packageName);
                return;
            }
        }

        if (prefs.getBoolean(Common.NO_MEDIA_SCAN, true)) {
            noMediaFile = perAppPath + File.separator + Common.FILE_NOMEDIA;
        } else {
            noMediaFile = perAppPath + File.separator + Common.FILE_DUMMY;
        }

        final String excludePathStr = prefs.getString(Common.EXCLUDE_PATH, Common.EMPTY_PATH);
        if (!excludePathStr.isEmpty()) {
            excludePaths = excludePathStr.toLowerCase().split(Common.WRAP_STRING);
            if (log_debug) {
                log("excludes: " + TextUtils.join(File.pathSeparator, excludePaths));
            }
        }

        XposedHelpers.findAndHookConstructor("java.io.File", lpparam.classLoader,
                String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String newPath = getPatchedPath((File) param.thisObject);
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
                String newPath = getPatchedPath((File) param.thisObject);
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
                        String newPath = getPatchedPath((File) param.thisObject);
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

    private boolean isEnabledApp(String appName, int appFlags) {
        if (!Common.isAppHookAllow(prefs, appName, appFlags)) {
            return false;
        }

        Set<String> enabledApps = prefs.getStringSet(Common.ENABLED_APPS, new HashSet<String>());
        return (!enabledApps.isEmpty()) && enabledApps.contains(appName);
    }

    private static boolean isExcludePath(String path, String[] excludeList, boolean log_debug) {
        for (String excludePath : excludeList) {
//            log("check excludes: " + excludePath);
            if (path.startsWith(excludePath)) {
                if (log_debug) {
                    log("exclude: " + excludePath + ", " + path);
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

    private String getPatchedPath(File oldFile) {
        String oldPath = getRealPath(oldFile);
        if (log_debug) {
            log("[O]: " + oldPath);
        }

        String newPath = null;
        if (oldPath.startsWith(userSd)) {
            // path within sdcard
            String inSdPath = oldPath.substring(userSdLength);
            String inSdPath2 = inSdPath.toLowerCase();

            if (inSdPath2.endsWith(File.separator + Common.FILE_NOMEDIA)) {
                // redirect all ".no_media" to one
                newPath = noMediaFile;
            }

            else if (inSdPath2.startsWith(perAppBase2)) {
                // within the home of sandbox
                if (!inSdPath2.startsWith(appPath, perAppBaseLength)) {
                    if (inSdPath2.length() > perAppBaseLength) {
                        if (inSdPath2.charAt(perAppBaseLength) == File.separatorChar) {
                            newPath = perAppPath + inSdPath.substring(perAppBaseLength);
                        } else {
                            newPath = perAppPath + inSdPath;
                        }
                    } else {
                        newPath = perAppPath;
                    }
                } else {
                    // check redundant app path
                    int subPathLength = perAppBaseLength + appPathLength;
                    int offset = subPathLength;

                    while (inSdPath2.startsWith(perAppBase2 + appPath, offset)) {
                        offset += subPathLength;
                    }

                    if (offset > subPathLength) {
                        newPath = perAppPath + inSdPath.substring(offset);
                    }
                }
            }

            else {
                // not in sandbox
                if ((excludePaths == null) || (!isExcludePath(inSdPath2, excludePaths, log_debug))) {
                    // make File object within sandbox
                    newPath = perAppPath + inSdPath;
                }
            }
        } else {
            // check path like /[0-9a-f]+/storage/emulated/0
            if (oldPath.length() > userSdLength) {
                int off = oldPath.indexOf(File.separatorChar, 1);
                if ((off > 0) && (oldPath.substring(off).startsWith(userSd))) {
                    newPath = perAppPath + oldPath.substring(off + userSdLength);
                }
            }
        }

        if (log_debug && (newPath != null)) {
            log("[P]: " + newPath);
        }

        return newPath;
    }
}
