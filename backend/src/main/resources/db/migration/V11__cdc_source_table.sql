-- V11: Add source_table column to data_source_definitions for CDC routing
ALTER TABLE data_source_definitions ADD COLUMN IF NOT EXISTS source_table VARCHAR(255);
