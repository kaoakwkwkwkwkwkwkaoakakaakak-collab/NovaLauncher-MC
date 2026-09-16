package net.kdt.pojavlaunch.nova;
import android.app.Activity;
import git.artdeell.mojo.R;
public final class NovaTransitions {

    public static final String NONE = "none";

    public static final String SLIDE = "slide";

    public static final String FADE = "fade";

    public static final String ZOOM = "zoom";

    public static final String BOUNCE = "bounce";

    private NovaTransitions() {}

    public static String current() {
        android.content.SharedPreferences prefs =
                net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF;
        if (prefs == null) return SLIDE;
        if (NovaPrefs.isOn(NovaPrefs.KEY_BOOST_ANIM)) return NONE;
        return prefs.getString(NovaPrefs.KEY_THEME_TRANSITION, SLIDE);
    }

    public static int enterAnim() {
        switch (current()) {
            case FADE: return R.anim.nova_fade_in;
            case ZOOM: return R.anim.nova_zoom_in;
            case BOUNCE: return R.anim.nova_bounce_in;
            case NONE: return 0;
            default: return R.anim.nova_slide_in;
        }
    }

    public static int exitAnim() {
        switch (current()) {
            case FADE: return R.anim.nova_fade_out;
            case ZOOM: return R.anim.nova_zoom_out;
            case BOUNCE: return R.anim.nova_bounce_out;
            case NONE: return 0;
            default: return R.anim.nova_slide_out;
        }
    }

    public static void apply(Activity activity) {
        if (activity == null) return;
        int enter = enterAnim();
        int exit = exitAnim();
        activity.overridePendingTransition(enter, exit);
    }
}
