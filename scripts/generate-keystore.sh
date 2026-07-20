#!/bin/bash
# Generate release keystore for signing APK/AAB
# Run this ONCE on your local machine, then store the keystore securely
# NEVER commit the keystore to git!

set -e

KEYSTORE_FILE="release.keystore"
KEY_ALIAS="beidou-satellite-key"
KEY_PASSWORD=$(openssl rand -base64 32)
STORE_PASSWORD=$(openssl rand -base64 32)
VALIDITY_DAYS=10000  # ~27 years

echo "========================================"
echo "Generating Release Keystore"
echo "========================================"
echo ""
echo "IMPORTANT: Save these credentials securely!"
echo "You will need them for every release build."
echo ""

# Generate keystore
keytool -genkeypair \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity $VALIDITY_DAYS \
  -keystore "$KEYSTORE_FILE" \
  -storepass "$STORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -dname "CN=Beidou Satellite, OU=Mobile, O=Huawei, L=Shenzhen, ST=Guangdong, C=CN" \
  -ext "bc=ca:false" \
  -ext "ku=digitalSignature,nonRepudiation,keyEncipherment"

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================"
    echo "Keystore generated successfully!"
    echo "========================================"
    echo ""
    echo "File: $KEYSTORE_FILE"
    echo "Alias: $KEY_ALIAS"
    echo "Key Password: $KEY_PASSWORD"
    echo "Store Password: $STORE_PASSWORD"
    echo ""
    echo "========================================"
    echo "NEXT STEPS:"
    echo "========================================"
    echo "1. Save the above credentials in a secure password manager"
    echo "2. Add to GitHub Secrets:"
    echo "   - KEYSTORE_BASE64: $(base64 -w 0 "$KEYSTORE_FILE")"
    echo "   - KEYSTORE_PASSWORD: $STORE_PASSWORD"
    echo "   - KEY_ALIAS: $KEY_ALIAS"
    echo "   - KEY_PASSWORD: $KEY_PASSWORD"
    echo "3. Copy keystore to secure backup location"
    echo "4. NEVER commit keystore to git!"
    echo ""
    echo "To verify keystore:"
    echo "  keytool -list -v -keystore $KEYSTORE_FILE -storepass $STORE_PASSWORD"
else
    echo "Failed to generate keystore"
    exit 1
fi