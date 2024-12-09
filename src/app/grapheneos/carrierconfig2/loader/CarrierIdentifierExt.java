package app.grapheneos.carrierconfig2.loader;

import android.service.carrier.CarrierIdentifier;

public record CarrierIdentifierExt(CarrierIdentifier carrierIdentifier, String iccid) {

    public static final CarrierIdentifierExt DEFAULT = new CarrierIdentifierExt(
            new CarrierIdentifier("000", "000", null, null, null, null), "");
}
