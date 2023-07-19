package app.grapheneos.carrierconfig2.loader;

import android.annotation.Nullable;
import android.os.Environment;
import android.service.carrier.CarrierIdentifier;
import android.util.Log;
import android.util.LruCache;

import com.google.carrier.CarrierList;
import com.google.carrier.CarrierSettings;
import com.google.carrier.MultiCarrierSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Representation of a CarrierSettings directory.
 * <p>
 * CarrierSettings directory has the following structure:
 * <ul>
 * <li>carrier_list.pb maps carrier IDs to their canonical names. Multiple carrier IDs can have the same canonical name.</li>
 * <li>$canonical_name.pb files contain settings for carriers with the given canonical name.</li>
 * <li>others.pb file contains settings for multiple canonical names. It's checked only if
 * $canonical_name.pb is missing.</li>
 * </ul>
 * </p>
 * <p>
 * The underlying file system directory is expected to be fully initialized and immutable for the
 * whole lifetime of a CSettingsDir instance.
 * </p>
 */
public class CSettingsDir {
    public static final String TAG = CSettingsDir.class.getSimpleName();

    private final File dir;

    final LruCache<CarrierIdentifier, Optional<CarrierId2>> carrierId2LookupCache = new LruCache<>(7);
    final LruCache<CarrierIdentifier, Optional<CSettings>> cSettingsLookupCache = new LruCache<>(7);

    // dir is required to be fully initialized and immutable for the whole lifetime of CSettingsDir
    public CSettingsDir(File dir) {
        this.dir = dir;
    }

    @Nullable
    private static final CSettingsDir DEFAULT;

    static {
        File dir = new File(Environment.getProductDirectory(), "etc/CarrierSettings");
        if (!dir.isDirectory()) {
            Log.e(TAG, "default dir is not available");
            DEFAULT = null;
        } else {
            DEFAULT = new CSettingsDir(dir);
        }
    }

    @Nullable
    public static CSettingsDir getDefault() {
        return DEFAULT;
    }

    public boolean isAvailable() {
        return dir.isDirectory();
    }

    private CarrierList cachedCarrierList;

    CarrierList getCarrierList() {
        synchronized (this) {
            if (cachedCarrierList != null) {
                return cachedCarrierList;
            }

            Path path = getProtobufPath("carrier_list");

            try {
                byte[] bytes = Files.readAllBytes(path);
                return cachedCarrierList = CarrierList.parseFrom(bytes);
            } catch (IOException e) {
                Log.e(TAG, "unable to read CarrierList, returning empty instance", e);
                return CarrierList.getDefaultInstance();
            }
        }
    }

    private MultiCarrierSettings cachedMultiCarrierSettings;

    @Nullable
    CarrierSettings getStandaloneCarrierSettings(String canonicalName)
            throws IOException {
        Path path = getProtobufPath(canonicalName);

        if (!Files.isRegularFile(path)) {
            return null;
        }

        return CarrierSettings.parseFrom(Files.readAllBytes(path));
    }

    MultiCarrierSettings getMultiCarrierSettings() {
        synchronized (this) {
            if (cachedMultiCarrierSettings != null) {
                return cachedMultiCarrierSettings;
            }

            Path path = getProtobufPath("others");

            try {
                byte[] bytes = Files.readAllBytes(path);
                return cachedMultiCarrierSettings = MultiCarrierSettings.parseFrom(bytes);
            } catch (IOException e) {
                Log.e(TAG, "unable to read MultiCarrierSettings, using empty instance", e);
                return MultiCarrierSettings.getDefaultInstance();
            }
        }
    }

    private Path getProtobufPath(String name) {
        return new File(dir, name + ".pb").toPath();
    }
}
