package com.ms.hrmanagement.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@SuppressWarnings("null")
public class FirestoreService {

    private final Firestore firestore;

    public FirestoreService(Firestore firestore) {
        this.firestore = firestore;
    }

    // =========================
    // CREATE / SET DOCUMENT
    // =========================

    public void saveDocument(
            String collection,
            String documentId,
            Map<String, Object> data
    ) throws ExecutionException, InterruptedException {

        requireText(collection, "Collection name is required");
        requireText(documentId, "Document ID is required");

        firestore
                .collection(collection)
                .document(documentId)
                .set(data)
                .get();
    }

    // =========================
    // CREATE DOCUMENT
    // Firestore generates the ID automatically
    // =========================

    public String addDocument(
            String collection,
            Map<String, Object> data
    ) throws ExecutionException, InterruptedException {

        requireText(collection, "Collection name is required");

        DocumentReference document =
                firestore.collection(collection).document();

        document.set(data).get();

        return document.getId();
    }

    // =========================
    // READ DOCUMENT
    // =========================

    public Map<String, Object> getDocument(
            String collection,
            String documentId
    ) throws ExecutionException, InterruptedException {

        requireText(collection, "Collection name is required");
        requireText(documentId, "Document ID is required");

        DocumentSnapshot document =
                firestore
                        .collection(collection)
                        .document(documentId)
                        .get()
                        .get();

        if (!document.exists()) {
            return null;
        }

        return document.getData();
    }

    // =========================
    // CHECK DOCUMENT EXISTS
    // =========================

    public boolean documentExists(
            String collection,
            String documentId
    ) throws ExecutionException, InterruptedException {

        requireText(collection, "Collection name is required");
        requireText(documentId, "Document ID is required");

        DocumentSnapshot document =
                firestore
                        .collection(collection)
                        .document(documentId)
                        .get()
                        .get();

        return document.exists();
    }

    // =========================
    // DELETE DOCUMENT
    // =========================

    public void deleteDocument(
            String collection,
            String documentId
    ) throws ExecutionException, InterruptedException {

        requireText(collection, "Collection name is required");
        requireText(documentId, "Document ID is required");

        firestore
                .collection(collection)
                .document(documentId)
                .delete()
                .get();
    }

    // =========================
    // GET COLLECTION
    // =========================

    public QuerySnapshot getCollection(
            String collection
    ) throws ExecutionException, InterruptedException {

        requireText(collection, "Collection name is required");

        ApiFuture<QuerySnapshot> future =
                firestore
                        .collection(collection)
                        .get();

        return future.get();
    }

    // =========================
    // GET FIRESTORE INSTANCE
    // =========================

    public Firestore getFirestore() {
        return firestore;
    }

    private void requireText(String value, String message) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
