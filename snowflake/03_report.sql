-- Daily reconciliation summary
CREATE OR REPLACE VIEW daily_recon_report AS
SELECT
    recon_status,
    COUNT(*)                           AS transaction_count,
    SUM(auth_amount)                   AS total_auth_amount,
    SUM(settle_amount)                 AS total_settle_amount,
    SUM(ABS(variance))                 AS total_variance,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER(), 1) AS pct_of_total
FROM reconciliation_results
GROUP BY recon_status
ORDER BY transaction_count DESC;

-- Query the report
SELECT * FROM daily_recon_report;