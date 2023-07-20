package app.grapheneos.carrierconfig2;

import android.annotation.Nullable;
import android.os.PersistableBundle;
import android.service.carrier.CarrierIdentifier;
import android.service.carrier.CarrierService;
import android.util.Log;

import app.grapheneos.carrierconfig2.loader.CSettingsDir;
import app.grapheneos.carrierconfig2.loader.CarrierConfigLoader;

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

        return new CarrierConfigLoader(getApplicationContext(), csd).load(carrierId);
    }

    @Override
    public PersistableBundle onLoadConfig(CarrierIdentifier id) {
        // this method is deprecated and should never be called by the OS
        throw new IllegalStateException(String.valueOf(id));
    }
}
