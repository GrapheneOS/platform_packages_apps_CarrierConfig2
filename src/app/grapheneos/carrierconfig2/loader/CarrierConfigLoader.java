package app.grapheneos.carrierconfig2.loader;

import android.annotation.Nullable;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.service.carrier.CarrierIdentifier;
import android.telephony.CarrierConfigManager;
import android.util.Log;

import com.google.carrier.CarrierConfig;
import com.google.carrier.CarrierSettings;
import com.google.carrier.Timestamp;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import app.grapheneos.carrierconfig2.Prefs;

public class CarrierConfigLoader {
    public static final String TAG = CarrierConfigLoader.class.getSimpleName();

    private static final CarrierIdentifier DEFAULT_CARRIER_ID = new CarrierIdentifier("000", "000",
            null, null, null, null);

    private final Context context;
    private final CSettingsDir csd;
    private boolean filteringEnabled = true;
    private boolean apnUpdateAllowed = true;

    public CarrierConfigLoader(Context context, CSettingsDir csd) {
        this.context = context;
        this.csd = csd;
    }

    public void disableFiltering() {
        filteringEnabled = false;
    }

    public void skipApnUpdate() {
        apnUpdateAllowed = false;
    }

    // carrierId is null when SIM is missing
    public PersistableBundle load(@Nullable CarrierIdentifier carrierId) {
        CSettings cSettings = null;
        if (carrierId != null) {
            cSettings = CSettings.get(csd, carrierId);

            if (cSettings != null && apnUpdateAllowed) {
                // OS doesn't request the APNs itself (except for ApnService.onRestoreApns()), carrier
                // config app is expected to update APNs on its own.

                if (isCurrentApnCSettingsVersion(cSettings)) {
                    Log.d(TAG, "CSettings version hasn't changed, skipping APN update");
                } else {
                    Apns.update(context, cSettings);
                    storeApnCSettingsVersion(cSettings);
                }
            }
        }

        CSettings defaults = CSettings.get(csd, DEFAULT_CARRIER_ID);

        var bundle = new PersistableBundle();
        if (defaults != null) {
            // settings for default carrier ID are used as a base, carrier-specific settings are
            // applied on top
            bundle.putAll(cSettingsToBundle(defaults));
        }

        if (cSettings != null) {
            bundle.putAll(cSettingsToBundle(cSettings));
            addVersionString(cSettings, false, bundle);
        } else if (defaults != null) {
            addVersionString(defaults, true, bundle);
        } else {
            return null;
        }
        return bundle;
    }

    private PersistableBundle cSettingsToBundle(CSettings cs) {
        return carrierConfigToBundle(cs.protoCSettings.getConfigs());
    }

    private PersistableBundle carrierConfigToBundle(CarrierConfig cc) {
        List<CarrierConfig.Config> configs = cc.getConfigList();
        var bundle = new PersistableBundle(configs.size());

        String TAG = "carrierConfigToBundle";

        Context ctx = context;
        boolean filteringEnabled = this.filteringEnabled;

        for (CarrierConfig.Config c : configs) {
            String k = c.getKey();

            switch (c.getValueCase()) {
                case TEXT_VALUE: {
                    String orig = c.getTextValue();
                    String v = filteringEnabled ? Filters.filterTextValue(ctx, k, orig) : orig;
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
                    boolean orig = c.getBoolValue();
                    if (filteringEnabled) {
                        Boolean v = Filters.filterBoolValue(ctx, k, orig);
                        if (v != null) {
                            bundle.putBoolean(k, v);
                        } else {
                            Log.d(TAG, "filtered out " + k + " = " + c.getBoolValue());
                        }
                    } else {
                        bundle.putBoolean(k, orig);
                    }
                    break;
                }
                case TEXT_ARRAY: {
                    String[] orig = c.getTextArray().getItemList().toArray(new String[0]);
                    String[] v = filteringEnabled ? Filters.filterTextArray(ctx, k, orig) : orig;
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
                    bundle.putPersistableBundle(k, carrierConfigToBundle(innerCc));
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

    private boolean isCurrentApnCSettingsVersion(CSettings cs) {
        String k = cs.carrierId2.canonicalName;
        return Prefs.get(context, Prefs.Namespace.APN_CSETTINGS_VERSIONS)
                .getLong(k, -1) == cs.protoCSettings.getVersion();
    }

    private void storeApnCSettingsVersion(CSettings cs) {
        String k = cs.carrierId2.canonicalName;
        SharedPreferences p = Prefs.get(context, Prefs.Namespace.APN_CSETTINGS_VERSIONS);
        var ed = p.edit();
        if (p.getAll().size() > 100) {
            // remove old values
            ed.clear();
        }
        ed.putLong(k, cs.protoCSettings.getVersion());
        ed.apply();
    }

    private void addVersionString(CSettings cSettings, boolean isDefault, PersistableBundle dest) {
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

        try {
            Timestamp ts = cs.getLastUpdated();
            String date = Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos())
                    .atZone(ZoneOffset.UTC).toLocalDate().toString();
            b.append('\n');
            b.append(date);
        } catch (DateTimeException|ArithmeticException e) {
            Log.e(TAG, "", e);
        }

        dest.putString(CarrierConfigManager.KEY_CARRIER_CONFIG_VERSION_STRING, b.toString());
    }
}
