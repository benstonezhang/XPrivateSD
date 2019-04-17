package cn.benstone.android.xposed.xprivatesd;

import android.content.pm.ApplicationInfo;
import android.text.TextUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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
        XposedBridge.log(Common.APP_NAME + ": " + s);
    }

    private static void debug(String s) {
//        log(s);
    }

    private static void trace(String s) {
        StringWriter sw = new StringWriter();
        new Throwable(s).printStackTrace(new PrintWriter(sw));
        log(sw.toString());
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
        appSdBase = File.separator + prefs.getString(Common.PER_APP_PATH, Common.DEFAULT_PER_APP_PATH);
        debug("internal SD path is " + internalSd + ", app path base is " + appSdBase);

        final String excludePathStr = prefs.getString(Common.EXCLUDE_PATH, Common.EMPTY_PATH);
        if (!excludePathStr.isEmpty()) {
            excludePaths = excludePathStr.split(Common.WRAP_STRING);
            for (int i = 0; i< excludePaths.length; i++) {
                excludePaths[i] = File.separator + excludePaths[i].toLowerCase();
            }
            debug("exclude paths " + TextUtils.join(File.pathSeparator, excludePaths));
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
        final String packageName = lpparam.packageName;

        if (!isEnabledApp(lpparam)) {
            return;
        }

        final String appSdPath = appSdBase + File.separator + packageName;
        final String appSdPath2 = appSdPath.toLowerCase();
        debug("app sd path is " + internalSd + appSdPath);

        //make missing dirs
        File perAppPath = new File(internalSd + appSdPath);
        if (!perAppPath.exists()) {
            try {
                perAppPath.mkdirs();
            } catch (Exception e) {
                log("create app SD folder failed for " + packageName);
                return;
            }
        }
        debug("folder created for " + packageName);

        XC_MethodHook fileHook1 = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                fileHookCallback1(param, appSdPath, appSdPath2);
            }
        };
        XposedHelpers.findAndHookConstructor("java.io.File", lpparam.classLoader, String.class, fileHook1);

        XC_MethodHook fileHook2 = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                fileHookCallback2(param, appSdPath, appSdPath2);
            }
        };
        XposedHelpers.findAndHookConstructor("java.io.File", lpparam.classLoader,
                String.class, String.class, fileHook2);

        XC_MethodHook fileHook3 = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                fileHookCallback3(param, appSdPath, appSdPath2);
            }
        };
        XposedHelpers.findAndHookConstructor("java.io.File", lpparam.classLoader,
                File.class, String.class, fileHook3);

        XC_MethodHook fileHook4 = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                fileHookCallback4(param, appSdPath, appSdPath2);
            }
        };
        XposedHelpers.findAndHookConstructor("java.io.File", lpparam.classLoader, URI.class, fileHook4);

//        XC_MethodHook externalStorageDirHook = new XC_MethodHook() {
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                File oldDir = (File) param.getResult();
//                if (oldDir != null) {
//                    log(packageName + " getExternalStorageDirectory '" +
//                            oldDir.getAbsolutePath() + "'");
//                }
//            }
//        };
//        XposedHelpers.findAndHookMethod(Environment.class, "getExternalStorageDirectory",
//                externalStorageDirHook);
//        XposedHelpers.findAndHookMethod(Environment.class, "getExternalStoragePublicDirectory",
//                String.class, externalStorageDirHook);
//
//        XC_MethodHook externalFilesDirHook = new XC_MethodHook() {
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                File oldDir = (File) param.getResult();
//                if (oldDir != null) {
//                    log(packageName + " getExternalFilesDir '" + oldDir.getAbsolutePath() + "'");
//                }
//            }
//        };
//        XposedHelpers.findAndHookMethod(XposedHelpers.findClass("android.app.ContextImpl",
//                lpparam.classLoader),
//                "getExternalFilesDir", String.class, externalFilesDirHook);
//        XposedHelpers.findAndHookMethod(XposedHelpers.findClass("android.app.ContextImpl",
//                lpparam.classLoader),
//                "getObbDir", externalFilesDirHook);
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            XC_MethodHook externalStorageDirsHook = new XC_MethodHook() {
//                @Override
//                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                    File[] oldDirs = (File[]) param.getResult();
//                    if (oldDirs.length > 0) {
//                        String[] oldPaths = new String[oldDirs.length];
//                        for (int i=0; i<oldDirs.length; i++) {
//                            oldPaths[i] = oldDirs[i].getAbsolutePath();
//                        }
//                        log(packageName + " getExternalFilesDirs '" +
//                                TextUtils.join(File.pathSeparator, oldPaths) + "'");
//                    }
//                }
//            };
//            XposedHelpers.findAndHookMethod(XposedHelpers.findClass("android.app.ContextImpl", lpparam.classLoader),
//                    "getExternalFilesDirs", String.class, externalStorageDirsHook);
//            XposedHelpers.findAndHookMethod(XposedHelpers.findClass("android.app.ContextImpl", lpparam.classLoader),
//                    "getObbDirs", externalStorageDirsHook);
//        }
    }

    public boolean isEnabledApp(LoadPackageParam lpparam) {
        debug("checking package " + lpparam.packageName);

        int appFlags = lpparam.appInfo == null ? ApplicationInfo.FLAG_SYSTEM : lpparam.appInfo.flags;
        if (!Common.isAllowedApp(prefs, lpparam.packageName, appFlags)) {
            return false;
        }

        Set<String> enabledApps = prefs.getStringSet(Common.ENABLED_APPS, new HashSet<String>());
        return (!enabledApps.isEmpty()) && enabledApps.contains(lpparam.packageName);
    }

    private static void prefsNotReady() {
        log("path of internal SD card not specified, exit");
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
            debug("check exclude path " + excludePath);
            if (path.startsWith(excludePath)) {
                debug("matched, bypass");
                return true;
            }
        }
        return false;
    }

    private static String getAbsolutePath(String reqPath) {
        return getRealPath(new File("")) + File.separator + reqPath;
    }

    private static String getCanonicalPath(String reqPath) {
        reqPath = reqPath.replaceAll(separatorChars, File.separator);
        reqPath = reqPath.replaceAll(dotMulti, dot2);

        if (reqPath.contains(dot2)) {
            // resolve all relative path
            int count = 0;
            ArrayList<String> dirStack = new ArrayList<>();
            String[] dirs = reqPath.split(File.separator);
            for (String dir : dirs) {
                if (dir.isEmpty() || (dir.charAt(0) != dot)) {
                    dirStack.add(dir);
                    count += 1;
                } else if (dir.equals(dot2) && (count > 0)) {
                    count -= 1;
                    dirStack.remove(count);
                }
            }
            reqPath = TextUtils.join(File.separator, dirStack);
        }

        return reqPath;
    }

    private String getPatchedPath(String reqPath, String appSdPath, String appSdPath2) {
        debug("request path '" + reqPath + "'");

        if (reqPath.charAt(0) != File.separatorChar) {
            // path relative to app's data folder /data/data/<package>
            return reqPath;
        }

        reqPath = getCanonicalPath(reqPath);

        if (reqPath.startsWith(internalSd)) {
            // in the sandbox, no need to create file .nomedia in sub folders
            if (reqPath.endsWith(Common.FILE_NOMEDIA)) {
                return internalSd + appSdPath + File.separator + Common.FILE_NOMEDIA;
            }

            String subPath = reqPath.substring(internalSdLen);
            String subPath2 = subPath.toLowerCase();
            debug("sub path '" + subPath + "'");

            if (subPath2.startsWith(appSdPath2)) {
                int appSdPathLen = appSdPath2.length();
                subPath2 = subPath2.substring(appSdPathLen);

                if ((excludePaths != null) && isExcludePath(subPath2, excludePaths)) {
                    debug("patched path with exclude path, revert it back");
                    reqPath = internalSd + subPath.substring(appSdPathLen);
                } else {
                    debug("patched path, ignore");
                }
            } else {
                if ((excludePaths == null) || (!isExcludePath(subPath2, excludePaths))) {
                    reqPath = internalSd + appSdPath + subPath;
                }
            }
        }

        debug("returned path '" + reqPath + "'");
        return reqPath;
    }

    private void fileHookCallback1(XC_MethodHook.MethodHookParam param, String appSdPath, String appSdPath2) {
        if (param.args[0] != null) {
            String reqPath = (String) param.args[0];
            if (!reqPath.isEmpty()) {
                param.args[0] = getPatchedPath(reqPath, appSdPath, appSdPath2);
            }
        } else {
            param.setThrowable(new NullPointerException());
        }
    }

    private void fileHookCallback2(XC_MethodHook.MethodHookParam param, String appSdPath, String appSdPath2) {
        if (param.args[1] != null) {
            String parentPath = (String) param.args[0];
            String childPath = (String) param.args[1];
            if (!childPath.isEmpty()) {
                if (parentPath != null) {
                    parentPath = getCanonicalPath(parentPath);
                    childPath = getCanonicalPath(childPath);
                    String reqPath = getPatchedPath(parentPath + File.separator + childPath, appSdPath, appSdPath2);
                    if (reqPath.endsWith(childPath)) {
                        param.args[0] = reqPath.substring(0, reqPath.length() - childPath.length());
                    } else {
                        param.args[0] = null;
                        param.args[1] = reqPath;
                    }
                } else {
                    param.args[1] = getPatchedPath(childPath, appSdPath, appSdPath2);
                }
            } else if ((parentPath != null) && (!parentPath.isEmpty())) {
                param.args[0] = getPatchedPath(parentPath, appSdPath, appSdPath2);
            }
        } else {
            param.setThrowable(new NullPointerException());
        }
    }

    private void fileHookCallback3(XC_MethodHook.MethodHookParam param, String appSdPath, String appSdPath2) {
        if (param.args[1] != null) {
            String childPath = (String) param.args[1];
            if ((!childPath.isEmpty()) && (childPath.contains(Common.PARENT_DIR))) {
                String parentPath = param.args[0] != null ? getRealPath((File) param.args[0]) : null;
                if (parentPath != null) {
                    if (!parentPath.isEmpty()) {
                        String reqPath = getPatchedPath(parentPath + File.separator + childPath,
                                appSdPath, appSdPath2);
                        if (reqPath.startsWith(parentPath)) {
                            param.args[1] = reqPath.substring(parentPath.length());
                        } else {
                            param.args[0] = null;
                            param.args[1] = reqPath;
                        }
                    }
                } else {
                    param.args[1] = getPatchedPath(childPath, appSdPath, appSdPath2);
                }
            }
        } else {
            param.setThrowable(new NullPointerException());
        }
    }

    private void fileHookCallback4(XC_MethodHook.MethodHookParam param, String appSdPath, String appSdPath2) {
        if (param.args[0] != null) {
            URI reqURI = (URI) param.args[0];
            if (fileScheme.equals(reqURI.getScheme())) {
                String reqPath = reqURI.getPath();
                if (!reqPath.isEmpty()) {
                    param.args[0] = getPatchedPath(reqPath, appSdPath, appSdPath2);
                }
            }
        } else {
            param.setThrowable(new NullPointerException());
        }
    }

//    private File getPatchedDir(File reqFile, String appSdPath, String appSdPath2) {
//        String reqDir = getRealPath(reqFile);
//        String newDir = getPatchedPath(reqDir, appSdPath, appSdPath2);
//        return makeDir(newDir);
//    }

//    private String getOriginalPath(String reqDir, String appSdPath) {
//        if (reqDir.startsWith(internalSd) && reqDir.toLowerCase().startsWith(appSdPath, internalSdLen)) {
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
        if ((childPath == null) || (parentPath == null)) {
            return childPath;
        } else {
            return parentPath + File.separator + childPath;
        }
    }
}
