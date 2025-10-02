package app.grapheneos.carrierconfig2;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;

import android.annotation.Nullable;

import java.util.Arrays;

public class UpdateConfigProvider extends ContentProvider {
    private static final String TAG = "UpdCfgProvider";

    public static final String METHOD_UPDATE_CONFIG = "updateConfig";
    public static final String ARG_SUBID = "subId";

    private static final String ALLOWED_PACKAGE = "com.android.settings";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(@Nullable String method, @Nullable String arg, @Nullable Bundle extras) {
        getContext().enforceCallingOrSelfPermission(
                Manifest.permission.MODIFY_PHONE_STATE, "MODIFY_PHONE_STATE required");
        final String[] pkgs = getContext().getPackageManager()
                .getPackagesForUid(Binder.getCallingUid());
        if (pkgs == null || !Arrays.asList(pkgs).contains(ALLOWED_PACKAGE)) {
            throw new SecurityException("Only " + ALLOWED_PACKAGE + " may call this provider");
        }

        if (method == null) return null;
        if (METHOD_UPDATE_CONFIG.equals(method)) {
            if (extras == null) return null;
            final int subId = extras.getInt(ARG_SUBID, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                return null;
            }

            final long token = Binder.clearCallingIdentity();
            try {
                // notifyConfigChangedForSubId done here in order for Telephony's CarrierConfigLoader
                // to properly delete the cached configs from CarrierConfig2 and trigger a reload.
                // The cache is keyed by carrier config app, and notifyConfigChangedForSubId uses
                // the calling package as a key for the cached config to delete.
                final var manager = getContext().getSystemService(CarrierConfigManager.class);
                manager.notifyConfigChangedForSubId(subId);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder) {
        throw new IllegalStateException("unused");
    }
    @Override
    public String getType(Uri uri) {
        throw new IllegalStateException("unused");
    }
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new IllegalStateException("unused");  }
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new IllegalStateException("unused");
    }
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new IllegalStateException("unused");
    }
}
