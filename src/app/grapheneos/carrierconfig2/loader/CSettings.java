package app.grapheneos.carrierconfig2.loader;

import android.annotation.Nullable;
import android.service.carrier.CarrierIdentifier;
import android.util.Log;

import com.google.carrier.CarrierSettings;
import com.google.carrier.MultiCarrierSettings;

import java.io.IOException;
import java.util.Optional;

// Complete set of settings for a given CarrierId
class CSettings {
    // carrier ID influences carrier configuration (e.g. APNs)
    final CarrierId2 carrierId2;
    final CarrierSettings protoCSettings;

    CSettings(CarrierId2 carrierId2, CarrierSettings protoCSettings) {
        this.carrierId2 = carrierId2;
        this.protoCSettings = protoCSettings;
    }

    @Nullable
    static CSettings getInner(CSettingsDir csd, CarrierId2 carrierId2) {
        String canonicalName = carrierId2.canonicalName;

        CarrierSettings protoCSettings;
        try {
            protoCSettings = csd.getStandaloneCarrierSettings(canonicalName);
        } catch (IOException e) {
            Log.e("CSettings.get", "", e);
            return null;
        }
        if (protoCSettings == null) {
            protoCSettings = searchMultiCarrierSettings(csd.getMultiCarrierSettings(), canonicalName);
        }
        if (protoCSettings == null) {
            return null;
        }
        return new CSettings(carrierId2, protoCSettings);
    }

    @Nullable
    private static CarrierSettings searchMultiCarrierSettings(MultiCarrierSettings mcs,
                                                              String canonicalName) {
        String TAG = "searchMCSettings";
        for (CarrierSettings cs : mcs.getSettingList()) {
            if (!canonicalName.equals(cs.getCanonicalName())) {
                continue;
            }

            Log.d(TAG, "found " + canonicalName);

            CarrierSettings.Builder b = cs.toBuilder();
            // versions and timestamps of CarrierSettings inside MultiCarrierSettings are missing,
            // use values from MultiCarrierSettings instead
            b.setVersion(mcs.getVersion());
            b.setLastUpdated(mcs.getLastUpdated());
            return b.build();
        }
        return null;
    }

    @Nullable
    static CSettings get(CSettingsDir csd, CarrierId2 carrierId2) {
        CSettings cs;
        Optional<CSettings> cached = csd.cSettingsLookupCache.get(carrierId2.carrierId);
        if (cached != null) {
            cs = cached.orElse(null);
        } else {
            cs = CSettings.getInner(csd, carrierId2);
            csd.cSettingsLookupCache.put(carrierId2.carrierId, Optional.ofNullable(cs));
        }

        String TAG = "CSettings.get";
        String canonicalName = carrierId2.canonicalName;
        if (cs == null) {
            Log.d(TAG, "missing for canonicalName " + canonicalName);
        } else {
            Log.d(TAG, "canonicalName " + canonicalName + "; version " + cs.protoCSettings.getVersion());
        }
        return cs;
    }

    @Nullable
    static CSettings get(CSettingsDir csd, CarrierIdentifier carrierId) {
        CarrierId2 carrierId2 = CarrierId2.get(csd, carrierId);
        if (carrierId2 == null) {
            return null;
        }
        return get(csd, carrierId2);
    }
}
