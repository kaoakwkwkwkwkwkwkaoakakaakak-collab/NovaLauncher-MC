package net.kdt.pojavlaunch.prefs.screens;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;
import android.widget.EditText;

import androidx.preference.SwitchPreference;
import androidx.preference.Preference;

import net.kdt.pojavlaunch.nova.NovaAI;
import net.kdt.pojavlaunch.nova.NovaOptimizer;
import net.kdt.pojavlaunch.nova.NovaPrefs;
import net.kdt.pojavlaunch.utils.GLInfoUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import git.artdeell.mojo.R;

public class LauncherPreferenceExperimentalFragment extends LauncherPreferenceFragment {

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_experimental);

        SwitchPreference pref = requirePreference("freedrenoSysmem", SwitchPreference.class);
        boolean hasFreedreno = GLInfoUtils.getGlInfo().isAdreno();
        pref.setVisible(hasFreedreno);

        requirePreference("novaAiOptimizer").setOnPreferenceClickListener(p -> {
            runOptimizer();
            return true;
        });

        Preference keyPref = requirePreference("novaAiKey");
        keyPref.setOnPreferenceClickListener(p -> {
            promptForKey(keyPref);
            return true;
        });
        refreshKeySummary(keyPref);

    }



    private void runOptimizer() {
        final Context context = getContext();
        if (context == null) return;

        if (!NovaAI.hasKey()) {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.nova_ai_optimizer_title)
                    .setMessage(R.string.nova_ai_no_key)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        final TextView body = new TextView(context);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad, pad, pad);
        body.setText(R.string.nova_ai_optimizer_running);
        body.setMovementMethod(new ScrollingMovementMethod());

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.nova_ai_optimizer_title)
                .setView(body)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();

        POOL.execute(() -> {
            final NovaOptimizer.Plan plan = NovaOptimizer.analyse(context, null);
            MAIN.post(() -> {
                if (!dialog.isShowing()) return;
                body.setText(NovaOptimizer.render(plan));
                if (plan.ok()) {
                    dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                            getString(R.string.nova_ai_optimizer_apply), (d, w) -> {
                                NovaOptimizer.apply(plan);
                                if (getActivity() != null) {
                                    net.kdt.pojavlaunch.prefs.LauncherPreferences
                                            .loadPreferences(getActivity());
                                }
                            });
                    dialog.show();
                }
            });
        });
    }

    private void refreshKeySummary(Preference keyPref) {
        String stored = NovaPrefs.getApiKey();
        boolean custom = stored != null && !stored.trim().isEmpty();
        keyPref.setSummary(custom
                ? getString(R.string.nova_ai_key_custom)
                : getString(R.string.nova_ai_key_default));
    }

    private void promptForKey(Preference keyPref) {
        Context context = getContext();
        if (context == null) return;

        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.nova_ai_key_hint);
        input.setText(NovaPrefs.getApiKey());
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(context)
                .setTitle(R.string.nova_ai_key_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    NovaPrefs.setApiKey(input.getText().toString().trim());
                    refreshKeySummary(keyPref);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
