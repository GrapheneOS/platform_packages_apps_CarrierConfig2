package app.grapheneos.carrierconfig2;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.service.carrier.ApnService;
import android.service.carrier.CarrierIdentifier;
import android.util.Log;

import java.util.List;

import app.grapheneos.carrierconfig2.loader.Apns;
import app.grapheneos.carrierconfig2.loader.CSettingsDir;

import static java.util.Collections.emptyList;

public class ApnServiceImpl extends ApnService {
    public static final String TAG = ApnServiceImpl.class.getSimpleName();

    @NonNull
    @Override
    public List<ContentValues> onRestoreApns(int subId) {
        Log.d(TAG, "onRestoreApns: subId " + subId);

        var csd = CSettingsDir.getDefault();
        if (csd == null) {
            Log.e(TAG, "CSettingsDir is missing");
            return emptyList();
        }

        CarrierIdentifier carrierId = Utils.subIdToCarrierId(this, subId);
        if (carrierId == null) {
            return emptyList();
        }

        return Apns.getApnContentValues(csd, carrierId);
    }
}
