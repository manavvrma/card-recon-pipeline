# Credit Card Transaction Reconciliation Pipeline

## Overview

An end-to-end ETL pipeline that reconciles credit card authorization transactions against settlement records to detect discrepancies, orphaned transactions, and amount variances.

```
CSV Files  →  Amazon S3  →  AWS Glue (Scala/Spark)  →  Snowflake  →  Reconciliation Report
```

## Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Data Generator │     │    Amazon S3     │     │   AWS Glue      │     │   Snowflake     │
│  (Scala)        │────>│  (Data Lake)     │────>│  (Spark ETL)    │────>│  (Warehouse)    │
│                 │     │                  │     │                 │     │                 │
│ 1000 auths      │     │ raw/             │     │ Extract         │     │ raw_authorizations│
│ ~956 settlements│     │ processed/       │     │ Transform       │     │ raw_settlements  │
│ with planted    │     │ jars/            │     │ Match & Reconcile│    │ reconciliation_  │
│ discrepancies   │     │                  │     │                 │     │   results        │
└─────────────────┘     └─────────────────┘     └─────────────────┘     └─────────────────┘
                                                                                │
                                                                                v
                                                                        ┌─────────────────┐
                                                                        │  Daily Report   │
                                                                        │                 │
                                                                        │ MATCHED: 79.8%  │
                                                                        │ ORPHANED: 8.9%  │
                                                                        │ MISMATCH: 6.7%  │
                                                                        └─────────────────┘
```

## Tech Stack

| Technology | Purpose |
|-----------|---------|
| Scala 2.12 | Data generation + ETL job |
| Apache Spark 3.3 | Distributed data processing |
| AWS S3 | Data lake (raw + processed zones) |
| AWS Glue 4.0 | Managed Spark runtime |
| Snowflake | Cloud data warehouse |
| SBT | Build tool |
| Git/GitHub | Version control |

## Project Structure

```
card-recon-pipeline/
├── glue_jobs/
│   ├── build.sbt                      <- Build configuration
│   ├── project/
│   │   └── plugins.sbt               <- SBT assembly plugin
│   └── src/main/scala/
│       ├── ReconciliationJob.scala     <- Spark ETL job
│       └── GenerateSampleData.scala    <- Sample data generator
├── snowflake/
│   ├── 01_setup.sql                   <- Database, tables, stages
│   ├── 02_load_and_reconcile.sql      <- Load from S3 + reconciliation
│   └── 03_report.sql                  <- Daily report view
├── data/                              <- Generated CSV files (gitignored)
├── .gitignore
└── README.md
```

## How It Works

### 1. Data Generation (Scala)

Generates synthetic credit card transaction data with intentional discrepancies:

| Scenario | % of Records | Purpose |
|----------|-------------|---------|
| Exact match (auth = settlement) | 85% | Normal transactions |
| Amount variance | 7% | Detects revenue leakage |
| Orphaned authorization | 4% | Auth with no settlement |
| Orphaned settlement | 4% | Settlement with no auth |

### 2. S3 Data Lake

Two-zone architecture:
- `raw/` — Landing zone for source CSV files
- `processed/` — Cleaned and matched output from Glue

### 3. AWS Glue ETL (Scala/Spark)

Single Spark job that:
- Reads authorizations and settlements from S3
- Performs FULL OUTER JOIN on `txn_id`
- Classifies each transaction: MATCHED, AMOUNT_MISMATCH, ORPHANED_AUTH, ORPHANED_SETTLEMENT
- Writes results back to S3

### 4. Snowflake Loading & Reporting

- COPY INTO loads data from S3 via external stage
- Reconciliation logic also implemented in Snowflake SQL (FULL OUTER JOIN + CASE)
- View produces daily summary report

## Results

```
RECON_STATUS          | COUNT | AUTH_AMOUNT  | SETTLE_AMOUNT | VARIANCE  | PCT
----------------------|-------|--------------|---------------|-----------|------
MATCHED              |   837 | $216,088.19  | $216,088.19   | $0        | 79.8%
ORPHANED_AUTH        |    93 | $23,627.53   |               |           |  8.9%
AMOUNT_MISMATCH      |    70 | $16,714.99   | $16,595.04    | $1,273.47 |  6.7%
ORPHANED_SETTLEMENT  |    49 |              | $12,413.44    |           |  4.7%
```

## How to Run

### Prerequisites
- Java 11 (JDK)
- SBT
- AWS CLI configured
- Snowflake account

### Commands (Windows)

```cmd
REM Generate sample data
cd glue_jobs
sbt "runMain GenerateSampleData ../data"

REM Build the Spark JAR
sbt assembly

REM Upload to S3
aws s3 cp target\scala-2.12\card-reconciliation-assembly-1.0.jar s3://card-recon-pipeline-dev/jars/
aws s3 cp ..\data\authorizations.csv s3://card-recon-pipeline-dev/raw/authorizations/
aws s3 cp ..\data\settlements.csv s3://card-recon-pipeline-dev/raw/settlements/

REM Run Glue job
aws glue start-job-run --job-name "card-reconciliation"

REM Load into Snowflake (run SQL files in order)
REM 01_setup.sql -> 02_load_and_reconcile.sql -> 03_report.sql
```

## Key SQL: Reconciliation Logic

```sql
SELECT
    COALESCE(a.txn_id, s.txn_id) AS txn_id,
    a.amount AS auth_amount,
    s.amount AS settle_amount,
    COALESCE(s.amount, 0) - COALESCE(a.amount, 0) AS variance,
    CASE
        WHEN a.txn_id IS NULL THEN 'ORPHANED_SETTLEMENT'
        WHEN s.txn_id IS NULL THEN 'ORPHANED_AUTH'
        WHEN ABS(a.amount - s.amount) < 0.01 THEN 'MATCHED'
        ELSE 'AMOUNT_MISMATCH'
    END AS recon_status
FROM raw_authorizations a
FULL OUTER JOIN raw_settlements s ON a.txn_id = s.txn_id;
```

## Concepts Demonstrated

| Concept | Implementation |
|---------|---------------|
| ETL Pipeline | End-to-end: extract from S3, transform in Spark, load to Snowflake |
| Data Lake Architecture | S3 with raw/processed zone separation |
| Apache Spark | Distributed processing with DataFrames and Spark SQL |
| FULL OUTER JOIN | Matching two datasets to find discrepancies |
| Data Quality | Detecting orphans, duplicates, and amount variances |
| Snowflake COPY INTO | Bulk loading from external stage (S3) |
| SQL Views | Reusable reporting layer |
| Scala | Functional programming with case classes and pattern matching |
| Infrastructure as Code | SBT build, AWS CLI commands |

## Future Enhancements

- [ ] Error handling with rejected records output
- [ ] Audit/logging table for pipeline runs
- [ ] Unit tests (ScalaTest)
- [ ] Incremental loading (process only new files)
- [ ] MERGE statement for dimension tables (SCD Type-1)
- [ ] Snowflake Task scheduling
- [ ] CloudWatch alerting if match rate drops below threshold

## Author

Manav Verma — Data Engineer at Capgemini
