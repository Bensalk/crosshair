# Security

## Reporting a problem

If you find a security issue in Crosshair, please **do not open a public issue**.
Use GitHub's private reporting instead: **Security → Report a vulnerability** on this repository.
You'll get a reply as soon as possible, and a fix will be released before the details are made public.

## What the app can and cannot do

- No internet permission: it cannot send or receive data.
- Settings stay in the app's private storage and are excluded from cloud backup and device transfer.
- The overlay window is not touchable or focusable: it cannot capture taps, keystrokes or screen content.
- The only exported parts are the launcher screen (reads no data from other apps) and the Quick Settings tile (only the system can bind to it).
- Backup files are validated on restore: values are kept within the normal setting ranges and files over 64 KB are rejected.

## Release signing

- Releases are built and signed by GitHub Actions. The signing key is stored only in encrypted GitHub Secrets, is unpacked only for the signing step and deleted right after. It is not in this repository or its history.
- The build refuses to publish an APK that is not signed with the official certificate:
  `FA:AC:5C:90:39:EB:34:24:8C:F2:4C:78:F6:7F:46:82:4E:91:27:A4:0A:A0:7E:D0:0A:95:30:2F:E7:19:73:E5`
- APKs are signed with APK Signature Scheme v2 and v3 (v3 allows the key to be rotated if it ever needs replacing).
