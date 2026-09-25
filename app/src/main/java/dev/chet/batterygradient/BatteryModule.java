package dev.chet.batterygradient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!UI.equals(param.packageName)) return;
        Class<?> controller = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.phone.PhoneStatusBarViewController",
                param.classLoader);
        if (controller != null) {
            XposedBridge.hookAllMethods(controller, "onViewAttached", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        View root = (View) XposedHelpers.getObjectField(p.thisObject, "mView");
                        if (root != null) root.post(() -> installInStatusBar(root));
                    } catch (Throwable error) {
                        XposedBridge.log("BatteryGradient: status bar attach unavailable: " + error);
                        report("Status bar controller view unavailable: "
                                + error.getClass().getSimpleName());
                    }
                }
            });
            XposedBridge.hookAllMethods(controller, "onViewDetached", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        View root = (View) XposedHelpers.getObjectField(p.thisObject, "mView");
                        ViewGroup container = findSystemIcons(root);
                        if (container != null) uninstall(container);
                    } catch (Throwable ignored) { }
                }
            });
            XposedBridge.log("BatteryGradient: PhoneStatusBarViewController hook ready");
            report("Status bar controller hook installed");
        }
        if (controller == null) {
            XposedBridge.log("BatteryGradient: status bar controller absent; trying battery view");
            report("Status bar controller absent; trying battery view");
        }
        Class<?> battery = XposedHelpers.findClassIfExists(
                "com.android.systemui.battery.BatteryMeterView", param.classLoader);
        if (battery == null) {
            XposedBridge.log("BatteryGradient: BatteryMeterView absent");
            if (controller == null) report("Neither status bar hook class was found");
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
        if (controller == null) report("Battery view hook installed");
    }

    private static void report(String message) {
        try {
            Object app = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication");
            if (app instanceof Context) SettingsProvider.report((Context) app, message);
        } catch (Throwable error) {
            XposedBridge.log("BatteryGradient: diagnostic report unavailable: " + error);
        }
    }

    private static void install(ViewGroup group) {
        try {
            if (!group.isAttachedToWindow() || HOLDERS.containsKey(group)) return;
            for (View parent = group.getParent() instanceof View
                    ? (View) group.getParent() : null; parent != null;
                    parent = parent.getParent() instanceof View ? (View) parent.getParent() : null) {
                if (parent instanceof ViewGroup && HOLDERS.containsKey(parent)) return;
            }
            // Preserve ROMs with an unexpected empty battery container.
            if (group.getChildCount() == 0) {
                report("Battery view attached, but has no child icon");
                return;
            }
            Holder holder = new Holder(group, true, null);
            HOLDERS.put(group, holder);
            holder.attach();
            report("Battery view icon attached");
        } catch (Throwable error) {
            XposedBridge.log("BatteryGradient: battery view left unchanged: " + error);
            report("Battery view attach failed: " + error.getClass().getSimpleName());
            uninstall(group);
        }
    }

    private static void uninstall(ViewGroup group) {
        Holder holder = HOLDERS.remove(group);
        if (holder != null) holder.detach();
    }

    private static ViewGroup findSystemIcons(View root) {
        if (root == null) return null;
        int id = root.getResources().getIdentifier("system_icons", "id", UI);
        View view = id == 0 ? null : root.findViewById(id);
        return view instanceof ViewGroup ? (ViewGroup) view : null;
    }

    private static void installInStatusBar(View root) {
        try {
            if (!root.isAttachedToWindow()) {
                report("Status bar root has not attached");
                return;
            }
            ViewGroup container = findSystemIcons(root);
            if (container == null || HOLDERS.containsKey(container)) {
                if (container == null) {
                    XposedBridge.log("BatteryGradient: system_icons container absent");
                    report("Status bar attached; system_icons not found");
                }
                return;
            }
            int batteryId = root.getResources().getIdentifier("battery", "id", UI);
            View stock = batteryId == 0 ? null : root.findViewById(batteryId);
            Holder holder = new Holder(container, false, stock);
            HOLDERS.put(container, holder);
            holder.attach();
            XposedBridge.log("BatteryGradient: status bar icon attached");
            report("Status bar icon attached");
        } catch (Throwable error) {
            XposedBridge.log("BatteryGradient: status bar icon unavailable: " + error);
            report("Status bar attach failed: " + error.getClass().getSimpleName());
            ViewGroup group = findSystemIcons(root);
            if (group != null) uninstall(group);
        }
    }

    private static final class Holder {
        final ViewGroup group;
        final boolean insideStockBattery;
        final View stockBattery;
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
        final ContentObserver styleObserver = new ContentObserver(null) {
            @Override public void onChange(boolean selfChange) {
                // Vector can instantiate the module in the zygote, before a main
                // looper exists. Schedule UI work only after the view is attached.
                group.post(() -> readStyle());
            }
        };
        boolean listening;

        Holder(ViewGroup group, boolean insideStockBattery, View stockBattery) {
            this.group = group;
            this.insideStockBattery = insideStockBattery;
            this.stockBattery = stockBattery;
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
            if (!insideStockBattery) {
                if (stockBattery != null) {
                    if (!nativeVisibility.containsKey(stockBattery))
                        nativeVisibility.put(stockBattery, stockBattery.getVisibility());
                    stockBattery.setVisibility(View.GONE);
                }
                return;
            }
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
                if (entry.getKey().getParent() != null)
                    entry.getKey().setVisibility(entry.getValue());
            }
            nativeVisibility.clear();
        }
    }
}
