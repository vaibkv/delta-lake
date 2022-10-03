package lake_utils.post_trade_utils

import org.apache.spark.sql.types.{DoubleType, IntegerType, StringType, StructField, StructType}

object Models {

  val tradesSchema = StructType(Seq(
    StructField("trade_date", StringType, true),
    StructField("book", StringType, true),
    StructField("security", StringType, true),
    StructField("quantity", IntegerType, true),
    StructField("price", DoubleType, true)
  ))
  case class TradesRecord(trade_date: String, book: String, security: String, quantity: Int, price: Double)
}
