-- Create database
CREATE DATABASE IF NOT EXISTS card_recon;
USE DATABASE card_recon;
CREATE SCHEMA IF NOT EXISTS recon;
USE SCHEMA recon;

-- Create warehouse
CREATE WAREHOUSE IF NOT EXISTS recon_wh
  WAREHOUSE_SIZE = 'XSMALL'
  AUTO_SUSPEND = 60
  AUTO_RESUME = TRUE;

-- Table: Raw authorizations
CREATE TABLE IF NOT EXISTS raw_authorizations (
    txn_id        VARCHAR(20),
    amount        NUMBER(10,2),
    merchant      VARCHAR(100),
    auth_date     TIMESTAMP
);

-- Table: Raw settlements
CREATE TABLE IF NOT EXISTS raw_settlements (
    txn_id        VARCHAR(20),
    amount        NUMBER(10,2),
    merchant      VARCHAR(100),
    settle_date   DATE
);

-- Table: Reconciliation results
CREATE TABLE IF NOT EXISTS reconciliation_results (
    txn_id          VARCHAR(20),
    auth_amount     NUMBER(10,2),
    settle_amount   NUMBER(10,2),
    variance        NUMBER(10,2),
    recon_status    VARCHAR(30),
    merchant        VARCHAR(100),
    loaded_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP()
);

-- File format for CSVs
CREATE FILE FORMAT IF NOT EXISTS csv_format
    TYPE = 'CSV'
    FIELD_OPTIONALLY_ENCLOSED_BY = '"'
    SKIP_HEADER = 1;

-- Stage pointing to S3
CREATE STAGE IF NOT EXISTS s3_stage
    URL = 's3://card-recon-pipeline-dev/'
    CREDENTIALS = (AWS_KEY_ID = '***' AWS_SECRET_KEY = '***')
    FILE_FORMAT = csv_format;