package app.grapheneos.carrierconfig2;

import android.annotation.Nullable;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.android.internal.R;
import com.android.internal.gmscompat.gcarriersettings.GCarrierSettingsApp;
import com.android.internal.gmscompat.gcarriersettings.ICarrierConfigsLoader;
import com.android.internal.gmscompat.gcarriersettings.TestCarrierConfigService;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import app.grapheneos.carrierconfig2.loader.CmpTest;

public class TestActivity extends Activity implements ServiceConnection {
    private static final String TAG = TestActivity.class.getSimpleName();

    private ListView listView;
    private ArrayAdapter<String> listAdapter;

    private final Executor bgExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        listAdapter = new ArrayAdapter<>(this, R.layout.simple_list_item_1, R.id.text1);

        var lv = new ListView(this);
        listView = lv;
        lv.setAdapter(listAdapter);
        lv.setScrollbarFadingEnabled(false);

        setContentView(lv);

        var cn = new ComponentName(GCarrierSettingsApp.PKG_NAME, TestCarrierConfigService.class.getName());
        var i = new Intent();
        i.setComponent(cn);

        if (!bindService(i, this, BIND_AUTO_CREATE)) {
            log("bindService returned false, " + i);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        // intentionally checked after service is connected to avoid TOCTOU race
        if (!GCarrierSettingsApp.getPackageSpec().validate(getPackageManager(), 0L)) {
            log("GCarrierSettings app doesn't match its known PackageSpec");
            return;
        }

        log("onServiceConnected: starting comparison test");

        var iCarrierConfigsLoader = ICarrierConfigsLoader.Stub.asInterface(service);

        bgExecutor.execute(() -> {
            try {
                new CmpTest(iCarrierConfigsLoader, this::log).run();
            } catch (Exception e) {
                log(Utils.printStackTraceToString(e));
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        log("onServiceDisconnected");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(this);
    }

    void log(String msg) {
        log(TAG, msg);
    }

    void log(String tag, String s) {
        Log.d(tag, s);
        getMainThreadHandler().post(() -> {
            listAdapter.add(tag + ": " + s);
            listView.postDelayed(() ->
                    listView.smoothScrollToPosition(listAdapter.getCount() - 1), 100);
        });
    }
}
