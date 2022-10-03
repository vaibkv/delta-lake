package org.vaibhav.lake.test_utils

import org.apache.spark.sql.SparkSession
import org.junit.runner.RunWith
import org.scalatest.exceptions.TestFailedException
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TestSparkSessionTest extends AnyFlatSpec with TestSparkSession {

  lazy val spark: SparkSession = sparkSession

  behavior of "hive and some basic sparksql operations"

  it should "correctly run spark sql" in {
    //Arrange
    val dropDb = "DROP DATABASE IF EXISTS XYZ CASCADE"
    val createDb = "CREATE DATABASE IF NOT EXISTS XYZ"
    val createTbl = "create table if not exists XYZ.someTable (col1 int, col2 string)"
    val DropTbl = "drop table if exists XYZ.someTable"

    //Act
    spark.sql(dropDb)
    spark.sql(createDb)
    spark.sql(createTbl)

    //Assert
    spark.catalog.setCurrentDatabase("XYZ")
    spark.sql("show tables").count() shouldBe 1L
    spark.sql(DropTbl)

    spark.sql("show tables").count() shouldBe 0L

    //Clean up
    spark.sql(dropDb)
  }

  behavior of "mkDataFrame"

  it should "be able to create dataframe from list of case class objects" in {
    import spark.implicits._

    //Arrange, Act
    val df = mkDataFrame(
      Items(1),
      Items(2),
      Items(3),
      Items(4),
      Items(55)
    )

    //Assert
    df.count() shouldBe 5L
    df.columns shouldBe List("Values")
  }

  behavior of "checkThatDataFramesAreEqual"

  it should "error out in case dataframe and sequence are not equal" in {
    import spark.implicits._

    //Arrange
    val df = mkDataFrame(
      Items(1),
      Items(2),
      Items(2)
    )

    val itemsSeq = Seq(
      Items(1),
      Items(1),
      Items(2)
    )

    //Act
    val throwBadThroughput = intercept[TestFailedException] {
      checkThatDataFramesAreEqual[Items](df, itemsSeq)
    }

    //Assert
    throwBadThroughput.message contains "Expected and Actual results did not match"
  }

  it should "error out if dataframe and seq cardinality are different" in {
    import spark.implicits._

    //Arrange
    val df = mkDataFrame(
      Items(1),
      Items(2)
    )

    val itemsSeq = Seq(
      Items(1),
      Items(1),
      Items(2)
    )

    //Act
    val throwBadThroughput = intercept[TestFailedException] {
      checkThatDataFramesAreEqual[Items](df, itemsSeq)
    }

    //Assert
    throwBadThroughput.message contains "Expected and Actual results did not match"
  }

  it should "error out if dataframe and seq have column values switched" in {
    import spark.implicits._

    //Arrange
    val df = mkDataFrame(
      ItemsMultiple(1, 1),
      ItemsMultiple(2, 1)
    )

    val itemsSeq = Seq(
      ItemsMultiple(1, 1),
      ItemsMultiple(1, 2)
    )

    //Act
    val throwBadThroughput = intercept[TestFailedException] {
      checkThatDataFramesAreEqual[ItemsMultiple](df, itemsSeq)
    }

    //Assert
    throwBadThroughput.message contains "Expected and Actual results did not match"
  }

  behavior of "spark.sparkContext.getConf"
  it should "have read the configuration from conf/spark-default.conf" in {
    spark.sparkContext.getConf.get("spark.serializer") should be("org.apache.spark.serializer.KryoSerializer")
    spark.sparkContext.getConf.get("spark.shuffle.compress") should be("false")
    spark.sparkContext.getConf.get("spark.sql.inMemoryColumnarStorage.compressed") should be("false")
    spark.sparkContext.getConf.get("spark.sql.legacy.parquet.datetimeRebaseModeInRead") should be("CORRECTED")
    spark.sparkContext.getConf.get("spark.ui.enabled") should be("false")
  }
}

case class Items(Values: Int)

case class ItemsMultiple(Id1: Int, Id2: Int)