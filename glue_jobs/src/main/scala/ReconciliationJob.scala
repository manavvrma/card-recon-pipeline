import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object ReconciliationJob {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Card Reconciliation")
      .getOrCreate()

    import spark.implicits._

    val inputPath = args(0)   // s3://bucket/raw/
    val outputPath = args(1)  // s3://bucket/processed/

    // --- EXTRACT ---
    val auths = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv(s"$inputPath/authorizations/")

    val settlements = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv(s"$inputPath/settlements/")

    // --- TRANSFORM: Match by txn_id ---
    val matched = auths.as("a")
      .join(settlements.as("b"), Seq("txn_id"), "full_outer")
      .withColumn("recon_status",
        when(col("a.txn_id").isNull, lit("ORPHANED_SETTLEMENT"))
          .when(col("b.txn_id").isNull, lit("ORPHANED_AUTH"))
          .when(abs(col("a.amount") - col("b.amount")) < 0.01, lit("MATCHED"))
          .otherwise(lit("AMOUNT_MISMATCH"))
      )
      .withColumn("variance",
        coalesce(col("b.amount"), lit(0)) - coalesce(col("a.amount"), lit(0))
      )
      .select(
        coalesce(col("a.txn_id"), col("b.txn_id")).as("txn_id"),
        col("a.amount").as("auth_amount"),
        col("b.amount").as("settle_amount"),
        col("variance"),
        col("recon_status"),
        coalesce(col("a.merchant"), col("b.merchant")).as("merchant")
      )

    // --- LOAD: Write results ---
    matched.write
      .mode("overwrite")
      .option("header", "true")
      .csv(s"$outputPath/reconciliation_results/")

    // Print summary
    matched.groupBy("recon_status").count().show()

    spark.stop()
  }
}