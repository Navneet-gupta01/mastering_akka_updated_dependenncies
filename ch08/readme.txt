If Some error is happening with serialization and deserialization just delete the old collections from cassandra db. 
Like  issue with dateFormat. Due to diffrent format stored in the records earlier..
cqlsh>
cqlsh> DROP KEYSPACE akka;
cqlsh> DROP KEYSPACE akka_snapshot;
cqlsh> drop keyspace bookstore;


and try again
