# Reader app external integration guide

## 1) Select source device in Reader UI

The Reader main screen now has:

- **Select device** button (`DeviceSelector`)
- Selected device status text (`DeviceStatus`)
- **Start capture** button

## 2) USB permission flow

1. Connect PN532/ACR122U by OTG.
2. Android sends USB attach broadcast.
3. `USBPermissionReceiver` requests permission via `UsbManager.requestPermission`.
4. Once granted, handlers can open a `USBConnection` and start protocol polling.

## 3) Wear OS NFC forwarding

- Reader side uses `WearOSNFCHandler` + `WearDataLayerBridge`.
- Wear side should forward detected tap metadata (`type`, `uid`, `timestamp`) to phone.

## 4) Protocol layers

- PN532: `PN532Protocol`
- ACR122U: `ACR122UProtocol`
- Shared USB transport: `USBConnection`

These layers are wired by `NFCManager` to provide a unified entry point for capture start/stop.
