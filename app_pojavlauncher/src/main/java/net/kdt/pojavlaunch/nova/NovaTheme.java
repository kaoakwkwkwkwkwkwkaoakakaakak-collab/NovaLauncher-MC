package net.kdt.pojavlaunch.nova;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.Window;
import net.kdt.pojavlaunch.Tools;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import git.artdeell.mojo.R;
public final class NovaTheme {

    private static final String TAG = "NovaTheme";

    private static final String BACKGROUND_NAME = "nova_background.png";

    public static final String[] ACCENT_VALUES = {
            "default", "blue", "green", "purple", "orange", "red", "pink", "cyan", "amber"
    };

    private static final int[] ACCENT_COLORS = {
            0xFF91DFFB,
            0xFF4A9EFF,
            0xFF4CD964,
            0xFFAF7AFF,
            0xFFFF9F43,
            0xFFFF5C5C,
            0xFFFF6FA5,
            0xFF23D5D5,
            0xFFFFC542
    };

    private NovaTheme() {}

    public static int accentColor(Context context) {
        String value = accentValue();
        for (int i = 0; i < ACCENT_VALUES.length; i++) {
            if (ACCENT_VALUES[i].equals(value)) return ACCENT_COLORS[i];
        }
        if (context != null) {
            return context.getResources().getColor(R.color.minebutton_color);
        }
        return ACCENT_COLORS[0];
    }

    private static String accentValue() {
        android.content.SharedPreferences prefs =
                net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF;
        if (prefs == null) return "default";
        return prefs.getString(NovaPrefs.KEY_THEME_ACCENT, "default");
    }

    public static boolean isDynamicColorEnabled() {
        return NovaPrefs.isOn(NovaPrefs.KEY_THEME_DYNAMIC)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static int resolveAccent(Context context) {
        if (isDynamicColorEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return context.getResources().getColor(
                        android.R.color.system_accent1_200, context.getTheme());
            } catch (Throwable ignored) {
            }
        }
        return accentColor(context);
    }

    public static float uiScale() {
        android.content.SharedPreferences prefs =
                net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF;
        if (prefs == null) return 1f;
        int percent = prefs.getInt(NovaPrefs.KEY_THEME_UI_SCALE, 100);
        if (percent < 70) percent = 70;
        if (percent > 150) percent = 150;
        return percent / 100f;
    }

    public static void applyUiScale(Activity activity) {
        float scale = uiScale();
        if (Math.abs(scale - 1f) < 0.01f) return;
        android.content.res.Configuration configuration =
                new android.content.res.Configuration(activity.getResources().getConfiguration());
        configuration.fontScale = scale;
        activity.applyOverrideConfiguration(configuration);
    }

    public static File backgroundFile() {
        return new File(Tools.DIR_DATA, BACKGROUND_NAME);
    }

    public static boolean hasBackground() {
        return NovaPrefs.isOn(NovaPrefs.KEY_THEME_BACKGROUND) && backgroundFile().isFile();
    }

    public static void saveBackground(Context context, Uri source) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) throw new IOException("Cannot read image");
            File target = backgroundFile();
            try (OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
        }
        NovaPrefs.set(NovaPrefs.KEY_THEME_BACKGROUND, true);
    }

    public static void clearBackground() {
        File file = backgroundFile();
        if (file.isFile() && !file.delete()) Log.w(TAG, "Could not delete background");
        NovaPrefs.set(NovaPrefs.KEY_THEME_BACKGROUND, false);
    }

    public static void applyBackground(Activity activity, View root) {
        if (activity == null || root == null) return;
        if (!hasBackground()) return;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(backgroundFile().getAbsolutePath(), options);
            int targetWidth = Math.max(1, root.getWidth() > 0
                    ? root.getWidth() : activity.getResources().getDisplayMetrics().widthPixels);
            int sample = 1;
            while (options.outWidth / sample > targetWidth * 2) sample *= 2;
            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeFile(backgroundFile().getAbsolutePath(), decode);
            if (bitmap == null) return;
            Drawable drawable = new BitmapDrawable(activity.getResources(), bitmap);
            drawable.setAlpha(backgroundAlpha());
            root.setBackground(drawable);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to apply background", t);
        }
    }

    private static int backgroundAlpha() {
        android.content.SharedPreferences prefs =
                net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF;
        if (prefs == null) return 255;
        int percent = prefs.getInt(NovaPrefs.KEY_THEME_BACKGROUND_ALPHA, 100);
        if (percent < 10) percent = 10;
        if (percent > 100) percent = 100;
        return (int) (percent * 2.55f);
    }

    public static void applyStatusBar(Activity activity) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null) return;
        int accent = resolveAccent(activity);
        int dark = darken(accent, 0.75f);
        try {
            window.setStatusBarColor(dark);
        } catch (Throwable ignored) {
        }
    }

    public static int darken(int color, float factor) {
        int r = (int) (Color.red(color) * factor);
        int g = (int) (Color.green(color) * factor);
        int b = (int) (Color.blue(color) * factor);
        return Color.argb(Color.alpha(color), r, g, b);
    }
}
