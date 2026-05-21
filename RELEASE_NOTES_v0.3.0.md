# NFCGate v0.3.0

## Highlights

- **Server-side automatic parsing** for uploaded sessions:
  - EMV parsing with brand detection and normalized fields.
  - MIFARE parsing for supported variants.
  - PAN is **masked by default**.
  - Admin-token protected unmask support.
  - Audit log coverage for sensitive unmask operations.
- **End-to-end encryption (Noise XX)** between **Reader and HCE**:
  - Optional (opt-in) encrypted payload mode.
  - TOFU workflow with peer fingerprint confirmation.
- **Backward compatible by design**:
  - Existing plain HTTPS upload still works.
  - E2EE remains off by default.

## Install / Upgrade

- Android APKs and plugin artifacts: use the latest successful CI artifacts from the repository Actions page:
  - <https://github.com/zhouyitian389-max/nfcgate/actions/workflows/build-android.yml>
- Upload server setup and deployment:
  - <https://github.com/zhouyitian389-max/nfcgate/blob/v2/tools/upload-server/README.md>

## Known limitations

- Parser brand coverage is currently limited to **Visa / Mastercard / Amex / JCB / UnionPay**.
- Noise XX E2EE in this release is implemented for **Reader↔HCE**; it is **not yet implemented** in the standalone Flask upload server path.
