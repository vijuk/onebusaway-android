/*
 * Copyright (C) 2010-2015 Brian Ferris (bdferris@onebusaway.org), University of South Florida
 * and individual contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.joulespersecond.seattlebusbot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.Window;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.joulespersecond.oba.ObaAnalytics;
import com.joulespersecond.oba.region.ObaRegionsTask;
import com.joulespersecond.seattlebusbot.util.UIHelp;

public class PreferencesActivity extends SherlockPreferenceActivity
        implements Preference.OnPreferenceClickListener, OnPreferenceChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener, ObaRegionsTask.Callback {

    private static final String TAG = "PreferencesActivity";

    Preference regionPref;

    Preference customApiUrlPref;

    Preference analyticsPref;

    Preference donatePref;

    boolean autoSelectInitialValue;
    //Save initial value so we can compare to current value in onDestroy()

    ListPreference preferredUnits;

    // Soo... we can use SherlockPreferenceActivity to display the
    // action bar, but we can't use a PreferenceFragment?
    @SuppressWarnings("deprecation")
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        UIHelp.setupActionBar(getSupportActionBar());
        setSupportProgressBarIndeterminate(true);

        addPreferencesFromResource(R.xml.preferences);

        regionPref = findPreference(getString(R.string.preference_key_region));
        regionPref.setOnPreferenceClickListener(this);

        customApiUrlPref = findPreference(getString(R.string.preference_key_oba_api_url));
        customApiUrlPref.setOnPreferenceChangeListener(this);

        analyticsPref = findPreference(getString(R.string.preferences_key_analytics));
        analyticsPref.setOnPreferenceChangeListener(this);

        donatePref = findPreference(getString(R.string.preferences_key_donate));
        donatePref.setOnPreferenceClickListener(this);

        SharedPreferences settings = Application.getPrefs();
        autoSelectInitialValue = settings
                .getBoolean(getString(R.string.preference_key_auto_select_region), true);

        preferredUnits = (ListPreference) findPreference(
                getString(R.string.preference_key_preferred_units));

        settings.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        changePreferenceSummary(getString(R.string.preference_key_region));
        changePreferenceSummary(getString(R.string.preference_key_preferred_units));
    }

    @Override
    protected void onStart() {
        super.onStart();
        ObaAnalytics.reportActivityStart(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    /**
     * Changes the summary of a preference based on a given preference key
     *
     * @param preferenceKey preference key that triggers a change in summary
     */
    private void changePreferenceSummary(String preferenceKey) {
        // Change the current region summary and server API URL summary
        if (preferenceKey.equalsIgnoreCase(getString(R.string.preference_key_region))
                || preferenceKey.equalsIgnoreCase(getString(R.string.preference_key_oba_api_url))) {
            if (Application.get().getCurrentRegion() != null) {
                regionPref.setSummary(getString(R.string.preferences_region_summary,
                        Application.get().getCurrentRegion().getName()));
                customApiUrlPref
                        .setSummary(getString(R.string.preferences_oba_api_servername_summary));
            } else {
                regionPref.setSummary(getString(R.string.preferences_region_summary_custom_api));
                customApiUrlPref.setSummary(Application.get().getCustomApiUrl());
            }
        } else if (preferenceKey
                .equalsIgnoreCase(getString(R.string.preference_key_preferred_units))) {
            preferredUnits.setSummary(preferredUnits.getValue());
        }
    }

    @Override
    public boolean onPreferenceClick(Preference pref) {
        if (pref.equals(regionPref)) {
            RegionsActivity.start(this);
        } else if (pref.equals(donatePref)) {
            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    getString(R.string.analytics_action_button_press),
                    getString(R.string.analytics_label_button_press_donate));
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.donate_url)));
            startActivity(intent);
        }
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.equals(customApiUrlPref) && newValue instanceof String) {
            String apiUrl = (String) newValue;

            if (!TextUtils.isEmpty(apiUrl)) {
                //User entered a custom API Url, so set the region info to null
                Application.get().setCurrentRegion(null);
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "User entered new API URL, set region to null.");
                }
            } else {
                //User cleared the API URL preference value, so re-initialize regions
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "User entered blank API URL, re-initializing regions...");
                }
                NavHelp.goHome(this);
            }
        } else if (preference.equals(analyticsPref) && newValue instanceof Boolean) {
            Boolean isAnalyticsActive = (Boolean) newValue;
            //Report if the analytics turns off, just before shared preference changed
            if (!isAnalyticsActive) {
                ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.APP_SETTINGS.toString(),
                        getString(R.string.analytics_action_edit_general),
                        getString(R.string.analytics_label_analytic_preference)
                                + (isAnalyticsActive ? "YES" : "NO"));
                GoogleAnalytics.getInstance(getBaseContext()).dispatchLocalHits();
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        SharedPreferences settings = Application.getPrefs();
        boolean currentValue = settings
                .getBoolean(getString(R.string.preference_key_auto_select_region), true);

        //If the use has selected to auto-select region, and the previous state of the setting was false, 
        //then run the auto-select by going to HomeActivity
        if (currentValue && !autoSelectInitialValue) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG,
                        "User re-enabled auto-select regions pref, auto-selecting via Home Activity...");
            }
            NavHelp.goHome(this);
        }
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        SharedPreferences settings = Application.getPrefs();
        // Listening to changes to a custom Preference doesn't seem to work, so we can listen to changes to the shared pref value instead
        if (key.equals(getString(R.string.preference_key_experimental_regions))) {
            boolean experimentalServers = settings
                    .getBoolean(getString(R.string.preference_key_experimental_regions), false);
            if (BuildConfig.DEBUG) {
                Log.d(TAG,
                        "Experimental regions shared preference changed to " + experimentalServers);
            }

            /*
            Force a refresh of the regions list, but don't using blocking progress dialog
            inside the ObaRegionsTask AsyncTask.
            We need to use our own Action Bar progress bar here so its linked to this activity,
            which will survive orientation changes.
            */
            setSupportProgressBarIndeterminateVisibility(true);
            ObaRegionsTask task = new ObaRegionsTask(this, this, true, false);
            task.execute();

            // Wait to change the region preference description until the task callback
            //Analytics
            if (experimentalServers) {
                ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                        getString(R.string.analytics_action_button_press),
                        getString(R.string.analytics_label_button_press_experimental_on));
            } else {
                ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                        getString(R.string.analytics_action_button_press),
                        getString(R.string.analytics_label_button_press_experimental_off));
            }
        } else if (key.equals(getString(R.string.preference_key_oba_api_url))) {
            // Change the region preference description to show we're not using a region
            changePreferenceSummary(key);
        } else if (key.equalsIgnoreCase(getString(R.string.preference_key_preferred_units))) {
            // Change the preferred units description
            changePreferenceSummary(key);
        } else if (key.equalsIgnoreCase(getString(R.string.preference_key_auto_select_region))) {
            //Analytics
            boolean autoSelect = settings
                    .getBoolean(getString(R.string.preference_key_auto_select_region), false);
            if (autoSelect) {
                ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                        getString(R.string.analytics_action_button_press),
                        getString(R.string.analytics_label_button_press_auto));
            } else {
                ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                        getString(R.string.analytics_action_button_press),
                        getString(R.string.analytics_label_button_press_manual));
            }
        } else if (key.equalsIgnoreCase(getString(R.string.preferences_key_analytics))) {
            Boolean isAnalyticsActive = settings.getBoolean(Application.get().
                    getString(R.string.preferences_key_analytics), Boolean.FALSE);
            //Report if the analytics turns on, just after shared preference changed
            if (isAnalyticsActive)
                ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.APP_SETTINGS.toString(),
                        getString(R.string.analytics_action_edit_general),
                        getString(R.string.analytics_label_analytic_preference)
                                + (isAnalyticsActive ? "YES" : "NO"));
        }
    }

    //
    // Region Task Callback
    //
    public void onTaskFinished(boolean currentRegionChanged) {
        setSupportProgressBarIndeterminateVisibility(false);

        if (currentRegionChanged) {
            // If region was auto-selected, show user the region we're using
            if (Application.getPrefs()
                    .getBoolean(getString(R.string.preference_key_auto_select_region), true)
                    && Application.get().getCurrentRegion() != null) {
                Toast.makeText(this,
                        getString(R.string.region_region_found,
                                Application.get().getCurrentRegion().getName()),
                        Toast.LENGTH_LONG
                ).show();
            }

            // Update the preference summary to show the newly selected region
            changePreferenceSummary(getString(R.string.preference_key_region));

            // Since the current region was updated as a result of enabling/disabling experimental servers, go home
            NavHelp.goHome(this);
        }
    }
}
