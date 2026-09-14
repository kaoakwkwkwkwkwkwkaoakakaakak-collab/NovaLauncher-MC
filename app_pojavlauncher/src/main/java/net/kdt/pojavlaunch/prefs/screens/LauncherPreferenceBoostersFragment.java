package net.kdt.pojavlaunch.prefs.screens;

import android.os.Bundle;

import androidx.preference.PreferenceCategory;

import net.kdt.pojavlaunch.nova.NovaBoost;

import git.artdeell.mojo.R;

public class LauncherPreferenceBoostersFragment extends LauncherPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_boosters);
        updateSummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSummary();
    }

    @Override
    public void onSharedPreferenceChanged(android.content.SharedPreferences prefs, String key) {
        super.onSharedPreferenceChanged(prefs, key);
        updateSummary();
    }

    private void updateSummary() {
        PreferenceCategory category = findPreference("boostToggles");
        if (category == null) return;
        int active = NovaBoost.activeCount();
        category.setTitle(getString(R.string.nova_boosters_toggles_count, active));
    }
}
