package net.kdt.pojavlaunch.nova;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import net.kdt.pojavlaunch.BaseActivity;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import git.artdeell.mojo.R;
public class NovaModsActivity extends BaseActivity {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private LinearLayout mList;

    private ProgressBar mSpinner;

    private TextView mStatus;

    private Instance mInstance;

    private long mQueryToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mInstance = Instances.loadSelectedInstance();
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * d);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0E13);
        root.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(this);
        title.setText(R.string.nova_mods_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        root.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setTextColor(0xFFC3C6CF);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subtitle.setText(mInstance == null
                ? getString(R.string.nova_mods_no_instance)
                : mInstance.name + "  ·  " + safeVersion());
        subtitle.setPadding(0, (int) (2 * d), 0, pad);
        root.addView(subtitle);
        final EditText search = new EditText(this);
        search.setHint(R.string.nova_mods_search);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xFF8D909A);
        search.setPadding(pad, (int) (12 * d), pad, (int) (12 * d));
        GradientDrawable field = new GradientDrawable();
        field.setCornerRadius(18 * d);
        field.setColor(0x14FFFFFF);
        field.setStroke((int) d, 0x22FFFFFF);
        search.setBackground(field);
        root.addView(search);
        mSpinner = new ProgressBar(this);
        mSpinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.gravity = Gravity.CENTER_HORIZONTAL;
        sp.topMargin = pad;
        mSpinner.setLayoutParams(sp);
        root.addView(mSpinner);
        mStatus = new TextView(this);
        mStatus.setTextColor(0xFF8D909A);
        mStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mStatus.setPadding(0, pad, 0, 0);
        mStatus.setVisibility(View.GONE);
        root.addView(mStatus);
        ScrollView scroller = new ScrollView(this);
        scroller.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        mList = new LinearLayout(this);
        mList.setOrientation(LinearLayout.VERTICAL);
        mList.setPadding(0, pad, 0, 0);
        scroller.addView(mList);
        root.addView(scroller);
        setContentView(root);
        search.addTextChangedListener(new TextWatcher() {

            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                final String query = e.toString();
                final long token = ++mQueryToken;
                MAIN.postDelayed(() -> {
                    if (token == mQueryToken) runSearch(query);
                }, 350);
            }
        });
        runSearch("");
    }

    private String safeVersion() {
        try {
            return mInstance.versionId == null ? "" : mInstance.versionId;
        } catch (Throwable t) {
            return "";
        }
    }

    private String loaderId() {
        if (mInstance == null) return null;
        try {
            String v = mInstance.versionId;
            if (v == null) return null;
            String lower = v.toLowerCase();
            if (lower.contains("fabric")) return "fabric";
            if (lower.contains("quilt")) return "quilt";
            if (lower.contains("neoforge")) return "neoforge";
            if (lower.contains("forge")) return "forge";
        } catch (Throwable ignored) {}
        return null;
    }

    private String gameVersion() {
        if (mInstance == null) return null;
        try {
            String v = mInstance.versionId;
            if (v == null) return null;
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("(1\\.\\d+(\\.\\d+)?)").matcher(v);
            if (m.find()) return m.group(1);
        } catch (Throwable ignored) {}
        return null;
    }

    private void runSearch(final String query) {
        mSpinner.setVisibility(View.VISIBLE);
        mStatus.setVisibility(View.GONE);
        mList.removeAllViews();
        final String version = gameVersion();
        final String loader = loaderId();
        POOL.execute(() -> {
            List<NovaMods.Mod> results = null;
            String error = null;
            try {
                results = NovaMods.search(query, version, loader);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final List<NovaMods.Mod> finalResults = results;
            final String finalError = error;
            MAIN.post(() -> {
                mSpinner.setVisibility(View.GONE);
                if (finalError != null) {
                    mStatus.setText(finalError);
                    mStatus.setVisibility(View.VISIBLE);
                    return;
                }
                if (finalResults == null || finalResults.isEmpty()) {
                    mStatus.setText(R.string.nova_mods_empty);
                    mStatus.setVisibility(View.VISIBLE);
                    return;
                }
                for (NovaMods.Mod mod : finalResults) {
                    mList.addView(buildRow(mod, version, loader));
                }
            });
        });
    }

    private View buildRow(final NovaMods.Mod mod, final String version, final String loader) {
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (14 * d);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20 * d);
        bg.setColor(0x12FFFFFF);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (10 * d);
        card.setLayoutParams(lp);
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = new TextView(this);
        name.setText(mod.title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        name.setSingleLine(true);
        text.addView(name);
        TextView desc = new TextView(this);
        desc.setText(mod.description);
        desc.setTextColor(0xFFC3C6CF);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        desc.setMaxLines(2);
        text.addView(desc);
        TextView meta = new TextView(this);
        meta.setText(mod.downloadsLabel());
        meta.setTextColor(0xFF8D909A);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        meta.setPadding(0, (int) (4 * d), 0, 0);
        text.addView(meta);
        card.addView(text);
        final TextView action = new TextView(this);
        action.setText(R.string.nova_mods_install);
        action.setTextColor(0xFF0A2478);
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        action.setPadding((int) (16 * d), (int) (8 * d), (int) (16 * d), (int) (8 * d));
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(20 * d);
        pill.setColor(0xFFAFC2FF);
        action.setBackground(pill);
        card.addView(action);
        action.setOnClickListener(v -> {
            if (mInstance == null) {
                Toast.makeText(this, R.string.nova_mods_no_instance, Toast.LENGTH_SHORT).show();
                return;
            }
            action.setEnabled(false);
            Toast.makeText(this,
                    getString(R.string.nova_mods_installing, mod.title),
                    Toast.LENGTH_SHORT).show();
            POOL.execute(() -> {
                boolean ok = false;
                String failure = null;
                try {
                    NovaMods.ModFile file = NovaMods.resolveFile(mod.projectId, version, loader);
                    if (file != null) {
                        NovaMods.install(file, mInstance.getGameDirectory());
                        ok = true;
                    } else {
                        failure = "No compatible file";
                    }
                } catch (Exception e) {
                    failure = e.getMessage();
                }
                final boolean done = ok;
                final String err = failure;
                MAIN.post(() -> {
                    if (done) {
                        action.setText(R.string.nova_mods_installed);
                        GradientDrawable grey = new GradientDrawable();
                        grey.setCornerRadius(20 * getResources().getDisplayMetrics().density);
                        grey.setColor(0x33FFFFFF);
                        action.setBackground(grey);
                        action.setTextColor(0xFFC3C6CF);
                        Toast.makeText(this,
                                getString(R.string.nova_mods_done, mod.title),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        action.setEnabled(true);
                        Toast.makeText(this,
                                err != null ? err : getString(R.string.nova_mods_failed, mod.title),
                                Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
        return card;
    }
}
