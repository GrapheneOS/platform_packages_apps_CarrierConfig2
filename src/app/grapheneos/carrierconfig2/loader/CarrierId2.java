package app.grapheneos.carrierconfig2.loader;

import android.annotation.Nullable;
import android.service.carrier.CarrierIdentifier;
import android.util.Log;

import com.google.carrier.CarrierId;
import com.google.carrier.CarrierMap;

import java.util.Optional;

import static android.text.TextUtils.nullIfEmpty;

// See com/google/carrier/carrier_list.proto for more info
public class CarrierId2 {
    public final String canonicalName;
    public final CarrierIdentifier carrierId; // AOSP CarrierId
    // CarrierId from CSettingsDir protobuf. Note that it doesn't use the modern integer carrierIds
    public final CarrierId protoCarrierId;

    public CarrierId2(String canonicalName, CarrierIdentifier carrierId, CarrierId protoCarrierId) {
        this.canonicalName = canonicalName;
        this.carrierId = carrierId;
        this.protoCarrierId = protoCarrierId;
    }

    @Nullable
    private static CarrierId2 getInner(CSettingsDir csd, CarrierIdentifier carrierId) {
        final String mccMnc = carrierId.getMcc() + carrierId.getMnc();
        final String spn = nullIfEmpty(carrierId.getSpn());
        final String imsi = nullIfEmpty(carrierId.getImsi());
        final String gid1 = nullIfEmpty(carrierId.getGid1());

        for (CarrierMap carrierMap : csd.getCarrierList().getEntryList()) {
            for (CarrierId candidate : carrierMap.getCarrierIdList()) {
                if (!mccMnc.equals(candidate.getMccMnc())) {
                    continue;
                }

                boolean isMatch = false;

                switch (candidate.getMvnoDataCase()) {
                    case MVNODATA_NOT_SET:
                        // For any given MCC+MNC, MVNO CarrierIds (if there are any) always precede
                        // MVNODATA_NOT_SET CarrierId in the CarrierIdList
                        isMatch = true;
                        break;
                    case SPN:
                        if (spn == null) {
                            continue;
                        }
                        isMatch = spn.equalsIgnoreCase(candidate.getSpn());
                        break;
                    case GID1:
                        if (gid1 == null) {
                            continue;
                        }
                        String candidateGid1 = candidate.getGid1();
                        // matches logic in Google's CarrierSettings app
                        if (gid1.length() >= candidateGid1.length()) {
                            isMatch = gid1.substring(0, candidateGid1.length()).equalsIgnoreCase(candidateGid1);
                        }
                        break;
                    case IMSI:
                        if (imsi == null) {
                            continue;
                        }
                        String candidateImsi = candidate.getImsi();

                        // matches logic in Google's CarrierSettings app
                        String imsiRegex = candidateImsi
                                .replaceAll("[xX]*$", "[0-9]*")
                                .replaceAll("[xX]", "[0-9]");
                        isMatch = imsi.matches(imsiRegex);
                        break;
                }

                if (isMatch) {
                    return new CarrierId2(carrierMap.getCanonicalName(), carrierId, candidate);
                }
            }
        }
        return null;
    }

    @Nullable
    public static CarrierId2 get(CSettingsDir csd, CarrierIdentifier carrierId) {
        Optional<CarrierId2> cached = csd.carrierId2LookupCache.get(carrierId);
        CarrierId2 cid2;
        if (cached != null) {
            cid2 = cached.orElse(null);
        } else {
            cid2 = getInner(csd, carrierId);
            csd.carrierId2LookupCache.put(carrierId, Optional.ofNullable(cid2));
        }

        String TAG = "CarrierId2.get";
        if (cid2 == null) {
            Log.d(TAG, "missing for " + carrierId);
        } else {
            Log.d(TAG, "canonicalName " + cid2.canonicalName + "; " + carrierId);
        }
        return cid2;
    }
}
