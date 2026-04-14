-- Seed schema for the CDC source database (Postgres with logical replication).
-- Loaded automatically by the postgres image on first boot.

CREATE TABLE IF NOT EXISTS customers (
    id         serial PRIMARY KEY,
    name       text NOT NULL,
    email      text,
    revenue    numeric(12,2),
    updated_at timestamptz DEFAULT now()
);

-- Publication feeds rows of `customers` into logical replication stream.
CREATE PUBLICATION nexo_pub FOR TABLE customers;

-- Demo rows so the initial snapshot has something to publish.
INSERT INTO customers (name, email, revenue) VALUES
    ('Acme Corp',    'info@acme.com',    125000.00),
    ('Globex Inc',   'hello@globex.com',  87000.00);
