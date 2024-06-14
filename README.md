Create an Atlas Cluster to use - for any Benchmarkin an M30 is a miniumum as it has dedicated resources.

Download the Sampel Atlas Data

curl https://atlas-education.s3.amazonaws.com/sampledata.archive -o sampledata.archive
mongorestore --archive=sampledata.archive

Plan is to test vector queries per second, retrieving the top N for each.
Data Will have pre-computed vectors we will sue these vectors to query so no AI required.
Effectively a 'Like this' type query

//We will also Synthesize data by taking each record and creating multiepl copies very slightly changin gth evectors for each.

M30 has 4GB free RAM - 2048

embedded_movies has 3483 records.

Embedding  size is 1536 - 6KB required

600,000 Vectors



```
use sample_mflix
doc = db.movies.findOne()
bsonsize(doc)
21925
bsonsize(doc.plot_embedding)
20399
```

Index size is 8K * 4 bytes (only 32 bits used)

8K per 2048 vector
We have 4GB Free

~400,000 vectors

M30 has 4GB Cache - so let's assume we have 3GB of Movies
Movies without Embeddings is ~6GB
SO multipley 500X

Total number of Movies (and Embeddings) = 


Simple query speed test - fetch random by ID


Reading all _id values
Have: 599076
 644.821978757146 queries/s

 
