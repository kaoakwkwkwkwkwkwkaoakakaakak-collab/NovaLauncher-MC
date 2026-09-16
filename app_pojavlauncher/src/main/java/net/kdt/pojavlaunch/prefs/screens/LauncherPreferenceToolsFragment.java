package net.kdt.pojavlaunch.prefs.screens;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.nova.NovaBackup;
import net.kdt.pojavlaunch.nova.NovaLanScanner;
import net.kdt.pojavlaunch.nova.NovaLogUpload;
import java.io.File;
import java.util.List;
import git.artdeell.mojo.R;
public class LauncherPreferenceToolsFragment extends LauncherPreferenceFragment {

    private final ActivityResultLauncher<String> mCreateBackup = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"), this::onExportTarget);

    private final ActivityResultLauncher<String[]> mOpenBackup = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onImportSource);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_tools);
        bind("toolsUploadLog", this::uploadLog);
        bind("toolsExport", () -> mCreateBackup.launch(NovaBackup.defaultFileName()));
        bind("toolsImport", () -> mOpenBackup.launch(new String[]{"application/zip"}));
        bind("toolsLanScan", this::scanLan);
    }

    private void bind(String key, Runnable action) {
        Preference preference = findPreference(key);
        if (preference == null) return;
        preference.setOnPreferenceClickListener(p -> {
            action.run();
            return true;
        });
    }

    private void uploadLog() {
        Context context = requireContext().getApplicationContext();
        Toast.makeText(context, R.string.nova_tools_uploading, Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            String message;
            String url = null;
            try {
                File log = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
                url = NovaLogUpload.uploadFile(log);
                message = url;
            } catch (Exception e) {
                message = getString(R.string.nova_tools_upload_failed, String.valueOf(e.getMessage()));
            }
            String finalMessage = message;
            String finalUrl = url;
            postToUi(() -> {
                if (finalUrl != null) {
                    ClipboardManager clipboard = (ClipboardManager)
                            context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("mclo.gs", finalUrl));
                    }
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.nova_tools_upload_title)
                            .setMessage(getString(R.string.nova_tools_upload_done, finalUrl))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                } else {
                    Toast.makeText(context, finalMessage, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void onExportTarget(Uri uri) {
        if (uri == null) return;
        Context context = requireContext().getApplicationContext();
        Toast.makeText(context, R.string.nova_tools_exporting, Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            String result;
            try {
                NovaBackup.export(context, uri, null);
                result = getString(R.string.nova_tools_export_done);
            } catch (Exception e) {
                result = getString(R.string.nova_tools_export_failed, String.valueOf(e.getMessage()));
            }
            String finalResult = result;
            postToUi(() -> Toast.makeText(context, finalResult, Toast.LENGTH_LONG).show());
        });
    }

    private void onImportSource(Uri uri) {
        if (uri == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.nova_tools_import_title)
                .setMessage(R.string.nova_tools_import_confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> doImport(uri))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doImport(Uri uri) {
        Context context = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            String result;
            try {
                NovaBackup.restore(context, uri, null);
                result = getString(R.string.nova_tools_import_done);
            } catch (Exception e) {
                result = getString(R.string.nova_tools_import_failed, String.valueOf(e.getMessage()));
            }
            String finalResult = result;
            postToUi(() -> Toast.makeText(context, finalResult, Toast.LENGTH_LONG).show());
        });
    }

    private void scanLan() {
        Context context = requireContext().getApplicationContext();
        Toast.makeText(context, R.string.nova_tools_lan_scanning, Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            List<NovaLanScanner.LanServer> servers = NovaLanScanner.scan(5000, null);
            postToUi(() -> {
                if (!isAdded()) return;
                if (servers.isEmpty()) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.nova_tools_lan_title)
                            .setMessage(R.string.nova_tools_lan_none)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }
                CharSequence[] items = new CharSequence[servers.size()];
                for (int i = 0; i < servers.size(); i++) {
                    items[i] = servers.get(i).displayName() + "\n" + servers.get(i).connectAddress();
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.nova_tools_lan_found)
                        .setItems(items, (d, which) -> {
                            String address = servers.get(which).connectAddress();
                            ClipboardManager clipboard = (ClipboardManager)
                                    context.getSystemService(Context.CLIPBOARD_SERVICE);
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(ClipData.newPlainText("server", address));
                            }
                            Toast.makeText(context,
                                    getString(R.string.nova_tools_lan_copied, address),
                                    Toast.LENGTH_LONG).show();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        });
    }

    private void postToUi(Runnable runnable) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (isAdded()) runnable.run();
        });
    }
}
