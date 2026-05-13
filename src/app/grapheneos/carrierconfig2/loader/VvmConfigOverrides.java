package app.grapheneos.carrierconfig2.loader;

import android.os.PersistableBundle;
import android.service.carrier.CarrierIdentifier;
import android.telephony.CarrierConfigManager;

/**
 * Provides Visual Voicemail (VVM) carrier config overrides for carriers whose
 * CarrierSettings protobuf files are missing VVM configuration.
 *
 * The CarrierSettings protobuf database extracted from stock Pixel images does not
 * always include VVM config for every carrier. When KEY_VVM_TYPE_STRING is absent,
 * the AOSP Dialer cannot activate visual voicemail. This class provides the missing
 * VVM config values based on known carrier VVM infrastructure.
 */
class VvmConfigOverrides {
    /**
     * Apply VVM config overrides if the carrier's protobuf is missing VVM settings.
     * Only injects values when KEY_VVM_TYPE_STRING is not already set.
     *
     * Detection uses three independent signals that must ALL match:
     * 1. MCC/MNC from the physical SIM (available before network registration)
     * 2. GID1 from the physical SIM (MVNO identifier)
     * 3. Canonical name from the carrier_list.pb database
     */
    static void apply(CarrierIdentifierExt carrierIdExt, String canonicalName, PersistableBundle bundle) {
        String existingVvmType = bundle.getString(CarrierConfigManager.KEY_VVM_TYPE_STRING);
        if (existingVvmType != null && !existingVvmType.isEmpty()) {
            return;
        }

        CarrierIdentifier cid = carrierIdExt.carrierIdentifier();
        String mcc = cid.getMcc();
        String mnc = cid.getMnc();
        String gid1 = cid.getGid1();

        // Visible (Verizon MVNO): MCC 311, MNC 480, GID1 starts with BAE2
        if ("311".equals(mcc) && "480".equals(mnc)
                && gid1 != null && gid1.toUpperCase().startsWith("BAE2")
                && "visiblev_us".equals(canonicalName)) {
            applyVerizonVvm3(bundle);
        }
    }

    /**
     * Verizon VVM3 config, used by Verizon and its MVNOs (e.g. Visible).
     * Source: AOSP platform/packages/apps/Dialer vvm_config.xml
     */
    private static void applyVerizonVvm3(PersistableBundle bundle) {
        bundle.putString(CarrierConfigManager.KEY_VVM_TYPE_STRING, "vvm_type_vvm3");
        bundle.putString(CarrierConfigManager.KEY_VVM_DESTINATION_NUMBER_STRING, "900080006200");
        bundle.putInt(CarrierConfigManager.KEY_VVM_PORT_NUMBER_INT, 0);
        bundle.putString(CarrierConfigManager.KEY_VVM_CLIENT_PREFIX_STRING, "//VZWVVM");
        bundle.putBoolean(CarrierConfigManager.KEY_VVM_PREFETCH_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_VVM_CELLULAR_DATA_REQUIRED_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_VVM_LEGACY_MODE_ENABLED_BOOL, true);
    }
}
