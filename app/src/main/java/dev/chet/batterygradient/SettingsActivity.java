package dev.chet.batterygradient;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

public final class SettingsActivity extends Activity {
    private final GradientBatteryDrawable preview = new GradientBatteryDrawable();
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView statusView;
    private final Runnable refreshStatus = new Runnable() {
        @Override public void run() {
            if (statusView != null) statusView.setText("System UI: "
                    + SettingsProvider.getStatus(SettingsActivity.this));
            main.postDelayed(this, 2000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(248, 248, 247));
        setContentView(root);

        TextView title = text("Battery Gradient", 24);
        root.addView(title);
        TextView detail = text("Select your status bar battery style. The colour changes "
                + "continuously from red at 0% through amber to green at 100%.", 15);
        detail.setPadding(0, dp(10), 0, dp(16));
        root.addView(detail);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(preview);
        LinearLayout.LayoutParams iconLayout = new LinearLayout.LayoutParams(dp(108), dp(108));
        iconLayout.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(icon, iconLayout);

        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = battery == null ? 75 : Math.round(100f *
                battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 75) /
                Math.max(1, battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100)));
        preview.setLevelPercent(level);

        TextView reading = text("Preview: " + level + "%", 15);
        reading.setGravity(Gravity.CENTER);
        root.addView(reading);
        SeekBar slider = new SeekBar(this);
        slider.setMax(100);
        slider.setProgress(level);
        root.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                preview.setLevelPercent(value);
                reading.setText("Preview: " + value + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });

        TextView selector = text("Status bar style", 18);
        selector.setPadding(0, dp(20), 0, dp(8));
        root.addView(selector);
        RadioGroup styles = new RadioGroup(this);
        styles.setOrientation(RadioGroup.VERTICAL);
        String[] names = {"Filled circle", "Dashed circle", "Circle"};
        String[] values = {SettingsProvider.FILLED, SettingsProvider.DASHED,
                SettingsProvider.CIRCLE};
        String selected = SettingsProvider.getStyle(this);
        for (int i = 0; i < names.length; i++) {
            RadioButton button = new RadioButton(this);
            button.setId(View.generateViewId());
            button.setText(names[i]);
            button.setTextSize(17);
            button.setPadding(0, dp(5), 0, dp(5));
            styles.addView(button);
            if (values[i].equals(selected)) button.setChecked(true);
            final String value = values[i];
            button.setOnClickListener(v -> {
                preview.setStyle(value);
                getContentResolver().call(SettingsProvider.URI, "set", value, null);
            });
        }
        preview.setStyle(selected);
        root.addView(styles);
        TextView note = text("Enable Battery Gradient in LSPosed and scope it to System UI. "
                + "Restart System UI once after enabling the module. Style changes apply live.", 14);
        note.setPadding(0, dp(24), 0, 0);
        root.addView(note);
        statusView = text("System UI: Checking module status…", 14);
        statusView.setPadding(0, dp(20), 0, 0);
        root.addView(statusView);
    }

    @Override protected void onResume() {
        super.onResume();
        main.removeCallbacks(refreshStatus);
        main.post(refreshStatus);
    }

    @Override protected void onPause() {
        main.removeCallbacks(refreshStatus);
        super.onPause();
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(32, 38, 42));
        return view;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
