#!/usr/bin/env bash
# Creates a release signing key and prints the four GitHub secrets the CI release build needs.
#
#   bash scripts/generate-keystore.sh [output-dir]
#
# Run it on your own computer, not in CI. KEEP release.keystore and the passwords somewhere safe:
# every update you publish has to be signed with this same key, and it can't be recreated.
set -euo pipefail

command -v openssl >/dev/null || { echo "openssl not found." >&2; exit 1; }
command -v keytool >/dev/null || { echo "keytool not found. Install a JDK (17 or newer) first." >&2; exit 1; }

OUT="${1:-.}"
KEYSTORE="$OUT/release.keystore"
ALIAS="call-alert-bot"

[ -e "$KEYSTORE" ] && { echo "$KEYSTORE already exists; refusing to overwrite it." >&2; exit 1; }
mkdir -p "$OUT"

# 24 random characters, letters and digits only so the secrets paste cleanly.
# (openssl, not tr|head: with pipefail the SIGPIPE from head would abort the script)
PASSWORD="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | cut -c1-24)"

keytool -genkeypair -v \
  -keystore "$KEYSTORE" -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass "$PASSWORD" -keypass "$PASSWORD" \
  -dname "CN=Call Alert Bot, O=Personal, C=IN" >/dev/null

# base64 without line breaks (GNU and BSD/macOS differ here)
B64="$(base64 < "$KEYSTORE" | tr -d '\n')"

cat <<EOF

Created $KEYSTORE

Add these four secrets in GitHub: repo > Settings > Secrets and variables > Actions > New repository secret

  ANDROID_KEYSTORE_BASE64     (the long text below)
  ANDROID_KEYSTORE_PASSWORD   $PASSWORD
  ANDROID_KEY_ALIAS           $ALIAS
  ANDROID_KEY_PASSWORD        $PASSWORD

ANDROID_KEYSTORE_BASE64:
$B64

Or, with the GitHub CLI, from this folder:

  base64 < "$KEYSTORE" | tr -d '\n' | gh secret set ANDROID_KEYSTORE_BASE64
  gh secret set ANDROID_KEYSTORE_PASSWORD --body "$PASSWORD"
  gh secret set ANDROID_KEY_ALIAS --body "$ALIAS"
  gh secret set ANDROID_KEY_PASSWORD --body "$PASSWORD"

Then back up $KEYSTORE and the password. Never commit them (*.keystore is already in .gitignore).
EOF
