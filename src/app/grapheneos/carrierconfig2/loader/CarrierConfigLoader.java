package app.grapheneos.carrierconfig2.loader;

import android.annotation.Nullable;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.service.carrier.CarrierIdentifier;
import android.telephony.CarrierConfigManager;
import android.text.format.DateFormat;
import android.util.Log;

import com.google.carrier.CarrierConfig;
import com.google.carrier.CarrierSettings;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import app.grapheneos.carrierconfig2.Prefs;

public class CarrierConfigLoader {
    public static final String TAG = CarrierConfigLoader.class.getSimpleName();

    private static final CarrierIdentifier DEFAULT_CARRIER_ID = new CarrierIdentifier("000", "000",
            null, null, null, null);

    // carrierId is null when SIM is missing
    public static PersistableBundle load(Context ctx, CSettingsDir csd, @Nullable CarrierIdentifier carrierId, boolean updateApns) {

        CSettings cSettings = null;
        if (carrierId != null) {
            cSettings = CSettings.get(csd, carrierId);

            if (cSettings != null && updateApns) {
                // OS doesn't request the APNs itself (except for ApnService.onRestoreApns()), carrier
                // config app is expected to update APNs on its own.

                if (isCurrentApnCSettingsVersion(ctx, cSettings)) {
                    Log.d(TAG, "CSettings version hasn't changed, skipping APN update");
                } else {
                    Apns.update(ctx, cSettings);
                    storeApnCSettingsVersion(ctx, cSettings);
                }
            }
        }

        CSettings defaults = CSettings.get(csd, DEFAULT_CARRIER_ID);

        var bundle = new PersistableBundle();
        if (defaults != null) {
            // settings for default carrier ID are used as a base, carrier-specific settings are
            // applied on top
            bundle.putAll(cSettingsToBundle(ctx, defaults));
        }

        if (cSettings != null) {
            bundle.putAll(cSettingsToBundle(ctx, cSettings));
            addVersionString(ctx, cSettings, false, bundle);
        } else if (defaults != null) {
            addVersionString(ctx, defaults, true, bundle);
        } else {
            return null;
        }
        return bundle;
    }

    private static PersistableBundle cSettingsToBundle(Context ctx, CSettings cs) {
        return carrierConfigToBundle(ctx, cs.protoCSettings.getConfigs());
    }

    private static PersistableBundle carrierConfigToBundle(Context ctx, CarrierConfig cc) {
        List<CarrierConfig.Config> configs = cc.getConfigList();
        var bundle = new PersistableBundle(configs.size());

        String TAG = "carrierConfigToBundle";

        for (CarrierConfig.Config c : configs) {
            String k = c.getKey();

            switch (c.getValueCase()) {
                case TEXT_VALUE: {
                    String v = Filters.filterTextValue(ctx, k, c.getTextValue());
                    if (v != null) {
                        bundle.putString(k, v);
                    } else {
                        Log.d(TAG, "filtered out " + k + " = " + c.getTextValue());
                    }
                    break;
                }
                case INT_VALUE: {
                    bundle.putInt(k, c.getIntValue());
                    break;
                }
                case LONG_VALUE: {
                    bundle.putLong(k, c.getLongValue());
                    break;
                }
                case BOOL_VALUE: {
                    Boolean v = Filters.filterBoolValue(ctx, k, c.getBoolValue());
                    if (v != null) {
                        bundle.putBoolean(k, v);
                    } else {
                        Log.d(TAG, "filtered out " + k + " = " + c.getBoolValue());
                    }
                    break;
                }
                case TEXT_ARRAY: {
                    String[] orig = c.getTextArray().getItemList().toArray(new String[0]);
                    String[] v = Filters.filterTextArray(ctx, k, orig);
                    if (v != null) {
                        bundle.putStringArray(k, v);
                    } else {
                        Log.d(TAG, "filtered out " + k + " = " + Arrays.toString(orig));
                    }
                    break;
                }
                case INT_ARRAY: {
                    bundle.putIntArray(k, c.getIntArray().getItemList()
                            .stream().mapToInt(Integer::intValue).toArray());
                    break;
                }
                case BUNDLE: {
                    CarrierConfig innerCc = c.getBundle();
                    bundle.putPersistableBundle(k, carrierConfigToBundle(ctx, innerCc));
                    break;
                }
                case DOUBLE_VALUE: {
                    bundle.putDouble(k, c.getDoubleValue());
                    break;
                }
                case VALUE_NOT_SET: {
                    Log.d(TAG, "missing value for key " + k);
                    break;
                }
            }
        }
        return bundle;
    }

    private static boolean isCurrentApnCSettingsVersion(Context ctx, CSettings cs) {
        String k = cs.carrierId2.canonicalName;
        return Prefs.get(ctx, Prefs.Namespace.APN_CSETTINGS_VERSIONS)
                .getLong(k, -1) == cs.protoCSettings.getVersion();
    }

    private static void storeApnCSettingsVersion(Context ctx, CSettings cs) {
        String k = cs.carrierId2.canonicalName;
        SharedPreferences p = Prefs.get(ctx, Prefs.Namespace.APN_CSETTINGS_VERSIONS);
        var ed = p.edit();
        if (p.getAll().size() > 100) {
            // remove old values
            ed.clear();
        }
        ed.putLong(k, cs.protoCSettings.getVersion());
        ed.apply();
    }

    private static void addVersionString(Context ctx, CSettings cSettings, boolean isDefault, PersistableBundle dest) {
        CarrierSettings cs = cSettings.protoCSettings;

        var b = new StringBuilder();
        b.append(cs.getCanonicalName());
        b.append('-');
        b.append(cs.getVersion());
        if (!isDefault) {
            CarrierIdentifier carrierId = cSettings.carrierId2.carrierId;
            b.append("\nMCC: ");
            b.append(carrierId.getMcc());
            b.append(" MNC: ");
            b.append(carrierId.getMnc());

            MvnoSpec mvnoSpec = MvnoSpec.get(cSettings.carrierId2.protoCarrierId);
            if (mvnoSpec != null) {
                b.append("\nMVNO: ");
                b.append(mvnoSpec.typeString());
                b.append(": ");
                b.append(mvnoSpec.matchData);
            }
        }

        var date = new Date(cs.getLastUpdated().getSeconds() * 1000L);
        b.append('\n');
        b.append(DateFormat.getMediumDateFormat(ctx).format(date));
        b.append(' ');
        b.append(DateFormat.getTimeFormat(ctx).format(date));

        dest.putString(CarrierConfigManager.KEY_CARRIER_CONFIG_VERSION_STRING, b.toString());
    }
}
