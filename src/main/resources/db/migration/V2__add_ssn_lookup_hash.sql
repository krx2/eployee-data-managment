-- ADD COLUMN ... NOT NULL with no DEFAULT only works because this table is always empty
-- at this point (V2 runs right after V1 in every environment this project has). On a table
-- with existing rows, Postgres would reject this outright; the safe sequence there would be:
-- add the column nullable, backfill ssn_lookup_hash for existing rows, then ALTER ... SET NOT NULL.
ALTER TABLE employee ADD COLUMN ssn_lookup_hash VARCHAR(64) NOT NULL;

CREATE UNIQUE INDEX uq_employee_ssn_lookup_hash ON employee (ssn_lookup_hash);
