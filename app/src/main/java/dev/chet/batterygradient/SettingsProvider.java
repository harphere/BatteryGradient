package dev.chet.batterygradient;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.text.format.DateFormat;

public final class SettingsProvider extends ContentProvider {
    public static final Uri URI = Uri.parse("content://dev.chet.batterygradient.settings/style");
    public static final String FILLED = "filled";
    public static final String DASHED = "dashed";
    public static final String CIRCLE = "circle";

    @Override public boolean onCreate() { return true; }

    public static String getStyle(Context context) {
        try {
            Bundle b = context.getContentResolver().call(URI, "get", null, null);
            return sanitize(b == null ? null : b.getString("style"));
        } catch (Throwable ignored) { return FILLED; }
    }

    public static void report(Context context, String message) {
        try { context.getContentResolver().call(URI, "report", message, null); }
        catch (Throwable ignored) { }
    }

    public static String getStatus(Context context) {
        try {
            Bundle b = context.getContentResolver().call(URI, "status", null, null);
            if (b != null) return b.getString("status", "No System UI event received yet");
        } catch (Throwable ignored) { }
        return "No System UI event received yet";
    }

    private boolean isSystemUiCaller(Context context) {
        String[] names = context.getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (names == null) return false;
        for (String name : names) if ("com.android.systemui".equals(name)) return true;
        return false;
    }

    private static String sanitize(String value) {
        return DASHED.equals(value) || CIRCLE.equals(value) ? value : FILLED;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        Bundle out = new Bundle();
        if (context == null) return out;
        if ("set".equals(method) && Binder.getCallingUid() == Process.myUid()) {
            context.getSharedPreferences("battery", 0).edit()
                    .putString("style", sanitize(arg)).apply();
            context.getContentResolver().notifyChange(URI, null);
        }
        if ("report".equals(method) && isSystemUiCaller(context)) {
            String safe = arg == null ? "Unknown event" : arg.substring(0, Math.min(arg.length(), 180));
            String when = DateFormat.format("yyyy-MM-dd HH:mm:ss", System.currentTimeMillis()).toString();
            context.getSharedPreferences("battery", 0).edit()
                    .putString("status", when + " — " + safe).apply();
        }
        out.putString("status", context.getSharedPreferences("battery", 0)
                .getString("status", "No System UI event received yet"));
        out.putString("style", context.getSharedPreferences("battery", 0)
                .getString("style", FILLED));
        return out;
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String sort) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String where, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { return 0; }
}
