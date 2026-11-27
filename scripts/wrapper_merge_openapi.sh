#!/bin/bash
set -e

# Esegue il tuo script di merge originale che genera il file
# pwd is infra folder
/bin/bash ../scripts/merge_openapi.sh >&2  # >&2 evita che eventuali echo sporchino stdout

# Calcola l'hash del file appena generato e lo passa a Terraform in JSON
HASH=$(sha256sum ../openapi/nodoTechnicalSupport_direct_openapi.json | awk '{print $1}')
echo "{\"hash\": \"$HASH\"}"