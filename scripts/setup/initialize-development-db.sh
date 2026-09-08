#!/usr/bin/env bash
# Creates the local development database and its application user from .env.

set -euo pipefail

project_root=$(cd "$(dirname "$0")/../.." && pwd)
environment_file="$project_root/.env"

if [ ! -f "$environment_file" ]; then
    echo "Missing .env. Create it first with: cp .env.example .env" >&2
    exit 78
fi

if ! command -v mysql >/dev/null 2>&1; then
    echo "mysql client is required." >&2
    exit 69
fi

set -a
# shellcheck disable=SC1090
source "$environment_file"
set +a

database_url=${DB_URL:?DB_URL must be set in .env}
database_user=${DB_USER:?DB_USER must be set in .env}
database_password=${DB_PASSWORD:?DB_PASSWORD must be set in .env}

if [[ ! "$database_url" =~ ^jdbc:mysql://(localhost|127\.0\.0\.1)(:([0-9]+))?/([A-Za-z0-9_]+)(\?.*)?$ ]]; then
    echo "DB_URL must point to a local MySQL database with an alphanumeric database name." >&2
    exit 64
fi

database_host=${BASH_REMATCH[1]}
database_port=${BASH_REMATCH[3]:-3306}
database_name=${BASH_REMATCH[4]}

if [[ ! "$database_user" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "DB_USER may contain only letters, numbers, and underscores." >&2
    exit 64
fi

# MySQL string literals escape a single quote by doubling it.
escaped_password=${database_password//\'/\'\'}

echo "Creating local database '$database_name' and user '$database_user' on $database_host:$database_port."
mysql --host="$database_host" --port="$database_port" --user=root --password <<SQL
CREATE DATABASE IF NOT EXISTS \`$database_name\`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$database_user'@'localhost' IDENTIFIED BY '$escaped_password';
ALTER USER '$database_user'@'localhost' IDENTIFIED BY '$escaped_password';
GRANT ALL PRIVILEGES ON \`$database_name\`.* TO '$database_user'@'localhost';
FLUSH PRIVILEGES;
SQL

echo "Development database initialization completed. Start the app with: set -a; source .env; set +a; sbt run"
