package cn.benstone.android.xposed.xprivatesd;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Preferences extends Activity {
    public static Context context;
    public static SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        getFragmentManager().beginTransaction().replace(android.R.id.content, new Settings()).commit();
    }

    @SuppressWarnings("deprecation")
    public static class Settings extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
            addPreferencesFromResource(R.xml.preferences);

            prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String internalSd = prefs.getString(Common.INTERNAL_SDCARD_PATH, null);
            if (internalSd == null) {
                internalSd = Common.getInternalStoragePath();
            }
            EditTextPreference internalSdPath = (EditTextPreference) findPreference(Common.INTERNAL_SDCARD_PATH);
            internalSdPath.setSummary(internalSd);
            internalSdPath.setText(internalSd);
            internalSdPath.setEnabled(false);

            String perAppPath = prefs.getString(Common.PER_APP_PATH, Common.DEFAULT_PER_APP_PATH);
            EditTextPreference patchedInternalSdPath = (EditTextPreference) findPreference(Common.PER_APP_PATH);
            patchedInternalSdPath.setSummary(perAppPath);
            patchedInternalSdPath.setText(perAppPath);
            patchedInternalSdPath.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            String newPath = ((String) newValue).trim();
                            if (newPath.isEmpty()) {
                                newPath = Common.DEFAULT_PER_APP_PATH;
                            }
                            preference.setSummary(newPath);
                            Toast.makeText(context, R.string.reboot_required, Toast.LENGTH_LONG).show();
                            return true;
                        }
                    });

            String excludePath = prefs.getString(Common.EXCLUDE_PATH, Common.EMPTY_PATH);
            EditTextPreference excludePathEdit = (EditTextPreference) findPreference(Common.EXCLUDE_PATH);
            excludePathEdit.setSummary(excludePath);
            excludePathEdit.setText(excludePath);
            excludePathEdit.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            String[] paths = ((String) newValue).split(Common.wrapString);
                            for (int i=0; i<paths.length; i++) {
                                paths[i] = paths[i].trim();
                            }
                            String newPath = TextUtils.join(Common.wrapString, paths);
                            preference.setSummary(newPath);
                            Toast.makeText(context, R.string.reboot_required, Toast.LENGTH_LONG).show();
                            return true;
                        }
                    });

            Preference includeSystemApps = findPreference(Common.INCLUDE_SYSTEM_APPS);
            includeSystemApps.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            reloadAppsList();
                            return true;
                        }
                    });

            reloadAppsList();
        }

        @Override
        public void onPause() {
            super.onPause();

            // Set preferences file permissions to be world readable
            File prefsDir = new File(getActivity().getApplicationInfo().dataDir, "shared_prefs");
            File prefsFile = new File(prefsDir, getPreferenceManager().getSharedPreferencesName() + ".xml");
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false);
            }
        }

        public void reloadAppsList() {
            new LoadApps().execute();
        }

        public class LoadApps extends AsyncTask<Void, Void, Void> {
            MultiSelectListPreference enabledApps = (MultiSelectListPreference) findPreference(Common.ENABLED_APPS);
            List<CharSequence> appNames = new ArrayList<>();
            List<CharSequence> packageNames = new ArrayList<>();
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

            @Override
            protected void onPreExecute() {
                enabledApps.setEnabled(false);
            }

            @Override
            protected Void doInBackground(Void... arg0) {
                List<String[]> sortedApps = new ArrayList<>();

                for (ApplicationInfo app : packages) {
                    if (Common.isAllowedApp(prefs, app.packageName, app.flags)) {
                        sortedApps.add(new String[]{
                                app.packageName,
                                app.loadLabel(pm).toString()});
                    }
                }

                Collections.sort(sortedApps, new Comparator<String[]>() {
                    @Override
                    public int compare(String[] entry1, String[] entry2) {
                        return entry1[1].compareToIgnoreCase(entry2[1]);
                    }
                });

                // put selected package at head of the list
                Set<String> enabledAppSet = prefs.getStringSet(Common.ENABLED_APPS, new HashSet<String>());
                List<CharSequence> unselectedAppNames = new ArrayList<>();
                List<CharSequence> unselectedPackageNames = new ArrayList<>();
                List<CharSequence> app;
                List<CharSequence> pkg;
                for (String[] sortedApp : sortedApps) {
                    String packageName = sortedApp[0];
                    if (enabledAppSet.contains(packageName)) {
                        app = appNames;
                        pkg = packageNames;
                    } else {
                        app = unselectedAppNames;
                        pkg = unselectedPackageNames;
                    }
                    String label = sortedApp[1];
                    app.add(label + "\n" + "(" + packageName + ")");
                    pkg.add(packageName);
                }
                appNames.addAll(unselectedAppNames);
                packageNames.addAll(unselectedPackageNames);

                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                CharSequence[] appNamesList = appNames.toArray(new CharSequence[appNames.size()]);
                CharSequence[] packageNamesList = packageNames.toArray(new CharSequence[packageNames.size()]);

                enabledApps.setEntries(appNamesList);
                enabledApps.setEntryValues(packageNamesList);
                enabledApps.setEnabled(true);

                Preference.OnPreferenceClickListener listener = new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        ((MultiSelectListPreference) preference).getDialog().getWindow().setLayout(
                                WindowManager.LayoutParams.FILL_PARENT,
                                WindowManager.LayoutParams.FILL_PARENT);
                        return false;
                    }
                };

                enabledApps.setOnPreferenceClickListener(listener);
            }
        }
    }
}
