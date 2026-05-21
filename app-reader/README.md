# app-reader

The Reader app (App A) provides NFC capture, tag data storage, export to standard formats (pcapng / JSON), and HTTPS upload to a configurable endpoint. It is designed to run on an Android device acting as the NFC reader/initiator role.

**Status: skeleton (milestone 2)**

Feature implementation begins in milestone 3. The current version contains minimal wiring and launchable placeholders.

## Wear OS integration (M12)

`app-reader` now includes a Wear Data Layer listener service to:

- receive start/stop/pause/resume/query commands from `app-wear`
- reply with session status updates over messages and data items
- advertise capability `nfcgate_reader`
- provide a bridge helper for forwarding tap events to the watch
