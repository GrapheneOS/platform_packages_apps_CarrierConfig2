package app.grapheneos.carrierconfig2;

import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.Context;
import android.service.carrier.CarrierIdentifier;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import app.grapheneos.carrierconfig2.loader.CarrierIdentifierExt;

public class Utils {

    @Nullable
    static CarrierIdentifierExt subIdToCarrierIdExt(Context ctx, int subId) {
        CarrierIdentifier carrierId = subIdToCarrierId(ctx, subId);
        if (carrierId == null) {
            return null;
        }
        return new CarrierIdentifierExt(carrierId, getIccid(ctx, subId));
    }

    @Nullable
    static CarrierIdentifier subIdToCarrierId(Context ctx, int subId) {
        String TAG = "subIdToCarrierId";

        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            Log.e(TAG, "invalid subId " + subId);
            return null;
        }

        var tm = ctx.getSystemService(TelephonyManager.class).createForSubscriptionId(subId);
        String simOperator = tm.getSimOperator();

        if (simOperator == null) {
            Log.w(TAG, "no simOperator for subId " + subId);
            return null;
        }

        int simOperatorLen = simOperator.length();

        boolean isValid = (simOperatorLen == 5 || simOperatorLen == 6);

        if (!isValid) {
            Log.e(TAG, "invalid simOperator " + simOperator);
            return null;
        }

        String mcc = simOperator.substring(0, 3);
        String mnc = simOperator.substring(3);
        String spn = tm.getSimOperatorName();
        @SuppressLint("HardwareIds")
        String imsi = tm.getSubscriberId();
        String gid1 = tm.getGroupIdLevel1();

        var res = new CarrierIdentifier(mcc, mnc, spn, imsi, gid1, null);
        Log.d(TAG, "subId " + subId + "; " + res);
        return res;
    }

    static String getIccid(Context ctx, int subId) {
        var sm = ctx.getSystemService(SubscriptionManager.class);
        SubscriptionInfo subInfo = sm.getActiveSubscriptionInfo(subId);
        if (subInfo == null) {
            Log.d("getIccid", "getActiveSubscriptionInfo() returned null");
            return "";
        }
        return subInfo.getIccId();
    }

    public static String printStackTraceToString(Throwable t) {
        var baos = new ByteArrayOutputStream(1000);
        t.printStackTrace(new PrintStream(baos));
        return baos.toString();
    }
}
