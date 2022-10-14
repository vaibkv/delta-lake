package lake_utils.post_trade_utils

import io.delta.tables.DeltaTable
import lake_utils.post_trade_utils.Models.TradesRecord
import org.apache.spark.sql.SparkSession
import org.junit.ClassRule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfterAll, PrivateMethodTester}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers._
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatestplus.junit.JUnitRunner
import org.vaibhav.lake.test_utils.TestSparkSession

@RunWith(classOf[JUnitRunner])
class TradesModelUtilsTest extends AnyFlatSpec with PrivateMethodTester with TestSparkSession with BeforeAndAfterAll {
  val _temporaryFolder = new TemporaryFolder
  implicit lazy val spark: SparkSession = sparkSession

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    temporaryFolder.create()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    temporaryFolder.delete()
  }

  @ClassRule
  def temporaryFolder: TemporaryFolder = _temporaryFolder

  behavior of "upsertTrades"

  ignore should "create initial table when first csv is available" in {
    //Arrange
    val tableName = "tradesDeltaTable"
    spark.sql(s"DROP TABLE IF EXISTS $tableName").show()
    val tradesTableLocation = temporaryFolder.newFolder().getAbsolutePath
    val csvLocation = this.getClass.getClassLoader.getResource("mock_data.csv").getPath
    val utilsObj = TradesModelUtils(tableName, tradesTableLocation, true, csvLocation, None, spark)

    //Act
    val resultDeltaTable = utilsObj.upsertTrades

    //Assert
    resultDeltaTable.toDF.count() should be(1000L)
    DeltaTable.isDeltaTable(spark, tradesTableLocation) should be(true)
  }

  behavior of "getMergedTradesDelta"

  it should "correctly upsert data for a given book" in {
    import spark.implicits._

    //Arrange
    val tableName = "tradesDeltaTable"
    spark.sql(s"DROP TABLE IF EXISTS $tableName").show()
    val tradesTableLocation = temporaryFolder.newFolder().getAbsolutePath
    val csvLocation = this.getClass.getClassLoader.getResource("mock_data.csv").getPath
    val fieldsAndValuesForGranularityOpt = Some(Map("book" -> Seq("Blue")))
    val utilsObj = TradesModelUtils(tableName, tradesTableLocation, true, csvLocation, fieldsAndValuesForGranularityOpt, spark)
    val resultDeltaTable = utilsObj.upsertTrades

    val blueBookRecords = Seq(
      //records that match exactly with data already present(so should not be deleted, just updated)
      TradesRecord("7/13/2021", "Blue", "Ultrapar Participacoes S.A.", 11327, 62.98),
      TradesRecord("7/13/2021", "Blue", "Blackrock MuniHoldings New Jersey Insured Fund, Inc.", 17282, 80.42),
      TradesRecord("7/13/2021", "Blue", "Acacia Communications, Inc.", 13046, 90.3),
      TradesRecord("7/13/2021", "Blue", "Akebia Therapeutics, Inc.", 18588, 88.87),

      //new records that should be added
      TradesRecord("7/11/2021", "Blue", "SomeOtherSec1", 20, 20.00),
      TradesRecord("7/11/2021", "Blue", "SomeOtherSec2", 30, 30.00),
      TradesRecord("7/11/2021", "Blue", "SomeOtherSec3", 40, 40.00),
      TradesRecord("7/11/2021", "Blue", "SomeOtherSec4", 50, 50.00),
    )
    val dfTradesCurrent = spark.createDataset(blueBookRecords).toDF()
    val getMergedTradesDelta = PrivateMethod[DeltaTable]('getMergedTradesDelta)

    //Testing Optimize
    spark.sql(s"OPTIMIZE $tableName ZORDER BY (book) ").show()

    //Act
    val result = utilsObj invokePrivate getMergedTradesDelta(resultDeltaTable, dfTradesCurrent)

    //Testing Optimize
    spark.sql(s"OPTIMIZE $tableName ZORDER BY (book) ").show()

    //Assert
    //There should be only 8 records with 'Blue' as book
    result.toDF.where("book = 'Blue'").count() should be (8L)

    //'Blue' book records should match exactly with dfTradesCurrent data
    checkThatDataFramesAreEqual[TradesRecord](result.toDF.where("book = 'Blue'"), blueBookRecords)

    //Total records should 1000(original records) - 51('Blue' book records, out of which all but 3 were deleted) + 8(new 'Blue' book records) = 957
    result.toDF.count() should be (957L)
  }

  //todo: more unit tests can be written for: "trade_date "7/11/2021" for all books" and "book "Blue" for trade_date "7/11/2021"".
  //These simply amount to dynamic where clauses that the code already handles.
}
