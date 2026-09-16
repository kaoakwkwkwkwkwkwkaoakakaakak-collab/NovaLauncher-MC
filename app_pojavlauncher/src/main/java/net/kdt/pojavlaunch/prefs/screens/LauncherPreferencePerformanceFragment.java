package net.kdt.pojavlaunch.prefs.screens;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import net.kdt.pojavlaunch.nova.NovaProfiles;
import net.kdt.pojavlaunch.nova.NovaStats;

import java.util.List;

import git.artdeell.mojo.R;

public class LauncherPreferencePerformanceFragment extends LauncherPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_performance);

        Preference apply = findPreference("perfApply");
        if (apply != null) {
            apply.setOnPreferenceClickListener(p -> {
                confirmApply();
                return true;
            });
        }

        Preference versions = findPreference("statsPerVersion");
        if (versions != null) {
            versions.setOnPreferenceClickListener(p -> {
                showPerVersion();
                return true;
            });
        }

        Preference reset = findPreference("statsReset");
        if (reset != null) {
            reset.setOnPreferenceClickListener(p -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.nova_stats_reset_title)
                        .setMessage(R.string.nova_stats_reset_confirm)
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            NovaStats.reset(requireContext().getApplicationContext());
                            refreshStats();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            });
        }

        refreshStats();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStats();
    }

    private void confirmApply() {
        Context context = requireContext().getApplicationContext();
        String profile = selectedProfile();
        int ram = NovaProfiles.recommendedRam(context, profile);
        String renderer = NovaProfiles.recommendedRenderer(context, profile);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.nova_profile_apply_title)
                .setMessage(getString(R.string.nova_profile_apply_confirm,
                        getString(NovaProfiles.displayNameRes(profile)),
                        ram,
                        renderer == null ? "-" : renderer))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    NovaProfiles.apply(context, profile);
                    Toast.makeText(context, R.string.nova_profile_applied,
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String selectedProfile() {
        Preference preference = findPreference("perfProfile");
        if (preference instanceof ListPreference) {
            String value = ((ListPreference) preference).getValue();
            if (value != null) return value;
        }
        return NovaProfiles.current();
    }

    private void showPerVersion() {
        Context context = requireContext().getApplicationContext();
        List<NovaStats.VersionTime> times = NovaStats.perVersion(context);
        if (times.isEmpty()) {
            Toast.makeText(context, R.string.nova_stats_none, Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] items = new CharSequence[times.size()];
        for (int i = 0; i < times.size(); i++) {
            NovaStats.VersionTime entry = times.get(i);
            items[i] = entry.version + "  -  " + NovaStats.formatDuration(entry.millis);
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.nova_stats_versions_title)
                .setItems(items, null)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void refreshStats() {
        Preference summary = findPreference("statsSummary");
        if (summary == null || !isAdded()) return;
        Context context = requireContext().getApplicationContext();
        int launches = NovaStats.launchCount(context);
        if (launches == 0) {
            summary.setSummary(R.string.nova_stats_none);
            return;
        }
        summary.setSummary(getString(R.string.nova_stats_summary,
                NovaStats.formatDuration(NovaStats.totalPlayMs(context)),
                launches,
                NovaStats.crashCount(context)));
    }
}
