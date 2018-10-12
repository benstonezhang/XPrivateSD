package cn.benstone.android.xposed.xprivatesd;

import android.text.TextUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
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
    final static String separatorChars = File.separator + "+";
    final static Character dot = '.';
    final static String dot2 = "..";
    final static String dotMulti = "\\.\\.+";
    final static String fileScheme = "file";

    public XSharedPreferences prefs;

    public String internalSd;
    public int internalSdLen;
    public String appSdBase;
    public String[] excludePaths;

    private static void log(String s) {
//        XposedBridge.log(s);
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        prefs = new XSharedPreferences(XposedModMain.class.getPackage().getName());
        prefs.makeWorldReadable();

        internalSd = prefs.getString(Common.INTERNAL_SDCARD_PATH, null);
        if (internalSd == null) {
            prefsNotReady();
            return;
        }
        internalSdLen = internalSd.length();
        log("Internal SD path: " + internalSd);

        appSdBase = File.separator + prefs.getString(Common.PER_APP_PATH,
                Common.DEFAULT_PER_APP_PATH);
        log("App SD path base: " + appSdBase);

        for (String path : Common.PER_APP_EXCLUDE_PATHS) {
            log("Exclude per app path: " + path + File.separator + "<package name>");
        }

        final String excludePathStr = prefs.getString(Common.EXCLUDE_PATH, Common.EMPTY_PATH);
        if (!excludePathStr.isEmpty()) {
            excludePaths = excludePathStr.split(Common.wrapString);
            for (int i = 0; i< excludePaths.length; i++) {
                excludePaths[i] = File.separator + excludePaths[i].toLowerCase();
            }
            log("Exclude paths: " + TextUtils.join(";", excludePaths));
        } else {
            excludePaths = null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (internalSd == null) {
            prefsNotReady();
            return;
        }

        prefs.reload();
        String packageName = lpparam.packageName;

        if (!isEnabledApp(lpparam)) {
            log("XPrivateSD is disabled for " + packageName);
            return;
        }

        final String appSdPath = appSdBase + File.separator + packageName;
        final String appSdPath2 = appSdPath.toLowerCase();

        log("XPrivateSD is enabled for " + packageName +
                ", SDCard path: " + internalSd + appSdPath);

        //make missing dirs
        File perAppPath = new File(internalSd + appSdPath);
        if (!perAppPath.exists()) {
            try {
                perAppPath.mkdirs();
            } catch (Exception e) {
                XposedBridge.log("Create app SD folder failed: " + packageName);
                return;
            }
        }
        log("Folder created for " + packageName);

        final String[] perAppExcludePaths = new String[Common.PER_APP_EXCLUDE_PATHS.length];
        for (int i=0; i<Common.PER_APP_EXCLUDE_PATHS.length; i++) {
            perAppExcludePaths[i] = File.separator + Common.PER_APP_EXCLUDE_PATHS[i].toLowerCase() +
                    File.separator + packageName.toLowerCase();
        }
        log("Per app exclude paths: " + TextUtils.join(";", perAppExcludePaths));

        XC_MethodHook fileHook1 = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String reqPath = (String) param.args[0];
                if ((reqPath != null) && (!reqPath.isEmpty())) {
                    param.args[0] = getPatchedPath(reqPath, appSdPath, appSdPath2,
                            perAppExcludePaths);
                }
            }
        };

        XposedHelpers.findAndHookConstructor("java.io.File", lpparam.classLoader,
                String.class, fileHook1);

        XC_MethodHook fileHook2 = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String reqPath = (String) param.args[0];
                String childPath = (String) param.args[1];

                reqPath = getCombinedPath(reqPath, childPath);

                if ((reqPath != null) && (!reqPath.isEmpty())) {
                    param.args[0] = null;
                    param.args[1] = getPatchedPath(reqPath, appSdPath, appSdPath2,
                            perAppExcludePaths);
                }
            }
        };
        XposedHelpers.findAndHookConstructor("java.io.File", lpparam.classLoader,
                String.class, String.class, fileHook2);

        XC_MethodHook fileHook3 = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                File reqFile = (File) param.args[0];
                String reqPath = reqFile != null ? getRealPath(reqFile) : null;
                String childPath = (String) param.args[1];

                reqPath = getCombinedPath(reqPath, childPath);

                if ((reqPath != null) && (!reqPath.isEmpty())) {
                    param.args[0] = null;
                    param.args[1] = getPatchedPath(reqPath, appSdPath, appSdPath2,
                            perAppExcludePaths);
                }
            }
        };
        XposedHelpers.findAndHookConstructor("java.io.File", lpparam.classLoader,
                File.class, String.class, fileHook3);

        XC_MethodHook fileHook4 = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                URI reqURI = (URI) param.args[0];
                if ((reqURI != null) && fileScheme.equals(reqURI.getScheme())) {
                    String reqPath = reqURI.getPath();
                    if (!reqPath.isEmpty()) {
                        param.args[0] = getPatchedPath(reqPath, appSdPath, appSdPath2,
                                perAppExcludePaths);
                    }
                }
            }
        };
        XposedHelpers.findAndHookConstructor("java.io.File", lpparam.classLoader,
                URI.class, fileHook4);

//        XC_MethodHook externalStorageDirHook = new XC_MethodHook() {
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                File oldDirPath = (File) param.getResult();
//                if (oldDirPath != null) {
//                    param.setResult(getPatchedDir(oldDirPath, appSdPath, appSdPath2));
//                }
//            }
//        };
//        XposedHelpers.findAndHookMethod(Environment.class, "getExternalStorageDirectory",
//                externalStorageDirHook);
//        XposedHelpers.findAndHookMethod(Environment.class, "getExternalStoragePublicDirectory",
//                String.class, externalStorageDirHook);

//        XC_MethodHook externalFilesDirHook = new XC_MethodHook() {
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                File oldDirPath = (File) param.getResult();
//                if (oldDirPath != null) {
//                    param.setResult(getOriginalDir(oldDirPath, appSdPath2));
//                }
//            }
//        };
//        XposedHelpers.findAndHookMethod(XposedHelpers.findClass(
//                "android.app.ContextImpl", lpparam.classLoader),
//                "getExternalFilesDir", String.class, externalFilesDirHook);
//        XposedHelpers.findAndHookMethod(XposedHelpers.findClass(
//                "android.app.ContextImpl", lpparam.classLoader),
//                "getObbDir", externalFilesDirHook);
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            XC_MethodHook externalStorageDirsHook = new XC_MethodHook() {
//                @Override
//                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                    File[] oldDirPaths = (File[]) param.getResult();
//                    File[] newDirPaths = new File[oldDirPaths.length];
//                    for (int i=0; i<oldDirPaths.length; i++) {
//                        newDirPaths[i] = getOriginalDir(oldDirPaths[i], appSdPath2);
//                        log("externalStorageDirs: " + newDirPaths[i]);
//                    }
//                    param.setResult(newDirPaths);
//                }
//            };
//
//            XposedHelpers.findAndHookMethod(XposedHelpers.findClass(
//                    "android.app.ContextImpl", lpparam.classLoader),
//                    "getExternalFilesDirs", String.class,
//                    externalStorageDirsHook);
//            XposedHelpers.findAndHookMethod(XposedHelpers.findClass(
//                    "android.app.ContextImpl", lpparam.classLoader),
//                    "getObbDirs", externalStorageDirsHook);
//        }
    }

    public boolean isEnabledApp(LoadPackageParam lpparam) {
        log("Checking package: " + lpparam.packageName);

        boolean isEnabled = Common.isAllowedApp(prefs, lpparam.appInfo);
        if (!isEnabled) {
            return isEnabled;
        }

        Set<String> excludeApps = prefs.getStringSet(Common.ENABLED_APPS, new HashSet<String>());
        return (!excludeApps.isEmpty()) && excludeApps.contains(lpparam.packageName);
    }

    private static void prefsNotReady() {
        XposedBridge.log("Path to internal SD card not specified, exit");
    }

//    private static File makeDir(String dir) {
//        File dirPath = new File(dir);
//        if (!dirPath.exists()) {
//            dirPath.mkdirs();
//        }
//        return dirPath;
//    }

    private static boolean isExcludePath(String path, String[] excludeList) {
        for (String excludePath : excludeList) {
            log("check exclude path: " + excludePath);
            if (path.startsWith(excludePath)) {
                // exclude path
                log("Exclude path, bypass");
                return true;
            }
        }
        return false;
    }

    private static String getAbsolutePath(String reqPath) {
        return getRealPath(new File("")) + File.separator + reqPath;
    }

    private static String getCanonicalPath(String reqPath) {
        if (reqPath.charAt(0) != File.separatorChar) {
            // convert relative path to absolute path
            reqPath = getAbsolutePath(reqPath);
        }

        reqPath = reqPath.replaceAll(separatorChars, File.separator);
        reqPath = reqPath.replaceAll(dotMulti, dot2);

        if (reqPath.indexOf(dot) >= 0) {
            // resolve all relative path
            ArrayList<String> dirStack = new ArrayList<>();
            dirStack.add(0, Common.EMPTY_PATH);

            int count = 0;
            String[] dirs = reqPath.split(File.separator);
            for (String dir : dirs) {
                if (!dir.isEmpty()) {
                    if (dir.charAt(0) != dot) {
                        dirStack.add(dir);
                        count += 1;
                    } else if (dir.equals(dot2) && (count > 0)) {
                        count -= 1;
                        dirStack.remove(count);
                    }
                }
            }
            reqPath = TextUtils.join(File.separator, dirStack);
        }

        return reqPath;
    }

    private String getPatchedPath(String reqPath, String appSdPath, String appSdPath2,
                                  String[] perAppExcludePaths) {
        log("Request path: " + reqPath);

        reqPath = getCanonicalPath(reqPath);
        int pathLen = reqPath.length();
        if (pathLen < internalSdLen) {
            return reqPath;
        }

        if (reqPath.startsWith(internalSd)) {
            if (pathLen > internalSdLen) {
                String subPath = reqPath.substring(internalSdLen);
                String subPath2 = subPath.toLowerCase();
                log("sub path: " + subPath);

                if (subPath2.startsWith(appSdPath2)) {
                    int appSdPathLen = appSdPath2.length();
                    subPath2 = subPath2.substring(appSdPathLen);
                    // the path is patched
                    if (isExcludePath(subPath2, perAppExcludePaths) ||
                            ((excludePaths != null) && isExcludePath(subPath2, excludePaths))) {
                        log("Patched path with exclude path, revert it back");
                        reqPath = internalSd + subPath.substring(appSdPathLen);
                    } else {
                        log("Patched path, ignore");
                    }
                } else {
                    if ((!isExcludePath(subPath2, perAppExcludePaths)) &&
                            ((excludePaths == null) || (!isExcludePath(subPath2, excludePaths)))) {
                        reqPath = internalSd + appSdPath + subPath;
                    }
                }
            } else {
                reqPath = internalSd + appSdPath;
            }

            log("Returned path: " + reqPath);
        }

        return reqPath;
    }

//    private File getPatchedDir(File reqFile, String appSdPath, String appSdPath2) {
//        String reqDir = getRealPath(reqFile);
//        String newDir = getPatchedPath(reqDir, appSdPath, appSdPath2);
//        return makeDir(newDir);
//    }

//    private String getOriginalPath(String reqDir, String appSdPath) {
//        if (reqDir.startsWith(internalSd) &&
//                reqDir.toLowerCase().startsWith(appSdPath, internalSdLen)) {
//            reqDir = internalSd + reqDir.substring(internalSdLen + appSdPath.length());
//        }
//        return reqDir;
//    }

//    private File getOriginalDir(File reqFile, String appSdPath) {
//        String reqDir = getRealPath(reqFile);
//        String newDir = getOriginalPath(reqDir, appSdPath);
//        return makeDir(newDir);
//    }

    private static String getRealPath(File f) {
        String path;
        try {
            path = f.getCanonicalPath();
        } catch (IOException e) {
            path = f.getAbsolutePath();
        }
        return path;
    }

    private static String getCombinedPath(String parentPath, String childPath) {
        String newPath;
        if (childPath == null) {
            newPath = childPath;
        } else if (parentPath == null) {
            newPath = childPath;
        } else if (parentPath.isEmpty()) {
            newPath = File.separator + childPath;
        } else {
            newPath = parentPath + File.separator + childPath;
        }
        return newPath;
    }
}
