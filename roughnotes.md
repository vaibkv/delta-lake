Delta Lake notes from readings/videos -

- file compaction while writing changes
- two configs for retention of files and transaction logs
- checkpoint parquet after every 10 logs
- optimistic isolation: two simultaneous writers
- schema enforcement is there by default
- schema evolution (merge schema while writing or spark config) but only changes that will allow previous data to be still readable (adding columns, upcasts, etc.) OR overwriteSchema - might not be able to read previous data
- any changes are at file level only. No in-place changes within a single file of course. So update does two scans - find data according to predicate, re-write files updated and mark the original ones as deleted. Can use paritioning and better predicates to narrow down scanning
- update and delete do not actuall delete files but mark them as deleted in new txn log so that time travel is possible.
- Merge under the hood: scan 1 is inner join between maintable and newtable to find files that need updating. scan 2 is outer join on these files and newtable for actual udpate and insertion. Performance upgrade by increasing shuffle partitions, broadcasting, z-order, compaction, etc. Use better predicates
- Inflation: checkpoints, compaction, etc.