package app.grapheneos.carrierconfig2.loader;

import android.annotation.Nullable;
import android.telephony.data.ApnSetting;

import com.google.carrier.CarrierId;

class MvnoSpec {
    @ApnSetting.MvnoType
    final int typeInt;
    final String matchData;

    MvnoSpec(int typeInt, String matchData) {
        this.typeInt = typeInt;
        this.matchData = matchData;
    }

    String typeString() {
        return ApnSetting.getMvnoTypeStringFromInt(typeInt);
    }

    @Nullable
    static MvnoSpec get(CarrierId protoCarrierId) {
        switch (protoCarrierId.getMvnoDataCase()) {
            case SPN:
                return new MvnoSpec(ApnSetting.MVNO_TYPE_SPN, protoCarrierId.getSpn());
            case GID1:
                return new MvnoSpec(ApnSetting.MVNO_TYPE_GID, protoCarrierId.getGid1());
            case IMSI:
                return new MvnoSpec(ApnSetting.MVNO_TYPE_IMSI, protoCarrierId.getImsi());
            default:
                return null;
        }
    }
}
