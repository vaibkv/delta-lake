package lake_utils.post_trade_utils

import lake_utils.post_trade_utils.Models.{TradesRecord, tradesSchema}
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import io.delta.tables._

case class TradesModelUtils(tradesTableName: String,
                            tradesTableLocation: String,
                            isIdentifiedViaLocaton: Boolean,
                            newCsvLocation: String,
                            fieldsForGranularity: Option[Seq[String]],
                            spark: SparkSession) {
  import spark.implicits._

  def upsertTrades: Dataset[TradesRecord] = {

    val dfTradesCurrent = getCurrentTradesFileDelta.toDF()
    val (deltaTableTrades, isNewTable) = getDeltaTableTrades(dfTradesCurrent)

    if (isNewTable) deltaTableTrades else {
      /*
      deltaTableTrades
        .as("mainTradesData")
        .merge(
          dfTradesCurrent.as("currentData"),
          "people.id = updates.id")
        .whenMatched
        .updateExpr(
          Map(
            "id" -> "updates.id",
            "firstName" -> "updates.firstName",
            "middleName" -> "updates.middleName",
            "lastName" -> "updates.lastName",
            "gender" -> "updates.gender",
            "birthDate" -> "updates.birthDate",
            "ssn" -> "updates.ssn",
            "salary" -> "updates.salary"
          ))
        .whenNotMatched
        .insertExpr(
          Map(
            "id" -> "updates.id",
            "firstName" -> "updates.firstName",
            "middleName" -> "updates.middleName",
            "lastName" -> "updates.lastName",
            "gender" -> "updates.gender",
            "birthDate" -> "updates.birthDate",
            "ssn" -> "updates.ssn",
            "salary" -> "updates.salary"
          ))
        .execute()
       */

      null
    }
    null
  }

  private def getDeltaTableTrades(currentData: DataFrame): (DeltaTable, Boolean) = {
    tradesTableExists match {
      case true =>
        (if (isIdentifiedViaLocaton) DeltaTable.forPath(spark, tradesTableLocation)
          else DeltaTable.forName(tradesTableName), false)
      case false => (createTradesTable(currentData), true)
    }
  }

  private def createTradesTable(df: DataFrame): DeltaTable = {
    df.write.format("delta").mode("overwrite").save(tradesTableLocation)
    DeltaTable.createOrReplace(spark)
      .tableName(tradesTableName)
      .addColumns(tradesSchema)
      .location(tradesTableLocation)
      .execute()
  }

  private def tradesTableExists: Boolean = {
    if (isIdentifiedViaLocaton) DeltaTable.isDeltaTable(spark, tradesTableLocation)
      else DeltaTable.isDeltaTable(tradesTableName)
  }

  private def getCurrentTradesFileDelta: Dataset[TradesRecord] = {
    spark.read
      .option("header", true)
      .option("sep", ",")
      .schema(tradesSchema)
      .csv(newCsvLocation).as[TradesRecord]
  }
}
