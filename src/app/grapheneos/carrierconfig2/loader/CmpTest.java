package app.grapheneos.carrierconfig2.loader;

import android.app.ActivityThread;
import android.content.ContentValues;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.service.carrier.CarrierIdentifier;
import android.telephony.CarrierConfigManager;

import com.android.internal.gmscompat.gcarriersettings.ICarrierConfigsLoader;
import com.android.internal.gmscompat.gcarriersettings.TestCarrierConfigService;
import com.google.carrier.CarrierId;
import com.google.carrier.CarrierMap;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.android.internal.util.Preconditions.checkArgumentInRange;

// For each proto CarrierId, compares our output for that CarrierId (with disabled filtering)
// against the output of Google's CarrierSettings app
public class CmpTest {
    private static final String TAG = CmpTest.class.getSimpleName();

    final ICarrierConfigsLoader loader;
    final CSettingsDir csd = Objects.requireNonNull(CSettingsDir.getDefault());
    final BiConsumer<String, String> logger;
    final Random random = new Random();
    final AtomicInteger numProtoCarrierIds = new AtomicInteger();
    final AtomicInteger differenceCount = new AtomicInteger();

    public CmpTest(ICarrierConfigsLoader loader, BiConsumer<String, String> logger) {
        this.loader = loader;
        this.logger = logger;
    }

    void log(String tag, String msg) {
        logger.accept(tag, msg);
    }

    public void run() throws ExecutionException, InterruptedException {
        var clock = SystemClock.uptimeClock();
        var start = clock.instant();

        ForkJoinPool fjp = ForkJoinPool.commonPool();

        List<Future> futures = csd.getCarrierList().getEntryList()
                .stream()
                .map(carrierMap -> fjp.submit(() -> processCarrierMap(loader, carrierMap)))
                .collect(Collectors.toList());

        for (var f : futures) {
            f.get();
        }

        log(TAG, "completed, checked " + numProtoCarrierIds + " protoCarrierIds in "
                + Duration.between(start, clock.instant()));
        log(TAG, "number of differences: " + differenceCount.get());
    }

    boolean processCarrierMap(ICarrierConfigsLoader gcsConfigLoader, CarrierMap carrierMap)
            throws RemoteException {
        String canonicalName = carrierMap.getCanonicalName();

        var ccl = new CarrierConfigLoader(ActivityThread.currentApplication(), csd);
        ccl.disableFiltering();
        ccl.skipApnUpdate();

        for (CarrierId protoCarrierId : carrierMap.getCarrierIdList()) {
            CarrierIdentifier carrierId = createMatchingAndroidCarrierId(protoCarrierId, random);

            Bundle gcsConfigs = gcsConfigLoader.getConfigs(carrierId);

            PersistableBundle gcsCarrierConfigs = gcsConfigs.getParcelable(
                    TestCarrierConfigService.KEY_CARRIER_SERVICE_RESULT, PersistableBundle.class);
            // this key is used internally by GCS, it doesn't affect the OS configuration
            gcsCarrierConfigs.remove("_gcs_carrier_version_");

            PersistableBundle ourCarrierServiceResult = ccl.load(carrierId);
            compareCarrierConfigs(canonicalName, gcsCarrierConfigs, ourCarrierServiceResult);

            List<ContentValues> gcsApns = Arrays.asList(gcsConfigs.getParcelableArray(
                    TestCarrierConfigService.KEY_APN_SERVICE_RESULT, ContentValues.class));
            List<ContentValues> ourApns = Apns.getApnContentValues(csd, carrierId);
            compareApns(canonicalName, gcsApns, ourApns);

            int num = numProtoCarrierIds.incrementAndGet();
            if ((num % 200) == 0) {
                log(TAG, "processed " + num + " protoCarrierIds");
            }
        }
        return true;
    }

    static CarrierIdentifier createMatchingAndroidCarrierId(CarrierId protoCarrierId, Random rnd) {
        String mccMnc = protoCarrierId.getMccMnc();
        checkArgumentInRange(mccMnc.length(), 5, 6, "mccMncLen");
        String mcc = mccMnc.substring(0, 3);
        String mnc = mccMnc.substring(3);
        String spn = null;
        String imsi = null;
        String gid1 = null;

        switch (protoCarrierId.getMvnoDataCase()) {
            case SPN:
                spn = protoCarrierId.getSpn().toLowerCase();
                break;
            case IMSI: {
                String pattern = protoCarrierId.getImsi();

                imsi = pattern.replace('x', randomDigit(rnd))
                        .replace('X', randomDigit(rnd));

                var sb = new StringBuilder();
                sb.append(imsi);
                for (int i = 0; i < 2; ++i) {
                    if (rnd.nextBoolean()) {
                        sb.append(randomDigit(rnd));
                    }
                }
                imsi = sb.toString();
                break;
            }
            case GID1:
                var sb = new StringBuilder();
                sb.append(protoCarrierId.getGid1().toLowerCase());
                for (int i = 0; i < 2; ++i) {
                    if (rnd.nextBoolean()) {
                        sb.append(randomDigit(rnd));
                    }
                }
                gid1 = sb.toString();
                break;
        }
        return new CarrierIdentifier(mcc, mnc, spn, imsi, gid1, null);
    }

    void compareCarrierConfigs(String logTag, PersistableBundle a, PersistableBundle b) {
        logTag += ": compareCarrierConfigs";

        Set<String> ksa = a.keySet();
        Set<String> ksb = b.keySet();

        compareKeysets(logTag, ksa, ksb);

        var fullKs = new HashSet<>(ksa);
        fullKs.addAll(ksb);
        // this string is intentionally formatted differently, it's used only in UI and doesn't
        // affect the actual carrier configuration
        fullKs.remove(CarrierConfigManager.KEY_CARRIER_CONFIG_VERSION_STRING);

        for (String k : fullKs) {
            compareObjects(logTag, k, a.get(k), b.get(k));
        }
    }

    void compareApns(String logTag, List<ContentValues> a, List<ContentValues> b) {
        logTag += ": compareApns";

        if (a.size() != b.size()) {
            log(logTag, "size mismatch: a " + a.size() + " b " + b.size());
            differenceCount.getAndIncrement();
        }

        for (int i = 0, m = Math.min(a.size(), b.size()); i < m; ++i) {
            ContentValues va = a.get(i);
            ContentValues vb = b.get(i);

            Set<String> ksa = va.keySet();
            Set<String> ksb = vb.keySet();

            compareKeysets(logTag, va.keySet(), vb.keySet());

            var unified = new HashSet<>(ksa);
            unified.addAll(ksb);

            for (String k : unified) {
                compareObjects(logTag, k, va.get(k), vb.get(k));
            }
        }
    }

    void compareKeysets(String logTag, Set<String> ksa, Set<String> ksb) {
        var diffA = new HashSet<>(ksa);
        diffA.removeAll(ksb);
        if (!diffA.isEmpty()) {
            log(logTag, "keys missing in b " + Arrays.toString(diffA.toArray()));
        }

        var diffB = new HashSet<>(ksb);
        diffB.removeAll(ksa);
        if (!diffB.isEmpty()) {
            log(logTag, "keys missing in a " + Arrays.toString(diffB.toArray()));
        }
    }

    void compareObjects(String logTag, String k, Object a, Object b) {
        if (Objects.deepEquals(a, b)) {
            return;
        }

        if (a instanceof PersistableBundle && b instanceof PersistableBundle) {
            compareCarrierConfigs(logTag, (PersistableBundle) a, (PersistableBundle) b);
            return;
        }

        log(logTag, "difference found, k " + k + " a " + a + " b " + b);
        differenceCount.getAndIncrement();
    }

    private static char randomDigit(Random rnd) {
        int i = rnd.nextInt(10);
        return (char) ((int) '0' + i);
    }
}
