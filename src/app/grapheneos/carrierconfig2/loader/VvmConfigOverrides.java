package app.grapheneos.carrierconfig2.loader;

import android.os.PersistableBundle;
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
     */
    static void apply(String canonicalName, PersistableBundle bundle) {
        String existingVvmType = bundle.getString(CarrierConfigManager.KEY_VVM_TYPE_STRING);
        if (existingVvmType != null && !existingVvmType.isEmpty()) {
            return;
        }

        switch (canonicalName) {
            case "visiblev_us":
                // Visible (Verizon MVNO) uses Verizon's VVM3 infrastructure.
                // Values sourced from AOSP Dialer vvm_config.xml for MCC/MNC 311480.
                applyVerizonVvm3(bundle);
                break;
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
