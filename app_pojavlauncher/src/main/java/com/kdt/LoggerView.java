package com.kdt;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import net.kdt.pojavlaunch.Logger;
import git.artdeell.mojo.R;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public class LoggerView extends ConstraintLayout {

    private static final int MAX_LINES = 3000;
    private static final int FLUSH_MS = 120;

    private static final int COLOR_ERROR = 0xFFFF6B6B;
    private static final int COLOR_WARN = 0xFFFFC866;
    private static final int COLOR_INFO = 0xFFD6D9E0;
    private static final int COLOR_DEBUG = 0xFF8D909A;
    private static final int COLOR_MATCH = 0x66FFD54F;

    private Logger.eventLogListener mLogListener;
    private ToggleButton mLogToggle;
    private DefocusableScrollView mScrollView;
    private HorizontalScrollView mHScroll;
    private TextView mLogTextView;
    private TextView mStats;
    private EditText mSearch;
    private ToggleButton mFilterError;
    private ToggleButton mFilterWarn;
    private ToggleButton mWrap;

    private final Deque<String> mLines = new ArrayDeque<>();
    private final List<String> mPending = new ArrayList<>();
    private String mQuery = "";
    private int mErrorCount;
    private boolean mFlushScheduled;

    private final Runnable mFlush = new Runnable() {
        @Override
        public void run() {
            mFlushScheduled = false;
            synchronized (mPending) {
                if (mPending.isEmpty()) return;
                for (String line : mPending) {
                    mLines.addLast(line);
                    if (isError(line)) mErrorCount++;
                    while (mLines.size() > MAX_LINES) {
                        String dropped = mLines.pollFirst();
                        if (dropped != null && isError(dropped)) mErrorCount--;
                    }
                }
                mPending.clear();
            }
            render();
        }
    };

    public LoggerView(@NonNull Context context) {
        this(context, null);
    }

    public LoggerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        mLogToggle.setChecked(visibility == VISIBLE);
    }

    private void init() {
        inflate(getContext(), R.layout.view_logger, this);

        mLogTextView = findViewById(R.id.content_log_view);
        mLogTextView.setTypeface(Typeface.MONOSPACE);
        mLogTextView.setMaxLines(Integer.MAX_VALUE);
        mLogTextView.setEllipsize(null);
        mLogTextView.setVisibility(GONE);
        mLogTextView.setHorizontallyScrolling(true);

        mStats = findViewById(R.id.log_stats);
        mHScroll = findViewById(R.id.content_log_hscroll);
        mScrollView = findViewById(R.id.content_log_scroll);
        mScrollView.setKeepFocusing(true);

        mLogToggle = findViewById(R.id.content_log_toggle_log);
        mLogToggle.setOnCheckedChangeListener((button, isChecked) -> {
            mLogTextView.setVisibility(isChecked ? VISIBLE : GONE);
            if (isChecked) {
                Logger.setLogListener(mLogListener);
            } else {
                Logger.setLogListener(null);
                synchronized (mPending) {
                    mPending.clear();
                }
                mLines.clear();
                mErrorCount = 0;
                mLogTextView.setText("");
                updateStats();
            }
        });
        mLogToggle.setChecked(false);

        ImageButton cancelButton = findViewById(R.id.log_view_cancel);
        cancelButton.setOnClickListener(view -> LoggerView.this.setVisibility(GONE));

        ImageButton copyButton = findViewById(R.id.log_view_copy);
        copyButton.setOnClickListener(view -> {
            ClipboardManager mgr = (ClipboardManager)
                    getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (mgr != null) {
                mgr.setPrimaryClip(ClipData.newPlainText("log", plainText()));
                Toast.makeText(getContext(), R.string.nova_log_copied, Toast.LENGTH_SHORT).show();
            }
        });

        ImageButton shareButton = findViewById(R.id.log_view_share);
        shareButton.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, plainText());
            getContext().startActivity(Intent.createChooser(intent, null));
        });

        ToggleButton autoscrollToggle = findViewById(R.id.content_log_toggle_autoscroll);
        autoscrollToggle.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) mScrollView.fullScroll(View.FOCUS_DOWN);
            mScrollView.setKeepFocusing(isChecked);
        });
        autoscrollToggle.setChecked(true);

        mFilterError = findViewById(R.id.log_filter_error);
        mFilterWarn = findViewById(R.id.log_filter_warn);
        mFilterError.setOnCheckedChangeListener((b, c) -> render());
        mFilterWarn.setOnCheckedChangeListener((b, c) -> render());

        mWrap = findViewById(R.id.log_filter_wrap);
        mWrap.setOnCheckedChangeListener((b, wrapOn) -> {
            mLogTextView.setHorizontallyScrolling(!wrapOn);
            if (wrapOn) {
                mLogTextView.setLayoutParams(new HorizontalScrollView.LayoutParams(
                        HorizontalScrollView.LayoutParams.MATCH_PARENT,
                        HorizontalScrollView.LayoutParams.WRAP_CONTENT));
            } else {
                mLogTextView.setLayoutParams(new HorizontalScrollView.LayoutParams(
                        HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                        HorizontalScrollView.LayoutParams.WRAP_CONTENT));
            }
            mLogTextView.requestLayout();
        });

        mSearch = findViewById(R.id.log_search);
        mSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                mQuery = e.toString();
                render();
            }
        });

        mLogListener = text -> {
            if (mLogTextView.getVisibility() != VISIBLE) return;
            synchronized (mPending) {
                mPending.add(text);
            }
            if (!mFlushScheduled) {
                mFlushScheduled = true;
                postDelayed(mFlush, FLUSH_MS);
            }
        };

        updateStats();
    }

    private boolean isError(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.contains("ERROR") || upper.contains("SEVERE")
                || upper.contains("FATAL") || upper.contains("EXCEPTION")
                || upper.contains("\tAT ") || upper.startsWith("AT ");
    }

    private boolean isWarn(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.contains("WARN");
    }

    private boolean isDebug(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.contains("DEBUG") || upper.contains("TRACE");
    }

    private int colorFor(String line) {
        if (isError(line)) return COLOR_ERROR;
        if (isWarn(line)) return COLOR_WARN;
        if (isDebug(line)) return COLOR_DEBUG;
        return COLOR_INFO;
    }

    private boolean passes(String line) {
        boolean onlyError = mFilterError.isChecked();
        boolean onlyWarn = mFilterWarn.isChecked();
        if (onlyError || onlyWarn) {
            boolean matched = (onlyError && isError(line)) || (onlyWarn && isWarn(line));
            if (!matched) return false;
        }
        if (mQuery.isEmpty()) return true;
        return line.toLowerCase(Locale.ROOT).contains(mQuery.toLowerCase(Locale.ROOT));
    }

    private void render() {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        String needle = mQuery.toLowerCase(Locale.ROOT);

        for (String line : mLines) {
            if (!passes(line)) continue;
            int start = builder.length();
            builder.append(line).append('\n');
            builder.setSpan(new ForegroundColorSpan(colorFor(line)),
                    start, start + line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            if (!needle.isEmpty()) {
                String hay = line.toLowerCase(Locale.ROOT);
                int from = 0;
                while (true) {
                    int hit = hay.indexOf(needle, from);
                    if (hit < 0) break;
                    builder.setSpan(new android.text.style.BackgroundColorSpan(COLOR_MATCH),
                            start + hit, start + hit + needle.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    from = hit + needle.length();
                }
            }
        }

        mLogTextView.setText(builder);
        updateStats();
        if (mScrollView.isKeepFocusing()) {
            mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void updateStats() {
        if (mStats == null) return;
        mStats.setText(getContext().getString(
                R.string.nova_log_stats, mLines.size(), mErrorCount));
        mStats.setTextColor(mErrorCount > 0 ? COLOR_ERROR : Color.parseColor("#8D909A"));
    }

    private String plainText() {
        StringBuilder sb = new StringBuilder();
        for (String line : mLines) sb.append(line).append('\n');
        return sb.toString();
    }
}
