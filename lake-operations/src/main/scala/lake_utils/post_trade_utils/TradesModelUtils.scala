package lake_utils.post_trade_utils

import lake_utils.post_trade_utils.Models.{TradesRecord, tradesSchema}
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession, functions}
import io.delta.tables._
import scala.language.postfixOps

case class TradesModelUtils(tradesTableName: String,
                            tradesTableLocation: String,
                            isIdentifiedViaLocaton: Boolean,
                            newCsvLocation: String,
                            fieldsAndValuesForGranularityOpt: Option[Map[String, Seq[String]]],
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

    val deleteDf = if(fieldsAndValuesForGranularityOpt.isDefined) {
      deltaTableTrades.toDF.where(getWhereClause).as(MAIN_TRADES_DATA).join(dfTradesCurrent.as(CURRENT_DATA),
        tradesSchema.fieldNames.toSeq, "left_anti").select("mainTradesData.*").
      withColumn("status", functions.lit("deleteOp"))
    } else deltaTableTrades.toDF

    val updateInsertDf = if (fieldsAndValuesForGranularityOpt.isDefined) {
      deltaTableTrades.toDF.where(getWhereClause).as(MAIN_TRADES_DATA).join(dfTradesCurrent.as(CURRENT_DATA),
        tradesSchema.fieldNames.toSeq, "right").select("currentData.*").
        withColumn("status", functions.lit("updateOp"))
    } else dfTradesCurrent

    val changesDf = updateInsertDf.union(deleteDf)

    deltaTableTrades
      .as(MAIN_TRADES_DATA)
      .merge(changesDf.as("changesDf"),
        getMergeCondition(MAIN_TRADES_DATA, "changesDf"))
      .whenMatched("changesDf.status = 'updateOp' ").updateAll()
      .whenMatched("changesDf.status = 'deleteOp' ").delete()
      .whenNotMatched().insertAll().execute()
    deltaTableTrades
  }

  //todo: correction
  /*
    where (book = 'Blue' or book = 'Arcesium') AND (date = '7/1/2020')
  */
  private def getWhereClause: String = {
    fieldsAndValuesForGranularityOpt.get.flatMap {
      case (fieldName: String, fieldValues: Seq[String]) =>
        fieldValues.map(fieldVal => s"$fieldName = '$fieldVal'") //use OR here
    }.mkString(" AND ")
  }

  private def getMergeCondition(mainData: String, currentData: String): String = {
    tradesSchema.fieldNames.map(fieldName => s"$mainData.$fieldName = $currentData.$fieldName").mkString(" and ")
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

  //todo: Remove any hardcodings like 'header' and 'sep' values below, which should be config based input
  private def getCurrentTradesFileDelta: Dataset[TradesRecord] = {
    spark.read
      .option("header", true)
      .option("sep", ",")
      .schema(tradesSchema)
      .csv(newCsvLocation).as[TradesRecord]
  }
}
