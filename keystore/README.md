# keystore/relay-debug.keystore

Not checked in — it's `.gitignore`'d (`keystore/*.keystore`) and provided to
CI as the `RELEASE_KEYSTORE_BASE64` repository secret instead (Settings →
Secrets and variables → Actions). The release workflow decodes it to this
exact path before building.

It still has to be the *same file* everywhere, secret or not: Relay is
sideload-only (see the root README), so there's no Play Store identity to
anchor to, and the only thing that lets a user install a new release over an
old one without uninstalling first is every build using the same signing
key. A keystore that differed between your machine and CI, or was
regenerated fresh on every CI runner, would silently break upgrades.

It is not sensitive in the usual sense — password and alias are the Android
tooling defaults (`android` / `androiddebugkey`), same as any other debug
keystore. It's a secret here only so it isn't duplicated in git history;
losing it isn't a security incident, but regenerating it invalidates
upgrades for everyone who already has Relay installed from a build signed
with the old one — they'd need to uninstall first. Don't do it without a
reason, and if you do, update the GitHub secret to match.

For a local build, this file has to exist at this path — ask whoever holds
it, or generate your own for local-only testing (it just won't match what
CI produces, so an APK you build yourself won't upgrade one from a GitHub
Release, or vice versa, until you're both on the same key):

```sh
keytool -genkeypair -v -keystore keystore/relay-debug.keystore \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass android -keypass android -dname "CN=Relay Debug, O=Relay, C=US"
```
