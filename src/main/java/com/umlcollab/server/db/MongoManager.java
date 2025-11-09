package com.umlcollab.server.db;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

public class MongoManager {
    private static final String CONNECTION_STRING =
            "mongodb+srv://23pw01:Aniruktha123@cluster0.u735k.mongodb.net/?appName=Cluster0";
    private static final String DATABASE_NAME = "AnirukthaV";
    private static final String COLLECTION_NAME = "india";

    private static MongoClient mongoClient;
    private static MongoDatabase database;

    public static void connect() {
        try {
            mongoClient = MongoClients.create(CONNECTION_STRING);
            database = mongoClient.getDatabase(DATABASE_NAME);
            System.out.println("Connected to MongoDB Atlas: " + DATABASE_NAME);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static MongoDatabase getDatabase() {
        if (database == null) connect();
        return database;
    }

    public static void close() {
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("MongoDB connection closed.");
        }
    }

    // New method to get all documents from 'sample1'
    public static void getAllFromSample1() {
        MongoCollection<Document> collection = getDatabase().getCollection("india");
        FindIterable<Document> documents = collection.find();

        for (Document doc : documents) {
            System.out.println(doc.toJson());
        }
    }
}
