package app.grapheneos.carrierconfig2;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    public interface Namespace {
        String APN_CSETTINGS_VERSIONS = "apn_csettings_versions";
    }

    public static SharedPreferences get(Context ctx, String ns) {
        return ctx.getSharedPreferences(ns, Context.MODE_PRIVATE);
    }
}
