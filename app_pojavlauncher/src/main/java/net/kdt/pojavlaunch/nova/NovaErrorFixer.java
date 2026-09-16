package net.kdt.pojavlaunch.nova;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import git.artdeell.mojo.R;
public final class NovaErrorFixer {

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final int MAX_LOG = 6000;

    private NovaErrorFixer() {}

    public static void diagnose(Activity activity, String errorText, Throwable throwable) {
        if (activity == null || activity.isFinishing()) return;
        if (!NovaAI.hasKey()) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.nova_ai_fixer_title)
                    .setMessage(R.string.nova_ai_no_key)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        final TextView body = new TextView(activity);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad, pad, pad);
        body.setText(R.string.nova_ai_working);
        body.setMovementMethod(new ScrollingMovementMethod());
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.nova_ai_fixer_title)
                .setView(body)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.show();
        final String prompt = buildPrompt(activity, errorText, throwable);
        POOL.execute(() -> {
            final NovaAI.Reply reply = NovaAI.ask(prompt);
            MAIN.post(() -> {
                if (activity.isFinishing() || !dialog.isShowing()) return;
                body.setText(reply.ok() ? reply.content : reply.error);
            });
        });
    }

    private static String buildPrompt(Context context, String errorText, Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append("A Minecraft Java Edition launcher on Android hit an error.\n");
        sb.append("Explain the cause in plain language and give numbered, concrete steps to fix it.\n");
        sb.append("Be brief. Do not invent settings that do not exist.\n");
        sb.append("Useful settings the user can change: renderer (holy-gl4es, Zink, Freedreno, ");
        sb.append("MobileGlues), RAM allocation, Java version, FPS Booster toggles.\n\n");
        sb.append(NovaAI.describeDevice(context)).append('\n');
        sb.append("Renderer in use: ").append(
                net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_RENDERER).append('\n');
        sb.append("RAM allocated: ").append(
                net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_RAM_ALLOCATION).append(" MB\n\n");
        if (errorText != null && !errorText.isEmpty()) {
            sb.append("Error:\n").append(clip(errorText)).append('\n');
        }
        if (throwable != null) {
            sb.append("\nStack trace:\n").append(clip(stackToString(throwable)));
        }
        return sb.toString();
    }

    private static String stackToString(Throwable throwable) {
        java.io.StringWriter writer = new java.io.StringWriter();
        throwable.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString();
    }

    private static String clip(String value) {
        if (value == null) return "";
        if (value.length() <= MAX_LOG) return value;
        return value.substring(value.length() - MAX_LOG);
    }
}
