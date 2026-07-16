#!/bin/sh
set -eu

UPLOADS="${UPLOADS_DIR:-/app/data/uploads}"
PROOFS_UNDER_UPLOADS="$UPLOADS/_private/subscription-proofs"
PARENT="$(dirname "$UPLOADS")"
PROOFS_SIBLING="$PARENT/private/subscription-proofs"

mkdir -p "$UPLOADS" "$PROOFS_UNDER_UPLOADS" "$PROOFS_SIBLING" \
  /app/data/uploads /app/data/private/subscription-proofs \
  /data/uploads /data/private/subscription-proofs || true

# Railway volumes often remount as root; ensure the app user can write proofs + product images.
chown -R app:app "$UPLOADS" "$PROOFS_SIBLING" /app/data /data 2>/dev/null || true
chmod -R u+rwX "$UPLOADS" "$PROOFS_SIBLING" /app/data /data 2>/dev/null || true

echo "[entrypoint] UPLOADS_DIR=$UPLOADS proofs=$PROOFS_UNDER_UPLOADS"

if command -v runuser >/dev/null 2>&1; then
  exec runuser -u app -- env HOME=/app java $JAVA_OPTS -jar /app/app.jar
fi

exec su -s /bin/sh app -c "exec env HOME=/app java $JAVA_OPTS -jar /app/app.jar"
