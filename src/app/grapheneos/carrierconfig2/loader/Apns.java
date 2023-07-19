package app.grapheneos.carrierconfig2.loader;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.Telephony;
import android.service.carrier.CarrierIdentifier;
import android.text.TextUtils;
import android.util.Log;

import com.google.carrier.ApnItem;
import com.google.carrier.CarrierId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;

public class Apns {
    static final String TAG = Apns.class.getSimpleName();

    // Matches the sequence used by Google's CarrierSettings app as of version 41
    static void update(Context ctx, CSettings cSettings) {
        String TAG = "updateApns";

        deleteUneditedApnsForCarrierId(ctx, cSettings.carrierId2.protoCarrierId);

        List<ContentValues> list = getApnContentValues(cSettings);
        if (list.isEmpty()) {
            Log.d(TAG, "APN list is empty");
            return;
        }

        int numNewRows = ctx.getContentResolver().bulkInsert(Telephony.Carriers.CONTENT_URI,
                list.toArray(new ContentValues[0]));
        Log.d(TAG, "numNewRows: " + numNewRows);
    }

    private static ContentValues apnItemToContentValues(ApnItem i, CarrierId protoCarrierId) {
        var cv = new ContentValues();
        cv.put(Telephony.Carriers.APN, i.getValue());
        cv.put(Telephony.Carriers.NAME, i.getName());

        String mccMnc = protoCarrierId.getMccMnc();
        String mcc = mccMnc.substring(0, 3);
        String mnc = mccMnc.substring(3);
        cv.put(Telephony.Carriers.MCC, mcc);
        cv.put(Telephony.Carriers.MNC, mnc);
        cv.put(Telephony.Carriers.NUMERIC, mccMnc);

        String mvnoType = "";
        String mvnoMatchData = "";

        MvnoSpec mvnoSpec = MvnoSpec.get(protoCarrierId);
        if (mvnoSpec != null) {
            mvnoType = mvnoSpec.typeString();
            mvnoMatchData = mvnoSpec.matchData;
        }

        // Google's CarrierSettings app still uses this API as of mid-2023, despite it being deprecated in 2019
        cv.put(Telephony.Carriers.MVNO_TYPE, mvnoType);
        cv.put(Telephony.Carriers.MVNO_MATCH_DATA, mvnoMatchData);

        cv.put(Telephony.Carriers.EDITED_STATUS, Telephony.Carriers.UNEDITED);

        cv.put(Telephony.Carriers.TYPE, typesListToString(i.getTypeList()));
        cv.put(Telephony.Carriers.PROTOCOL, i.getProtocol().name());
        cv.put(Telephony.Carriers.ROAMING_PROTOCOL, i.getRoamingProtocol().name());

        cv.put(Telephony.Carriers.SERVER, i.getServer());
        cv.put(Telephony.Carriers.PROXY, i.getProxy());
        cv.put(Telephony.Carriers.PORT, i.getPort());
        cv.put(Telephony.Carriers.USER, i.getUser());
        cv.put(Telephony.Carriers.PASSWORD, i.getPassword());
        cv.put(Telephony.Carriers.AUTH_TYPE, i.getAuthtype());

        cv.put(Telephony.Carriers.MMSC, i.getMmsc());
        cv.put(Telephony.Carriers.MMSPROXY, i.getMmscProxy());
        cv.put(Telephony.Carriers.MMSPORT, i.getMmscProxyPort());

        int bearerBitmaskInt = i.hasBearerBitmask() ? parseBitmaskString(i.getBearerBitmask()) : 0;
        cv.put(Telephony.Carriers.BEARER_BITMASK, bearerBitmaskInt);

        cv.put(Telephony.Carriers.MTU, i.getMtu());
        cv.put(Telephony.Carriers.MTU_V4, i.getMtu());
        if (i.hasProfileId()) {
            cv.put(Telephony.Carriers.PROFILE_ID, i.getProfileId());
        }

        cv.put(Telephony.Carriers.MAX_CONNECTIONS, i.getMaxConns());
        cv.put(Telephony.Carriers.WAIT_TIME_RETRY, i.getWaitTime());
        cv.put(Telephony.Carriers.TIME_LIMIT_FOR_MAX_CONNECTIONS, i.getMaxConnsTime());
        cv.put(Telephony.Carriers.MODEM_PERSIST, i.getModemCognitive());
        cv.put(Telephony.Carriers.USER_VISIBLE, i.getUserVisible());
        cv.put(Telephony.Carriers.USER_EDITABLE, i.getUserEditable());
        cv.put(Telephony.Carriers.APN_SET_ID, i.getApnSetId());

        int skip464XlatInt;
        switch (i.getSkip464Xlat()) {
            case SKIP_464XLAT_DISABLE:
                skip464XlatInt = Telephony.Carriers.SKIP_464XLAT_DISABLE;
                break;
            case SKIP_464XLAT_ENABLE:
                skip464XlatInt = Telephony.Carriers.SKIP_464XLAT_ENABLE;
                break;
            case SKIP_464XLAT_DEFAULT:
            default:
                skip464XlatInt = Telephony.Carriers.SKIP_464XLAT_DEFAULT;
        }
        cv.put(Telephony.Carriers.SKIP_464XLAT, skip464XlatInt);

        int lntBitmask = i.hasLingeringNetworkTypeBitmask() ?
                parseBitmaskString(i.getLingeringNetworkTypeBitmask()) : 0;
        cv.put(Telephony.Carriers.LINGERING_NETWORK_TYPE_BITMASK, lntBitmask);
        cv.put(Telephony.Carriers.ALWAYS_ON, i.getAlwaysOn());
        cv.put(Telephony.Carriers.MTU_V6, i.getMtuV6());

        return cv;
    }

    private static void deleteUneditedApnsForCarrierId(Context ctx, CarrierId protoCarrierId) {
        final String TAG = "deleteUneditedApns";

        final String uneditedClause = " AND " +
                Telephony.Carriers.EDITED_STATUS + "=" + Telephony.Carriers.UNEDITED;

        MvnoSpec mvnoSpec = MvnoSpec.get(protoCarrierId);
        String where;
        String[] selectionArgs;
        if (mvnoSpec == null || TextUtils.isEmpty(mvnoSpec.matchData)) {
            where = Telephony.Carriers.NUMERIC + "=? AND "
                    + Telephony.Carriers.MVNO_TYPE + "=''"
                    + uneditedClause;
            selectionArgs = new String[] { protoCarrierId.getMccMnc() };
        } else {
            where = Telephony.Carriers.NUMERIC + "=? AND "
                    + Telephony.Carriers.MVNO_TYPE + "=? AND "
                    + Telephony.Carriers.MVNO_MATCH_DATA + "=? COLLATE NOCASE"
                    + uneditedClause;
            selectionArgs = new String[] { protoCarrierId.getMccMnc(), mvnoSpec.typeString(), mvnoSpec.matchData };
        }

        Uri uri = Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, "delete");
        ContentResolver cr = ctx.getContentResolver();

        Log.d(TAG, "uri: " + uri + "; where: " + where
                + "; selArgs: " + Arrays.toString(selectionArgs));

        int numDeletedRows = cr.delete(uri, where + uneditedClause, selectionArgs);
        Log.d(TAG, "numDeletedRows " + numDeletedRows);
    }

    public static List<ContentValues> getApnContentValues(CSettingsDir csd, CarrierIdentifier carrierId) {
        CSettings cs = CSettings.get(csd, carrierId);
        if (cs == null) {
            return emptyList();
        }
        return getApnContentValues(cs);
    }

    private static List<ContentValues> getApnContentValues(CSettings cs) {
        List<ApnItem> list = cs.protoCSettings.getApns().getApnList();

        var result = new ArrayList<ContentValues>(list.size());

        for (ApnItem apnItem : list) {
            result.add(apnItemToContentValues(apnItem, cs.carrierId2.protoCarrierId));
        }

        return result;
    }

    private static int parseBitmaskString(String s) {
        int res = 0;
        for (String flagStr : s.split("\\|", -1)) {
            int flag = Integer.parseInt(flagStr);
            if (flag > 0) {
                res |= 1 << (flag - 1);
            }
        }
        return res;
    }

    private static String typesListToString(List<ApnItem.ApnType> list) {
        var b = new StringBuilder(list.size() * 5);
        boolean skipComma = true;

        for (ApnItem.ApnType apnType : list) {
            if (skipComma) {
                skipComma = false;
            } else {
                b.append(',');
            }

            if (apnType == ApnItem.ApnType.ALL) {
                b.append('*');
            } else {
                b.append(apnType.name().toLowerCase());
            }
        }
        return b.toString();
    }
}
