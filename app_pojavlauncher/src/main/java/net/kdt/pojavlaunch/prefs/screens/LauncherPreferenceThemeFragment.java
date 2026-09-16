package net.kdt.pojavlaunch.prefs.screens;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;
import net.kdt.pojavlaunch.nova.NovaTheme;
import java.io.IOException;
import git.artdeell.mojo.R;
public class LauncherPreferenceThemeFragment extends LauncherPreferenceFragment {

    private final ActivityResultLauncher<String> mPickImage = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::onImagePicked);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_theme);
        Preference dynamic = findPreference("themeDynamic");
        if (dynamic != null && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            dynamic.setEnabled(false);
            dynamic.setSummary(R.string.nova_theme_dynamic_unsupported);
        }
        Preference pick = findPreference("themePickBackground");
        if (pick != null) {
            pick.setOnPreferenceClickListener(p -> {
                mPickImage.launch("image/*");
                return true;
            });
        }
        Preference clear = findPreference("themeClearBackground");
        if (clear != null) {
            clear.setOnPreferenceClickListener(p -> {
                NovaTheme.clearBackground();
                Toast.makeText(requireContext(), R.string.nova_theme_cleared,
                        Toast.LENGTH_SHORT).show();
                return true;
            });
        }
    }

    private void onImagePicked(Uri uri) {
        if (uri == null || !isAdded()) return;
        try {
            NovaTheme.saveBackground(requireContext().getApplicationContext(), uri);
            Toast.makeText(requireContext(), R.string.nova_theme_bg_saved,
                    Toast.LENGTH_SHORT).show();
            Preference toggle = findPreference("themeBackground");
            if (toggle instanceof androidx.preference.SwitchPreference) {
                ((androidx.preference.SwitchPreference) toggle).setChecked(true);
            }
        } catch (IOException e) {
            Toast.makeText(requireContext(),
                    getString(R.string.nova_theme_bg_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }
}
