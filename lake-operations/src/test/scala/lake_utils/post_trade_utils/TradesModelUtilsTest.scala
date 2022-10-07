package lake_utils.post_trade_utils

import org.apache.spark.sql.SparkSession
import org.junit.ClassRule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfterAll, PrivateMethodTester}
import org.scalatest.flatspec.AnyFlatSpec
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

  it should "create initial table when first csv is available" in {
    //Arrange
    val tradesTableLocation = temporaryFolder.newFolder().getAbsolutePath
    val csvLocation = this.getClass.getClassLoader.getResource("mock_data.csv").getPath
    val utilsObj = TradesModelUtils("tradesDeltaTable", tradesTableLocation, true, csvLocation, None, spark)

    //Act
    val resultDeltaTable = utilsObj.upsertTrades

    //Assert
    val x = 2
  }
}
