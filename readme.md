### Set up
1. Use preferred IDE(like IntelliJ) to import the project using main pom file(delta-lake)
2. Have JDK 8 installed on your machine.
3. Scala: Either install 2.12.x locally and point your IDE to it. Or simply use an IDE plugin for Scala.
4. No need to install Spark or anything else. Could install maven to run the project using `mvn clean install`. Currently, this command compiles the project but is running into a dependency issue to be able to run Unit tests. As such, simply use the IDE to run and debug Unit tests.

### How code is structured
Ideally, simply running the available Unit tests should be enough to understand exactly what the code is doing. 
The code is set up such that the first time csv is available to the code it will simply use that data to create and set 
up the initial delta table. After that, any subsequent csv files will invoke the upsert set operations. There are two 
unit tests to demonstrate this approach. The second unit test will also print the plan so we can know what exactly is going on. 
There are proper comments in unit tests to ascertain that correct records are being deleted, 
inserted and modified.

The code first computes relevant dataframes with records to delete and update/insert and then uses delta lake API to 
do the final merges using this data and original delta lake table. 

Granularity is defined by an optional map. Keys are field names and for each field there can be multiple values. From this map, 
we develop dynamic `where` and `merge` clauses to scale it to any situation.

There is only one public method in the `TradesModelUtils.scala` file which can be used in an orchestration process to enable 
the functionality of csv drops. We unit test this method once and we unit test 

### Schema assumptions and migration layer
There is currently no migration layer for schema. We assume that each time csv will have same schema.

### External vs managed table
We've chosen the route of unmanaged/external table for a variety of reasons: 

1. Enables separate processes for data and schema deployments.
2. Enables separate security access for data and schema.
3. Enables our own convention to be followed when creating data layers.

### Misc.
Although for `price` field we could've used `Decimal` type but I avoided that for now and used Double because I did not want 
to assume a certain precision and scale.

### Some learning references (not gone through all of them though)
1. https://docs.delta.io/latest/index.html
2. https://www.youtube.com/watch?v=7ewmcdrylsA 
3. https://www.databricks.com/blog/2020/09/29/diving-into-delta-lake-dml-internals-update-delete-merge.html 
4. https://docs.delta.io/latest/delta-resources.html#delta-lake-transaction-log-specification