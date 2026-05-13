# Build Verification

- **Device**: Pixel 9 Pro (caiman)
- **Branch**: visible-vvm-config (based on 16-qpr2)
- **Build date**: 2026-03-05 05:01:52 UTC
- **Build status**: PARTIAL
- **Host**: rabidllm (16 cores, 31Gi RAM)

## Test Results
- 34/34 unit tests passing (VvmConfigOverridesTest)
- VVM3 config values verified against AOSP Dialer vvm_config.xml (Android 10)
- All three identity gates tested independently
- Null safety, case sensitivity, idempotency verified

## VVM3 Values (verified against AOSP)
| Key | Value |
|-----|-------|
| vvm_type_string | vvm_type_vvm3 |
| vvm_destination_number_string | 900080006200 |
| vvm_port_number_int | 0 |
| vvm_client_prefix_string | //VZWVVM |
| vvm_prefetch_bool | true |
| vvm_cellular_data_required_bool | true |
| vvm_legacy_mode_enabled_bool | true |
