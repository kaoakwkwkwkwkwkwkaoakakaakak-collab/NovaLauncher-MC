package net.kdt.pojavlaunch.prefs.screens;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.nova.NovaShizuku;

import git.artdeell.mojo.R;

public class LauncherPreferenceShizukuFragment extends LauncherPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_shizuku);

        Preference status = findPreference("shizukuStatus");
        if (status != null) {
            status.setOnPreferenceClickListener(p -> {
                if (!NovaShizuku.isBinderAlive()) {
                    Toast.makeText(requireContext(), R.string.nova_shizuku_not_running,
                            Toast.LENGTH_LONG).show();
                } else if (!NovaShizuku.hasPermission()) {
                    NovaShizuku.requestPermission();
                }
                refreshStatus();
                return true;
            });
        }

        Preference grant = findPreference("shizukuGrant");
        if (grant != null) {
            grant.setOnPreferenceClickListener(p -> {
                if (!NovaShizuku.hasPermission()) {
                    NovaShizuku.requestPermission();
                    return true;
                }
                PojavApplication.sExecutorService.execute(() -> {
                    boolean ok = NovaShizuku.grantLauncherPermissions(requireContext().getApplicationContext());
                    if (isAdded()) requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(),
                                    ok ? R.string.nova_shizuku_grant_ok : R.string.nova_shizuku_grant_fail,
                                    Toast.LENGTH_LONG).show());
                });
                return true;
            });
        }

        refreshStatus();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        Preference status = findPreference("shizukuStatus");
        if (status == null || !isAdded()) return;
        int summary;
        if (!NovaShizuku.isInstalled(requireContext())) {
            summary = R.string.nova_shizuku_not_installed;
        } else if (!NovaShizuku.isBinderAlive()) {
            summary = R.string.nova_shizuku_not_running;
        } else if (!NovaShizuku.hasPermission()) {
            summary = R.string.nova_shizuku_no_permission;
        } else {
            summary = R.string.nova_shizuku_ready;
        }
        status.setSummary(summary);
    }
}
