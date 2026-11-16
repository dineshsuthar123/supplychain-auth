#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/common.sh"
[[ "$#" -eq 2 && "$1" =~ ^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+$ ]] || { echo 'Supply a reviewed existing account email and role.' >&2; exit 1; }
[[ "$2" =~ ^(MANUFACTURER|VERIFIER|AUDITOR)$ ]] || { echo 'Only bounded operator roles can be assigned here.' >&2; exit 1; }
"$ORACLE_ROOT/backup.sh" >/dev/null
changed=$(compose exec -T -e "SP_OPERATOR_EMAIL=$1" -e "SP_OPERATOR_ROLE=$2" db psql -X -q -A -t -v ON_ERROR_STOP=1 -U postgres -d supplyprint <<'SQL'
\getenv email SP_OPERATOR_EMAIL
\getenv role SP_OPERATOR_ROLE
UPDATE users SET role=:'role' WHERE email=:'email' RETURNING id;
SQL
)
[[ "$changed" =~ ^[a-f0-9-]{36}$ ]] || { echo 'No single existing account was updated.' >&2; exit 1; }
echo 'Reviewed role assigned after backup. Sign in again to obtain updated JWT claims.'
