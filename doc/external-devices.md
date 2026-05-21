# External NFC devices (Reader app)

`app-reader` now exposes a multi-source NFC abstraction for:

- PN532 (USB host bridge adapters, e.g. PL2303/CP210x)
- Wear OS NFC tap forwarding
- ACR122U (USB)

## USB support

- USB host feature is declared in `app-reader/src/main/AndroidManifest.xml`.
- USB attach/detach and permission broadcasts are handled by `USBPermissionReceiver`.
- `NFCManager.scanUSBDevices()` classifies supported USB IDs:
  - PN532 bridge IDs: `067B:2303`, `10C4:EA60`
  - ACR122U vendor: `072F`

## Event model

All handlers emit `NFCEvent` with a `NFCSource` (`PHONE`, `WEAR_OS`, `PN532`, `ACR122U`) so downstream parsing/upload logic can treat external devices uniformly.
