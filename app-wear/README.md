# app-wear

Wear OS companion app for NFCGate Reader.

## Features

- Start/stop/pause/resume capture commands from the watch
- Session status rendering (tap count, bytes, duration, phone connectivity)
- Data Layer message + data item sync with `app-reader`
- Local settings toggles for gesture, brightness, and debug mode

## Integration Notes

- Capability name: `nfcgate_reader`
- Wear → phone command path: `/nfcgate/wear/command`
- Phone → wear status path: `/nfcgate/phone/status`
- Phone → wear tap path: `/nfcgate/phone/tap`
- Session data item path: `/nfcgate/session`
