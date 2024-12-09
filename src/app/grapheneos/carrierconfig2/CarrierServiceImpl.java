package app.grapheneos.carrierconfig2;

import android.annotation.Nullable;
import android.content.Context;
import android.os.PersistableBundle;
import android.service.carrier.CarrierIdentifier;
import android.service.carrier.CarrierService;
import android.util.Log;

import app.grapheneos.carrierconfig2.loader.CSettingsDir;
import app.grapheneos.carrierconfig2.loader.CarrierConfigLoader;
import app.grapheneos.carrierconfig2.loader.CarrierIdentifierExt;

public class CarrierServiceImpl extends CarrierService {
    static final String TAG = CarrierServiceImpl.class.getSimpleName();

    @Nullable
    @Override
    public PersistableBundle onLoadConfig(int subId, @Nullable CarrierIdentifier carrierId) {
        Log.d(TAG, "subId " + subId + "; carrierId " + carrierId);

        var csd = CSettingsDir.getDefault();
        if (csd == null) {
            Log.e(TAG, "missing CSettingsDir");
            return null;
        }

        Context ctx = getApplicationContext();

        CarrierIdentifierExt carrierIdExt = null;
        if (carrierId != null) {
            carrierIdExt = new CarrierIdentifierExt(carrierId, Utils.getIccid(ctx, subId));
        }

        return new CarrierConfigLoader(ctx, csd).load(carrierIdExt);
    }

    @Override
    public PersistableBundle onLoadConfig(CarrierIdentifier id) {
        // this method is deprecated and should never be called by the OS
        throw new IllegalStateException(String.valueOf(id));
    }
}
