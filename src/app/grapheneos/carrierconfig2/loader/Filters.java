package app.grapheneos.carrierconfig2.loader;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.telephony.CarrierConfigManager;
import android.util.Log;

import java.util.Arrays;
import java.util.function.Predicate;

class Filters {
    static final String TAG = Filters.class.getSimpleName();

    @Nullable
    static Boolean filterBoolValue(Context ctx, String key, boolean orig) {
        switch (key) {
            case CarrierConfigManager.KEY_ALLOW_ADDING_APNS_BOOL:
            case CarrierConfigManager.KEY_APN_EXPAND_BOOL:
            case CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_BOOL:
            case CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL:
            case CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL:
            case CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL:
            case CarrierConfigManager.KEY_HIDE_ENABLE_2G:
            case CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL:
            case CarrierConfigManager.KEY_HIDE_IMS_APN_BOOL:
            case CarrierConfigManager.KEY_HIDE_PRESET_APN_DETAILS_BOOL:
            case CarrierConfigManager.KEY_SHOW_APN_SETTING_CDMA_BOOL:
            case CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL:
            case "com.google.android.dialer.display_wifi_calling_button_bool":
                return null;
        }

        return orig;
    }

    @Nullable
    static String filterTextValue(Context ctx, String key, String orig) {
        switch (key) {
            case CarrierConfigManager.Gps.KEY_NFW_PROXY_APPS_STRING:
            case CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_EXTRA_STRING:
            case CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_EXTRA_VAL_STRING:
            case CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_INTENT_STRING:
                return null;

            case CarrierConfigManager.KEY_CONFIG_IMS_MMTEL_PACKAGE_OVERRIDE_STRING:
            case CarrierConfigManager.KEY_CONFIG_IMS_PACKAGE_OVERRIDE_STRING:
            case CarrierConfigManager.KEY_CONFIG_IMS_RCS_PACKAGE_OVERRIDE_STRING:
                if (!isSystemApp(ctx, orig)) {
                    return null;
                }
                break;
            case CarrierConfigManager.KEY_CARRIER_PROVISIONING_APP_STRING:
            case CarrierConfigManager.KEY_CARRIER_SETTINGS_ACTIVITY_COMPONENT_NAME_STRING:
            case CarrierConfigManager.KEY_CARRIER_SETUP_APP_STRING:
            case CarrierConfigManager.KEY_SMART_FORWARDING_CONFIG_COMPONENT_NAME_STRING:
            case CarrierConfigManager.KEY_WFC_EMERGENCY_ADDRESS_CARRIER_APP_STRING: {
                if (!isSystemComponentName(ctx, orig)) {
                    return null;
                }
                break;
            }
        }
        return orig;
    }

    @Nullable
    static String[] filterTextArray(Context ctx, String key, String[] orig) {
        Predicate<String> predicate = null;

        switch (key) {
            case CarrierConfigManager.KEY_CARRIER_APP_WAKE_SIGNAL_CONFIG_STRING_ARRAY:
                // copied from com.android.internal.telephony.CarrierSignalAgent
                predicate = str -> {
                    String componentNameDelimiter = "\\s*:\\s*";
                    String[] split = str.split(componentNameDelimiter);
                    if (split.length != 2) {
                        Log.w(TAG, "invalid " + key + ": " + str);
                        return false;
                    }

                    return isSystemComponentName(ctx, split[0]);
                };
                break;

            case CarrierConfigManager.KEY_ENABLE_APPS_STRING_ARRAY:
                predicate = str -> isSystemApp(ctx, str);
                break;

            case CarrierConfigManager.KEY_READ_ONLY_APN_FIELDS_STRING_ARRAY:
            case CarrierConfigManager.KEY_READ_ONLY_APN_TYPES_STRING_ARRAY:
                // don't block the user from modifying APNs
                return null;
        }

        if (predicate != null) {
            String[] arr = Arrays.stream(orig).filter(predicate).toArray(String[]::new);
            if (arr.length == 0) {
                return null;
            }
            return arr;
        }

        return orig;
    }

    private static boolean isSystemComponentName(Context ctx, String name) {
        var cn = ComponentName.unflattenFromString(name);
        if (cn == null) {
            Log.w(TAG, "malformed ComponentName " + name);
            return false;
        }
        return isSystemApp(ctx, cn.getPackageName());
    }

    private static boolean isSystemApp(Context ctx, String packageName) {
        ApplicationInfo ai;
        var pm = ctx.getPackageManager();
        try {
            ai = pm.getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }
}
