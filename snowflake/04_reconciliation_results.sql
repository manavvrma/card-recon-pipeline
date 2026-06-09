INSERT INTO reconciliation_results (txn_id, auth_amount, settle_amount, variance, recon_status, merchant)
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
    END AS recon_status,
    COALESCE(a.merchant, s.merchant) AS merchant
FROM raw_authorizations a
FULL OUTER JOIN raw_settlements s ON a.txn_id = s.txn_id;