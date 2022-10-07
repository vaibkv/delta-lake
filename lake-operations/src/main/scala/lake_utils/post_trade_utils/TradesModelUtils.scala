package lake_utils.post_trade_utils

import lake_utils.post_trade_utils.Models.{TradesRecord, tradesSchema}
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import io.delta.tables._

case class TradesModelUtils(tradesTableName: String,
                            tradesTableLocation: String,
                            isIdentifiedViaLocaton: Boolean,
                            newCsvLocation: String,
                            fieldsAndValuesForGranularityOpt: Option[Map[String, Option[Seq[String]]]],
                            spark: SparkSession) {
  import spark.implicits._

  def upsertTrades: DeltaTable = {
    val dfTradesCurrent = getCurrentTradesFileDelta.toDF()
    val (deltaTableTrades, isNewTable) = getDeltaTableTrades(dfTradesCurrent)

    if (isNewTable) deltaTableTrades else {
      getMergedTradesDelta(deltaTableTrades, dfTradesCurrent)
    }
  }

  private def getMergedTradesDelta(deltaTableTrades: DeltaTable, dfTradesCurrent: DataFrame): DeltaTable = {
    import Constants._

    val updateInsertMapExpr = getUpdateInsertMapExpr

    deltaTableTrades
      .as(MAIN_TRADES_DATA)
      .merge(
        dfTradesCurrent.as(CURRENT_DATA),
        getMergeCondition(MAIN_TRADES_DATA, CURRENT_DATA))
      .whenMatched
      .updateExpr(updateInsertMapExpr)
      .whenNotMatched
      .insertExpr(updateInsertMapExpr)
      .execute()
    deltaTableTrades
  }

  private def getUpdateInsertMapExpr: Map[String, String] = {
    import Constants._
    tradesSchema.fieldNames.map(fName => {
      fName -> s"$CURRENT_DATA.$fName"
    }).toMap
  }

  private def getMergeCondition(mainData: String, currentData: String): String = {
    if(fieldsAndValuesForGranularityOpt.isEmpty) {
      tradesSchema.fields.map(fieldName => s"$mainData.$fieldName = $currentData.$fieldName").mkString(" and ")
    } else {
      fieldsAndValuesForGranularityOpt.get.map {
        case (fieldName: String, optFieldValues: Option[Seq[String]]) => s"$mainData.$fieldName = $currentData.$fieldName" +
          if(optFieldValues.isEmpty) "" else optFieldValues.get.map(fieldVal => s"$mainData.$fieldName = $fieldVal")
      }.mkString(" and ")
    }
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
