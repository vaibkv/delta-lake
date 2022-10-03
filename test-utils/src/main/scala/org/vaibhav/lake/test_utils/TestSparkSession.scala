package org.vaibhav.lake.test_utils

import ch.qos.logback.classic.Level
import org.apache.spark.sql.catalyst.expressions.RowOrdering.isOrderable
import org.apache.spark.sql.{DataFrame, Encoder, SparkSession}
import org.scalatest._
import org.slf4j.LoggerFactory

import java.io.ByteArrayOutputStream

object TestSparkSession {

  lazy val parentSparkSession: SparkSession = createSparkSession
  var warnOnceDone = false

  /**
   * Unit test optimizations - https://spark.apache.org/docs/latest/configuration.html#available-properties
   */
  def createSparkSession: SparkSession = {
    SparkSession.builder()
      .enableHiveSupport()
      .config("spark.broadcast.compress", value = false)
      .config("spark.driver.bindAddress", value = "127.0.0.1")
      .config("spark.driver.host", "localhost")
      .config("spark.executor.memory", "500mb")
      .config("spark.kryo.unsafe", value = true)
      .config("spark.kryoserializer.buffer", "64k")
      .config("spark.rdd.compress", value = false)
      .config("spark.serializer.objectStreamReset", -1)
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .config("spark.shuffle.compress", value = false)
      .config("spark.sql.inMemoryColumnarStorage.compressed", value = false)
      .config("spark.sql.legacy.parquet.datetimeRebaseModeInRead", "CORRECTED")
      .config("spark.sql.optimizer.maxIterations", 28)
      .config("spark.sql.optimizer.metadataOnly ", value = false)
      .config("spark.sql.orc.compression.codec ", value = false)
      .config("spark.sql.parquet.writeLegacyFormat", value = true)
      .config("spark.sql.shuffle.partitions", 4)
      .config("spark.ui.enabled", value = false)
      .master("local[*]")
      .appName("Test Suite")
      .getOrCreate()
  }
}

//extends BeforeAndAfterAll
trait TestSparkSession {
  this: Suite =>

  private var sparkSessionInternal: SparkSession = _

  def setLogLevels(level: Level, loggers: Seq[String]): Map[String, Level] = loggers.map(loggerName => {
    val logger = LoggerFactory.getLogger(loggerName).asInstanceOf[ch.qos.logback.classic.Logger]
    val prevLevel = logger.getLevel
    logger.setLevel(level)
    loggerName -> prevLevel
  }).toMap

  setLogLevels(
    Level.WARN,
    Seq(
      "org.apache.spark", "org.apache.hadoop", "org.apache.parquet", "org.spark_project", "org.eclipse.jetty", "akka",
      "io.netty", "org.apache.http", "com.amazonaws"
    )
  )

  if (!TestSparkSession.warnOnceDone) {
    LoggerFactory.getLogger(this.getClass).warn("\n\n!!! Spark INFO/DEBUG logging has been suppressed. Look in org.vaibhav.lake.testutils.TestSparkSession if you need to enable it. !!!\n\n")
    TestSparkSession.warnOnceDone = true
  }

  implicit protected def sparkSession: SparkSession = {
    if (sparkSessionInternal == null) {
      createSession
    }
    sparkSessionInternal
  }

  def mkDataFrame[T: Encoder](entities: T*)(implicit spark: SparkSession): DataFrame = {
    import spark.implicits._
    entities.toDF
  }

  def checkThatDataFramesAreEqual[A: Encoder](actual: DataFrame, expectedOutput: Seq[A]): Unit = {
    val spark = sparkSession
    import spark.implicits._

    val convertedResultSet = actual.as[A].collect().toSeq

    if (convertedResultSet.size != expectedOutput.size ||
      convertedResultSet.exists(item => !expectedOutput.contains(item)) ||
      expectedOutput.exists(item => !convertedResultSet.contains(item)) ||
      expectedOutput.intersect(convertedResultSet).size != expectedOutput.size
    ) {

      val fields = actual.schema.fields
      val cols = fields.map(f => $"${f.name}")
      val sortableCols = fields.filter(f => isOrderable(f.dataType)).map(f => $"${f.name}")

      val resultDFShow = {
        val outCapture = new ByteArrayOutputStream()
        Console.withOut(outCapture) {
          actual
            .orderBy(sortableCols: _*)
            .show(convertedResultSet.size, truncate = false)
        }
        new String(outCapture.toByteArray)
      }

      val expectedDFShow = {
        val outCapture = new ByteArrayOutputStream()
        Console.withOut(outCapture) {
          spark
            .createDataset(expectedOutput)
            .select(cols: _*)
            .orderBy(sortableCols: _*).
            show(expectedOutput.size, truncate = false)
        }
        new String(outCapture.toByteArray)
      }

      fail(
        s"""
           |Expected and Actual results did not match
           |*** ACTUAL ***
           |$resultDFShow
           |
           |*** EXPECTED ***
           |$expectedDFShow
           """.stripMargin)
    }
  }

  /**
   * Apparently we can not close the child spark session because this will also close the shared SparkContext causing other tests to fail.
   * sparkSessionInternal.close()
   * But we can clear the cache to reduce test suite memory footprint
   */
  private def cleanupSession(): Unit = {
    if (sparkSessionInternal != null) {
      clearCache()
      sparkSessionInternal = null
    }
  }

  private def clearCache(): Unit = {
    if (sparkSessionInternal != null) {
      sparkSessionInternal.sqlContext.clearCache()
      sparkSessionInternal.sparkContext.getPersistentRDDs
        .foreach { case (_, rdd) => rdd.unpersist(true) }
    }
  }

  private def createSession: Unit = {
    sparkSessionInternal = TestSparkSession.parentSparkSession.newSession()
  }
}
