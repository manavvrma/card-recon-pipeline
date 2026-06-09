USE DATABASE card_recon;
USE SCHEMA recon;

-- Load raw files from S3
COPY INTO raw_authorizations (txn_id, amount, merchant, auth_date)
FROM @s3_stage/raw/authorizations/
FILE_FORMAT = csv_format
ON_ERROR = 'CONTINUE';

COPY INTO raw_settlements (txn_id, amount, merchant, settle_date)
FROM @s3_stage/raw/settlements/
FILE_FORMAT = csv_format
ON_ERROR = 'CONTINUE';

-- OR: Load Glue output directly
COPY INTO reconciliation_results (txn_id, auth_amount, settle_amount, variance, recon_status, merchant)
FROM @s3_stage/processed/reconciliation_results/
FILE_FORMAT = csv_format
ON_ERROR = 'CONTINUE';