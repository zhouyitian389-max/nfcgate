# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

## [0.3.0] - 2026-05-21

### Added

- Server-side automatic parsing for uploaded sessions, including EMV and MIFARE parsing outputs.
- EMV parser coverage for major brands (Visa, Mastercard, Amex, JCB, UnionPay).
- Optional end-to-end encryption between Reader and HCE using Noise XX with TOFU peer fingerprint confirmation.
- Audit logging for sensitive data access events.

### Changed

- PAN values are masked by default in parsed EMV output.
- PAN unmasking now requires an admin token.
- Plain HTTPS session upload remains supported for backward compatibility.
- E2EE upload is opt-in and remains disabled by default.

### Security

- Added Noise XX end-to-end encryption path for Reader↔HCE traffic protection independent of server-side TLS.
- Restricted PAN unmasking to authenticated admin-token requests and recorded unmask actions in audit logs.
