import java.io.{File, PrintWriter}
import scala.util.Random

object GenerateSampleData {
  def main(args: Array[String]): Unit = {
    val random = new Random(42) // Fixed seed for repeatable results
    val merchants = Seq("Uber", "Flipkart", "PVR Cinemas", "Blue Tokai Coffee", "Netflix India", "DMart", "BookMyShow", "Indian Oil Petrol Pump", "Café Coffee Day", "Amazon India", "MakeMyTrip")
    val outputDir = if (args.nonEmpty) args(0) else "data"

    new File(outputDir).mkdirs()

    // Generate 1000 authorizations
    case class Auth(txnId: String, amount: Double, merchant: String, authDate: String)

    val auths = (0 until 1000).map { i =>
      val txnId = f"TXN_$i%06d"
      val amount = BigDecimal(5 + random.nextDouble() * 495).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
      val merchant = merchants(random.nextInt(merchants.size))
      val hour = random.nextInt(24)
      val authDate = f"2024-11-15 $hour%02d:00:00"
      Auth(txnId, amount, merchant, authDate)
    }

    // Generate settlements with intentional discrepancies
    case class Settlement(txnId: String, amount: Double, merchant: String, settleDate: String)

    val settlements = auths.flatMap { auth =>
      val roll = random.nextDouble()

      if (roll < 0.85) {
        // EXACT MATCH (85%)
        val days = 1 + random.nextInt(3)
        Some(Settlement(auth.txnId, auth.amount, auth.merchant, f"2024-11-${15 + days}%02d"))
      } else if (roll < 0.92) {
        // AMOUNT MISMATCH (7%) - differs by up to 15%
        val variance = 1.0 + (random.nextDouble() * 0.3 - 0.15)
        val settleAmount = BigDecimal(auth.amount * variance).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
        val days = 1 + random.nextInt(3)
        Some(Settlement(auth.txnId, settleAmount, auth.merchant, f"2024-11-${15 + days}%02d"))
      } else if (roll < 0.96) {
        // ORPHANED AUTH (4%) - no settlement
        None
      } else {
        // ORPHANED SETTLEMENT (4%) - unknown txn_id
        val fakeId = s"UNKNOWN_${100000 + random.nextInt(900000)}"
        val fakeAmount = BigDecimal(5 + random.nextDouble() * 495).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
        Some(Settlement(fakeId, fakeAmount, auth.merchant, "2024-11-18"))
      }
    }

    // Write authorizations.csv
    val authWriter = new PrintWriter(new File(s"$outputDir/authorizations.csv"))
    authWriter.println("txn_id,amount,merchant,auth_date")
    auths.foreach(a => authWriter.println(s"${a.txnId},${a.amount},${a.merchant},${a.authDate}"))
    authWriter.close()

    // Write settlements.csv
    val settleWriter = new PrintWriter(new File(s"$outputDir/settlements.csv"))
    settleWriter.println("txn_id,amount,merchant,settle_date")
    settlements.foreach(s => settleWriter.println(s"${s.txnId},${s.amount},${s.merchant},${s.settleDate}"))
    settleWriter.close()

    println(s"Generated ${auths.size} authorizations and ${settlements.size} settlements")
    println(s"Files saved to $outputDir/ folder")
  }
}