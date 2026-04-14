#!/usr/bin/env bash
# Registers the Debezium Postgres connector against a running Kafka Connect instance.
# Safe to re-run: if the connector already exists it is replaced.

set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
CONNECTOR_NAME="nexo-customers-connector"
SOURCE_PASSWORD="${SOURCE_DB_PASSWORD:-source_pw}"

echo "Waiting for Kafka Connect at ${CONNECT_URL}..."
until curl -s -f "${CONNECT_URL}/connectors" >/dev/null; do
    sleep 2
done

# Drop any prior instance so configuration changes take effect.
curl -s -X DELETE "${CONNECT_URL}/connectors/${CONNECTOR_NAME}" >/dev/null || true

curl -s -X POST -H "Content-Type: application/json" "${CONNECT_URL}/connectors" --data @- <<JSON
{
  "name": "${CONNECTOR_NAME}",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "plugin.name": "pgoutput",
    "database.hostname": "source-postgres",
    "database.port": "5432",
    "database.user": "source_user",
    "database.password": "${SOURCE_PASSWORD}",
    "database.dbname": "source",
    "topic.prefix": "nexo.cdc",
    "table.include.list": "public.customers",
    "publication.name": "nexo_pub",
    "slot.name": "nexo_slot",
    "snapshot.mode": "initial",
    "tombstones.on.delete": "false"
  }
}
JSON

echo
echo "Connector registered. Status:"
curl -s "${CONNECT_URL}/connectors/${CONNECTOR_NAME}/status" | jq . || true
