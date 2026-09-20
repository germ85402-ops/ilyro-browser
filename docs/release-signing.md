# ILYRO release signing identity

Verified from the successful signed `main` release build on September 15, 2026 (app version 0.24.13, workflow run 34943757476).

## APK signer

- Certificate DN: `CN=ILYRO Release, O=ILYRO, C=AM`
- Signature scheme verified: APK Signature Scheme v2
- Number of signers: 1
- Key algorithm: RSA
- Key size: 4096 bits

## Certificate fingerprints

- SHA-256: `c3d911a2e736a4c5ab855d97f3970158633f02d9321674d4702f7d2eb238068f`
- SHA-1: `1b6f2c70fec322321da3c4b00755cc09ffce3e10`
- MD5: `88edb56f873666274cd69b6e64d0b859`

## Public-key fingerprints

- SHA-256: `e0da7014e3cd891f79d066a7d8b3321b2494857190575231b2af14e736244a18`
- SHA-1: `c55c7ab2217723570ea1d6c5865da1fea98da875`
- MD5: `0b3c60847e18a65198d37bdd9d4abfa4`

## Keystore identity guard

The release workflow also verifies the decoded permanent-keystore file against this SHA-256 before using it:

`5f3810a5ca96136c71c089f7b95390aa97f6065b1f7741182a6af35bdf3bdad0`

This is a file-integrity identifier only; the keystore itself and its passwords must never be committed.

## Release rule

Every future production APK/AAB intended to update the same installed ILYRO application must use the expected permanent signing identity. Before publishing, compare the produced artifact's certificate SHA-256 with the value above.
