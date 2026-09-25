package dev.chet.batterygradient;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

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
