package com.ms.hrmanagement.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.ms.hrmanagement.model.User;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Repository
@SuppressWarnings("null")
public class UserRepository {

    private final Firestore firestore;

    private static final String COLLECTION = "users";

    public UserRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    // Add user
    public String save(User user)
            throws ExecutionException, InterruptedException {

        DocumentReference document = firestore
                .collection(COLLECTION)
                .document(RoleIds.newId(user.getRole()));

        user.setId(document.getId());

        document.set(user).get();

        return user.getId();
    }

    // Find user by ID
    public User findById(String id)
            throws ExecutionException, InterruptedException {

        requireText(id, "Account ID is required");

        DocumentSnapshot document = firestore
                .collection(COLLECTION)
                .document(id)
                .get()
                .get();

        if (!document.exists()) {
            return null;
        }

        return document.toObject(User.class);
    }

    // Get all users
    public List<User> findAll()
            throws ExecutionException, InterruptedException {

        ApiFuture<QuerySnapshot> future = firestore
                .collection(COLLECTION)
                .get();

        List<User> users = new ArrayList<>();

        for (QueryDocumentSnapshot document : future.get().getDocuments()) {
            users.add(document.toObject(User.class));
        }

        return users;
    }

    // Find user by username
    public User findByUsername(String username)
            throws ExecutionException, InterruptedException {

        if (username == null || username.isBlank()) {
            return null;
        }

        Query query = firestore
                .collection(COLLECTION)
                .whereEqualTo("username", username);

        QuerySnapshot snapshot = query.get().get();

        if (snapshot.isEmpty()) {
            return null;
        }

        return snapshot.getDocuments()
                .get(0)
                .toObject(User.class);
    }

    // Find user by email
    public User findByEmail(String email)
            throws ExecutionException, InterruptedException {

        if (email == null || email.isBlank()) {
            return null;
        }

        Query query = firestore
                .collection(COLLECTION)
                .whereEqualTo("email", email);

        QuerySnapshot snapshot = query.get().get();

        if (snapshot.isEmpty()) {
            return null;
        }

        return snapshot.getDocuments()
                .get(0)
                .toObject(User.class);
    }

    // Update
    public void update(User user)
            throws ExecutionException, InterruptedException {

        requireText(user.getId(), "Account ID is required");

        firestore
                .collection(COLLECTION)
                .document(user.getId())
                .set(user)
                .get();
    }

    // Delete
    public void deleteById(String id)
            throws ExecutionException, InterruptedException {

        requireText(id, "Account ID is required");

        firestore
                .collection(COLLECTION)
                .document(id)
                .delete()
                .get();
    }

    private void requireText(String value, String message) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
