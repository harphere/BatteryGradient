package dev.chet.batterygradient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Hooks only SystemUI's battery container. No resources, network or root calls. */
public final class BatteryModule implements IXposedHookLoadPackage {
    private static final String UI = "com.android.systemui";
    private static final Map<ViewGroup, Holder> HOLDERS = new WeakHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!UI.equals(param.packageName)) return;
        Class<?> battery = XposedHelpers.findClassIfExists(
                "com.android.systemui.battery.BatteryMeterView", param.classLoader);
        if (battery == null) {
            XposedBridge.log("BatteryGradient: BatteryMeterView absent; no hook installed");
            return;
        }
        XposedBridge.hookAllMethods(battery, "onAttachedToWindow", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!(p.thisObject instanceof ViewGroup)) return;
                ViewGroup group = (ViewGroup) p.thisObject;
                group.post(() -> install(group));
            }
        });
        XposedBridge.hookAllMethods(battery, "onDetachedFromWindow", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.thisObject instanceof ViewGroup) uninstall((ViewGroup) p.thisObject);
            }
        });
        // Some ROMs recreate or reveal the native icon when theme/level changes.
        for (String method : new String[] {"updateColors", "onBatteryLevelChanged",
                "updatePercentText", "onDarkChanged"}) {
            try {
                XposedBridge.hookAllMethods(battery, method, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!(p.thisObject instanceof ViewGroup)) return;
                        ViewGroup group = (ViewGroup) p.thisObject;
                        Holder h = HOLDERS.get(group);
                        if (h != null) {
                            h.hideNative();
                            h.icon.setImageDrawable(h.drawable);
                        }
                    }
                });
            } catch (Throwable ignored) { /* ROM-specific method absent. */ }
        }
        XposedBridge.log("BatteryGradient: battery hook ready");
    }

    private static void install(ViewGroup group) {
        try {
            if (!group.isAttachedToWindow() || HOLDERS.containsKey(group)) return;
            // Preserve ROMs with an unexpected empty battery container.
            if (group.getChildCount() == 0) return;
            Holder holder = new Holder(group);
            HOLDERS.put(group, holder);
            holder.attach();
        } catch (Throwable error) {
            XposedBridge.log("BatteryGradient: battery view left unchanged: " + error);
            uninstall(group);
        }
    }

    private static void uninstall(ViewGroup group) {
        Holder holder = HOLDERS.remove(group);
        if (holder != null) holder.detach();
    }

    private static final class Holder {
        final ViewGroup group;
        final ImageView icon;
        final GradientBatteryDrawable drawable = new GradientBatteryDrawable();
        final IdentityHashMap<View, Integer> nativeVisibility = new IdentityHashMap<>();
        final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                int scale = Math.max(1, intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
                int raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                drawable.setLevelPercent(Math.round(raw * 100f / scale));
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
                drawable.setCharging(status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL);
                icon.setContentDescription("Battery " + Math.round(raw * 100f / scale)
                        + " percent" + (status == BatteryManager.BATTERY_STATUS_CHARGING
                        ? ", charging" : ""));
                hideNative();
            }
        };
        final ContentObserver styleObserver = new ContentObserver(MAIN) {
            @Override public void onChange(boolean selfChange) { readStyle(); }
        };
        boolean listening;

        Holder(ViewGroup group) {
            this.group = group;
            icon = new ImageView(group.getContext());
            icon.setImageDrawable(drawable);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        void attach() {
            Context context = group.getContext();
            int px = Math.round(24 * context.getResources().getDisplayMetrics().density);
            if (group instanceof LinearLayout) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px, px);
                lp.gravity = android.view.Gravity.CENTER_VERTICAL;
                group.addView(icon, 0, lp);
            } else {
                group.addView(icon, 0, new ViewGroup.LayoutParams(px, px));
            }
            hideNative();
            readStyle();
            context.getContentResolver().registerContentObserver(
                    SettingsProvider.URI, false, styleObserver);
            context.registerReceiver(batteryReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            listening = true;
        }

        void readStyle() { drawable.setStyle(SettingsProvider.getStyle(group.getContext())); }

        void hideNative() {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child == icon) continue;
                if (!nativeVisibility.containsKey(child)) nativeVisibility.put(child, child.getVisibility());
                child.setVisibility(View.GONE);
            }
        }

        void detach() {
            Context context = group.getContext();
            if (listening) {
                try { context.unregisterReceiver(batteryReceiver); } catch (Throwable ignored) { }
            }
            try { context.getContentResolver().unregisterContentObserver(styleObserver); }
            catch (Throwable ignored) { }
            if (icon.getParent() == group) group.removeView(icon);
            for (Map.Entry<View, Integer> entry : nativeVisibility.entrySet()) {
                if (entry.getKey().getParent() == group)
                    entry.getKey().setVisibility(entry.getValue());
            }
            nativeVisibility.clear();
        }
    }
}
