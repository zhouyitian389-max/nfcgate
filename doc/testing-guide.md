# Testing guide

## Parser regression checks

- `./gradlew :app-reader:testDebugUnitTest`
- `python -m unittest discover tools/upload-server/tests`

## Native fuzz scaffolding

The `fuzz/` directory contains starter libFuzzer targets for EMV, MIFARE, and APDU inputs together with regression corpus samples under `tests/regression/`.
