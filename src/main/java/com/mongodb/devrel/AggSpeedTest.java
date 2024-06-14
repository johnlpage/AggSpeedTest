package com.mongodb.devrel;

import org.bson.Document;
import org.bson.RawBsonDocument;
import java.util.concurrent.ThreadLocalRandom;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;

import javax.print.Doc;

public class AggSpeedTest {
    private static final Logger logger = LoggerFactory.getLogger(AggSpeedTest.class);
    public static ArrayList<Object> example_keys;

    private static ArrayList<Object> fetchKeys(Document keyConfig) {
        ArrayList<Object> rval = new ArrayList<Object>();

        MongoClient mongoClient = MongoClients.create(keyConfig.getString("uri"));
        MongoDatabase database = mongoClient.getDatabase(keyConfig.getString("database"));
        MongoCollection<Document> collection = database.getCollection(keyConfig.getString("collection"));
        AggregateIterable<Document> keys = collection.aggregate(keyConfig.getList("pipeline", Document.class));
        int count = 0;
        String fieldName = keyConfig.getString("field");
        for (Document key : keys) {
            rval.add(key.get(fieldName));
        }
        logger.info("Fetched " + rval.size() + " example keys");
        return rval;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -jar mongo-agg-speedtest.jar <config.json>");
            return;
        }
        LogManager.getLogManager().reset();
        Document testConfig;

        try {
            String configString = new String(Files.readAllBytes(Paths.get(args[0])));
            testConfig = Document.parse(configString);
        } catch (Exception ex) {
            logger.error("ERROR IN CONFIG" + ex.getMessage());
            return;
        }
        logger.info("Fetching values to query by");
        example_keys = fetchKeys(testConfig.get("query_values", Document.class));

        logger.info(testConfig.toJson());

        logger.info("Connecting to MongoDB...");
        try (MongoClient mongoClient = MongoClients.create(testConfig.getString("uri"))) {
            MongoDatabase database = mongoClient.getDatabase(testConfig.getString("database"));
            MongoCollection<RawBsonDocument> collection = database.getCollection(testConfig.getString("collection"),
                    RawBsonDocument.class);
            logger.info("Connected to database: " + testConfig.getString("database"));
            int numberOfThreads = testConfig.getInteger("threads");
            ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
            int totalCalls = testConfig.getInteger("calls");
            int callsPerThread = totalCalls / numberOfThreads;
            logger.info("Starting Timing");
            Date startTime = new Date();
            for (int i = 0; i < numberOfThreads; i++) {
                executorService.submit(
                        new DatabaseTask(collection, testConfig.getList("pipeline", Document.class), callsPerThread, i == 0 ));
            }
            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            Date endTime = new Date();
            long timeTaken = endTime.getTime() - startTime.getTime();
            logger.info("Time: " + timeTaken + " ms Operations/s = " + (totalCalls * 1000 / timeTaken));

        } catch (Exception e) {

            logger.error("An error occurred while connecting to MongoDB", e);
        }
    }
}

class DatabaseTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseTask.class);
    private MongoCollection<RawBsonDocument> collection;
    private int callsPerThread;
    private List<Document> pipeline;
    private boolean report;

    class QueryValue {
        QueryValue(String keyName, Document doc) {
            this.keyName = keyName;
            this.doc = doc;
        }

        public String keyName;
        public Document doc;
    }

    private QueryValue Walk(Document d) {
        for (String k : d.keySet()) {
            Object o = d.get(k);
            if (o.toString().equals("<<VALUE>>")) {
                return new QueryValue(k, d);
            }
            if (o instanceof Document) {
                QueryValue child = Walk((Document) o);
                if (child != null)
                    return child;
            }
        }
        return null;
    }

    public DatabaseTask(MongoCollection<RawBsonDocument> collection, List<Document> pipeline, int callsPerThread, boolean report) {
        this.collection = collection;
        this.pipeline = pipeline;
        this.callsPerThread = callsPerThread;
        this.report = report;
    }

    @Override
    public void run() {
        //logger.info("Running database task on thread: " + Thread.currentThread().getName());
        //logger.info("Processing " + callsPerThread + " calls");

        // First the thread makes a deep copy of the pipeline by conversion to JSON
        List<Document> privatePipeline = new ArrayList<Document>();
        QueryValue keyField = null;
        for (Document stage : this.pipeline) {
            Document newStage = Document.parse(stage.toJson());
            privatePipeline.add(newStage);

            QueryValue qv = Walk(newStage);
            if (qv != null) {
                keyField = qv;
            }
        }

        for (int x = 0; x < callsPerThread; x++) {
            try {
                if (keyField != null) {
                    int randomNum = ThreadLocalRandom.current().nextInt(0, AggSpeedTest.example_keys.size());
                    Object newValue = AggSpeedTest.example_keys.get(randomNum);
                    keyField.doc.put(keyField.keyName, newValue);
                }
                AggregateIterable<RawBsonDocument> docs = collection.aggregate(privatePipeline);
                int count = 0;
                for (RawBsonDocument doc : docs) {
                    count++;
                }
                if (this.report && x % 1000 == 0) {
                    logger.info("" + x + " of " + callsPerThread + " completed");
                }
            } catch (Throwable e) {
                logger.error(e.getMessage());
            }
        }
        // Perform database operations here
    }
}
