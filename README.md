CarrierConfig2 app is the provider of carrier-specific configuration data.

CarrierConfig2 relies on the carrier settings database from Google's CarrierSettings (GCS) app. 
This database is stored as a directory of protobuf files, its structure is described in CSettingsDir 
class.

The actual carrier configuration is initiated and performed by the OS, not by CarrierConfig2, see
https://source.android.com/docs/core/connect/carrier for more info.

For any given "settings database + CarrierIdentifier" input, CarrierConfig2 and GCS should output
the same configuration data when config editing is disabled in CarrierConfig2.

# Testing 

Testing is done by checking that CarrierConfig2 and GCS return the same configuration for each
CarrierId.

Testing can be performed only on debuggable OS builds, it relies on granting privileged permissions 
to GCS app and hooking it in an invasive way.

To enable the testing mode:
```
adb shell su root setprop persist.sys.GCarrierSettings_testing_mode_enabled 1
adb reboot
adb install $UNPACKED_STOCK_FACTORY_IMAGE/product/priv-app/CarrierSettings/CarrierSettings.apk
```

Start the test:
```
adb shell su root am start-activity -S -n app.grapheneos.carrierconfig2/.TestActivity
```

UI shows only the basic info, see logcat of CarrierConfig2 and GCS apps for more details.

If there are differences in output between CarrierConfig2 and GCS, they will be shown in the UI.
