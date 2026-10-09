package com.example.bussathi;

import android.content.Context;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.PersistentCacheSettings;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class FirestoreRepository {

    private static final String TAG = "FirestoreRepository";

    // Firestore Collection Names
    public static final String COLLECTION_STOPS = "stops";
    public static final String COLLECTION_SCHEDULES = "schedules";
    public static final String COLLECTION_PERIODS = "periods";

    // Bus constants
    public static final String BUS_1 = "BUS_1";
    public static final String BUS_2 = "BUS_2";

    // Schedule Period constants
    public static final String PERIOD_MORNING = "morning";
    public static final String PERIOD_EVENING = "evening";

    // Direction constants
    public static final String DIR_TO_KATHORA = "TO_KATHORA";
    public static final String DIR_TO_COLLEGE = "TO_COLLEGE";

    // Message required when google-services.json is missing
    public static final String MSG_FIREBASE_SETUP_REQUIRED =
            "Firebase Console setup is required. Download google-services.json and place it in app/.";

    private static FirestoreRepository instance;
    private FirebaseFirestore db;
    private boolean isAvailable = false;

    // ── Generic Callback Interface ──────────────────────────────────────
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    // ── Period Model for Firestore ───────────────────────────────────────
    public static class FirestorePeriod {
        public String id;
        public String name;
        public String startTime;
        public String endTime;

        public FirestorePeriod() {}

        public FirestorePeriod(String id, String name, String startTime, String endTime) {
            this.id = id;
            this.name = name;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public String getDisplayText() {
            if (startTime != null && !startTime.isEmpty() && endTime != null && !endTime.isEmpty()) {
                return name + " (" + startTime + " – " + endTime + ")";
            }
            return name != null ? name : "";
        }

        @Override
        public String toString() {
            return getDisplayText();
        }
    }

    // ── Stop Model for Firestore ─────────────────────────────────────────
    public static class FirestoreStop {
        public String id;
        public String stopName;
        public int stopOrder;

        public FirestoreStop() {}

        public FirestoreStop(String id, String stopName, int stopOrder) {
            this.id = id;
            this.stopName = stopName;
            this.stopOrder = stopOrder;
        }

        @Override
        public String toString() {
            return stopName;
        }
    }

    // ── Schedule Model for Firestore (Two Buses & Period-Specific Timings) ───────────
    public static class FirestoreSchedule {
        public String id;
        public String busNumber = BUS_1;
        public String schedulePeriodId = PERIOD_MORNING;
        public String schedulePeriod = PERIOD_MORNING;
        public String stopId;
        public String stopName;
        public String direction;
        public String arrivalTime;
        public String departureTime;

        public FirestoreSchedule() {}

        public FirestoreSchedule(String id, String busNumber, String schedulePeriodId,
                                 String stopId, String direction,
                                 String arrivalTime, String departureTime) {
            this.id = id;
            this.busNumber = busNumber;
            this.schedulePeriodId = schedulePeriodId != null ? schedulePeriodId.toLowerCase() : PERIOD_MORNING;
            this.schedulePeriod = this.schedulePeriodId;
            this.stopId = stopId;
            this.direction = direction;
            this.arrivalTime = arrivalTime;
            this.departureTime = departureTime;
        }
    }

    // ── Singleton Access ────────────────────────────────────────────────
    public static synchronized FirestoreRepository getInstance(Context context) {
        if (instance == null) {
            instance = new FirestoreRepository(context.getApplicationContext());
        }
        return instance;
    }

    private FirestoreRepository(Context context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context);
            }

            if (!FirebaseApp.getApps(context).isEmpty()) {
                db = FirebaseFirestore.getInstance();

                FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                        .build();
                db.setFirestoreSettings(settings);

                isAvailable = true;
                Log.d(TAG, "Firebase Firestore initialized with offline cache.");
            } else {
                Log.w(TAG, "No default FirebaseApp found. google-services.json may be missing.");
                isAvailable = false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Firebase initialization exception: " + e.getMessage());
            isAvailable = false;
        }
    }

    public boolean isAvailable() {
        return isAvailable && db != null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // STOPS CRUD
    // ═══════════════════════════════════════════════════════════════════

    public void addStop(String name, int order, Callback<String> callback) {
        if (!isAvailable()) {
            callback.onError(new IllegalStateException(MSG_FIREBASE_SETUP_REQUIRED));
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("stopName", name);
        data.put("stopOrder", order);

        db.collection(COLLECTION_STOPS)
                .add(data)
                .addOnSuccessListener(docRef -> callback.onSuccess(docRef.getId()))
                .addOnFailureListener(callback::onError);
    }

    public void getAllStops(Callback<List<FirestoreStop>> callback) {
        if (!isAvailable()) {
            callback.onError(new IllegalStateException(MSG_FIREBASE_SETUP_REQUIRED));
            return;
        }

        db.collection(COLLECTION_STOPS)
                .orderBy("stopOrder", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<FirestoreStop> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        list.add(parseStop(doc));
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onError);
    }

    public ListenerRegistration listenToStops(Callback<List<FirestoreStop>> callback) {
        if (!isAvailable()) {
            callback.onError(new IllegalStateException(MSG_FIREBASE_SETUP_REQUIRED));
            return null;
        }

        return db.collection(COLLECTION_STOPS)
                .orderBy("stopOrder", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }

                    if (snapshots != null) {
                        List<FirestoreStop> list = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : snapshots) {
                            list.add(parseStop(doc));
                        }
                        callback.onSuccess(list);
                    }
                });
    }

    public void updateStop(String stopId, String name, int order, Callback<Void> callback) {
        if (!isAvailable()) {
            callback.onError(new IllegalStateException(MSG_FIREBASE_SETUP_REQUIRED));
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("stopName", name);
        updates.put("stopOrder", order);

        db.collection(COLLECTION_STOPS)
                .document(stopId)
                .update(updates)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    public void deleteStop(String stopId, Callback<Void> callback) {
        if (!isAvailable()) {
            callback.onError(new IllegalStateException(MSG_FIREBASE_SETUP_REQUIRED));
            return;
        }

        // Query all schedules referencing this stopId
        db.collection(COLLECTION_SCHEDULES)
                .whereEqualTo("stopId", stopId)
                .get()
                .addOnSuccessListener(schedules -> {
                    WriteBatch batch = db.batch();

                    for (QueryDocumentSnapshot doc : schedules) {
                        batch.delete(doc.getReference());
                    }

                    DocumentReference stopRef = db.collection(COLLECTION_STOPS).document(stopId);
                    batch.delete(stopRef);

                    batch.commit()
                            .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PERIODS CRUD
    // ═══════════════════════════════════════════════════════════════════

    public void getAllPeriods(Callback<List<FirestorePeriod>> callback) {
        if (!isAvailable()) {
            callback.onError(new IllegalStateException(MSG_FIREBASE_SETUP_REQUIRED));
            return;
        }

        db.collection(COLLECTION_PERIODS)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<FirestorePeriod> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        list.add(parsePeriod(doc));
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onError);
    }

    public ListenerRegistration listenToPeriods(Callback<List<FirestorePeriod>> callback) {
        if (!isAvailable()) {
            callback.onError(new IllegalStateException(MSG_FIREBASE_SETUP_REQUIRED));
            return null;
        }

        return db.collection(COLLECTION_PERIODS)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snapshots != null) {
                        List<FirestorePeriod> list = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : snapshots) {
                            list.add(parsePeriod(doc));
                        }
                        callback.onSuccess(list);
                    }
                });
    }

    public void updatePeriodTimes(String periodId, String startTime, String endTime, Callback<Void> callback) {
        if (!isAvailable()) {
            if (callback != null) callback.onError(new IllegalStateException(MSG_FIREBASE_SETUP_REQUIRED));
            return;
        }

        String normId = periodId != null ? periodId.toLowerCase().trim() : PERIOD_MORNING;
        Map<String, Object> updates = new HashMap<>();
        updates.put("startTime", startTime);
        updates.put("endTime", endTime);

        db.collection(COLLECTION_PERIODS)
                .document(normId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e);
                });
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCHEDULES CRUD (Two Buses & Period-Specific Timings)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * CREATE or UPDATE – Add or update timetable record for bus, period, stop, and direction.
     * Prevents duplicate records for the exact same Period + Bus + Direction + Stop.
     */
    public void saveOrUpdateSchedule(String busNumber, String schedulePeriod,
                                     String stopId, String direction,
                                     String arrivalTime, String departureTime,
                                     Callback<String> callback) {
        if (!isAvailable()) {
            if (callback != null) callback.onError(new IllegalStateException(MSG_FIREBASE_SETUP_REQUIRED));
            return;
        }

        final String bus = busNumber != null ? busNumber : BUS_1;
        final String period = schedulePeriod != null ? schedulePeriod.toLowerCase().trim() : PERIOD_MORNING;

        db.collection(COLLECTION_SCHEDULES)
                .whereEqualTo("busNumber", bus)
                .whereEqualTo("stopId", stopId)
                .whereEqualTo("direction", direction)
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("busNumber", bus);
                    data.put("schedulePeriodId", period);
                    data.put("schedulePeriod", period);
                    data.put("stopId", stopId);
                    data.put("direction", direction);
                    data.put("arrivalTime", arrivalTime);
                    data.put("departureTime", departureTime);

                    DocumentSnapshot existingDoc = null;
                    for (DocumentSnapshot doc : querySnapshots) {
                        String pId = doc.getString("schedulePeriodId");
                        String pName = doc.getString("schedulePeriod");
                        if ((pId != null && pId.equalsIgnoreCase(period)) ||
                            (pName != null && pName.equalsIgnoreCase(period))) {
                            existingDoc = doc;
                            break;
                        }
                    }

                    if (existingDoc != null) {
                        final String finalDocId = existingDoc.getId();
                        existingDoc.getReference()
                                .set(data)
                                .addOnSuccessListener(aVoid -> {
                                    if (callback != null) callback.onSuccess(finalDocId);
                                })
                                .addOnFailureListener(e -> {
                                    if (callback != null) callback.onError(e);
                                });
                    } else {
                        String docId = bus + "_" + period + "_" + stopId + "_" + direction;
                        db.collection(COLLECTION_SCHEDULES)
                                .document(docId)
                                .set(data)
                                .addOnSuccessListener(aVoid -> {
                                    if (callback != null) callback.onSuccess(docId);
                                })
                                .addOnFailureListener(e -> {
                                    if (callback != null) callback.onError(e);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e);
                });
    }

    /** Backward-compatible overload defaulting to BUS_1 and morning. */
    public void saveOrUpdateSchedule(String stopId, String direction,
                                     String arrivalTime, String departureTime,
                                     Callback<String> callback) {
        saveOrUpdateSchedule(BUS_1, PERIOD_MORNING, stopId, direction, arrivalTime, departureTime, callback);
    }

    /**
     * READ – Query schedule for specific bus, period, stop, and direction.
     */
    public void getSchedule(String busNumber, String schedulePeriod,
                            String stopId, String direction,
                            Callback<FirestoreSchedule> callback) {
        if (!isAvailable()) {
            callback.onError(new IllegalStateException(MSG_FIREBASE_SETUP_REQUIRED));
            return;
        }

        final String pNorm = schedulePeriod != null ? schedulePeriod.toLowerCase().trim() : PERIOD_MORNING;

        db.collection(COLLECTION_SCHEDULES)
                .whereEqualTo("busNumber", busNumber)
                .whereEqualTo("stopId", stopId)
                .whereEqualTo("direction", direction)
                .get()
                .addOnSuccessListener(snapshots -> {
                    FirestoreSchedule match = null;
                    for (DocumentSnapshot doc : snapshots) {
                        String pId = doc.getString("schedulePeriodId");
                        String pName = doc.getString("schedulePeriod");
                        if ((pId != null && pId.equalsIgnoreCase(pNorm)) ||
                            (pName != null && pName.equalsIgnoreCase(pNorm))) {
                            match = parseSchedule(doc);
                            break;
                        }
                    }
                    callback.onSuccess(match);
                })
                .addOnFailureListener(callback::onError);
    }

    /** Backward-compatible overload defaulting to BUS_1 and morning. */
    public void getSchedule(String stopId, String direction, Callback<FirestoreSchedule> callback) {
        getSchedule(BUS_1, PERIOD_MORNING, stopId, direction, callback);
    }

    /**
     * REAL-TIME READ – Listen for real-time schedule updates for specific bus, period, stop, and direction.
     */
    public ListenerRegistration listenToSchedule(String busNumber, String schedulePeriod,
                                                 String stopId, String direction,
                                                 Callback<FirestoreSchedule> callback) {
        if (!isAvailable()) {
            callback.onError(new IllegalStateException(MSG_FIREBASE_SETUP_REQUIRED));
            return null;
        }

        final String pNorm = schedulePeriod != null ? schedulePeriod.toLowerCase().trim() : PERIOD_MORNING;

        return db.collection(COLLECTION_SCHEDULES)
                .whereEqualTo("busNumber", busNumber)
                .whereEqualTo("stopId", stopId)
                .whereEqualTo("direction", direction)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    FirestoreSchedule match = null;
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots) {
                            String pId = doc.getString("schedulePeriodId");
                            String pName = doc.getString("schedulePeriod");
                            if ((pId != null && pId.equalsIgnoreCase(pNorm)) ||
                                (pName != null && pName.equalsIgnoreCase(pNorm))) {
                                match = parseSchedule(doc);
                                break;
                            }
                        }
                    }
                    callback.onSuccess(match);
                });
    }

    /** Backward-compatible overload. */
    public ListenerRegistration listenToSchedule(String stopId, String direction,
                                                 Callback<FirestoreSchedule> callback) {
        return listenToSchedule(BUS_1, PERIOD_MORNING, stopId, direction, callback);
    }

    /**
     * READ – Fetch schedules filtered by bus, period, and direction.
     */
    public void getSchedules(String busNumber, String schedulePeriod,
                             String direction, Callback<List<FirestoreSchedule>> callback) {
        if (!isAvailable()) {
            callback.onError(new IllegalStateException(MSG_FIREBASE_SETUP_REQUIRED));
            return;
        }

        final String pNorm = schedulePeriod != null ? schedulePeriod.toLowerCase().trim() : PERIOD_MORNING;

        db.collection(COLLECTION_SCHEDULES)
                .whereEqualTo("busNumber", busNumber)
                .whereEqualTo("direction", direction)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<FirestoreSchedule> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        String pId = doc.getString("schedulePeriodId");
                        String pName = doc.getString("schedulePeriod");
                        if ((pId != null && pId.equalsIgnoreCase(pNorm)) ||
                            (pName != null && pName.equalsIgnoreCase(pNorm))) {
                            list.add(parseSchedule(doc));
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onError);
    }

    public void getSchedulesForDirection(String direction, Callback<List<FirestoreSchedule>> callback) {
        getSchedules(BUS_1, PERIOD_MORNING, direction, callback);
    }

    /**
     * REAL-TIME READ – Listen for schedules filtered by bus, period, and direction.
     */
    public ListenerRegistration listenToSchedules(String busNumber, String schedulePeriod,
                                                  String direction,
                                                  Callback<List<FirestoreSchedule>> callback) {
        if (!isAvailable()) {
            callback.onError(new IllegalStateException(MSG_FIREBASE_SETUP_REQUIRED));
            return null;
        }

        final String pNorm = schedulePeriod != null ? schedulePeriod.toLowerCase().trim() : PERIOD_MORNING;

        return db.collection(COLLECTION_SCHEDULES)
                .whereEqualTo("busNumber", busNumber)
                .whereEqualTo("direction", direction)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<FirestoreSchedule> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            String pId = doc.getString("schedulePeriodId");
                            String pName = doc.getString("schedulePeriod");
                            if ((pId != null && pId.equalsIgnoreCase(pNorm)) ||
                                (pName != null && pName.equalsIgnoreCase(pNorm))) {
                                list.add(parseSchedule(doc));
                            }
                        }
                    }
                    callback.onSuccess(list);
                });
    }

    public ListenerRegistration listenToSchedulesForDirection(String direction,
                                                              Callback<List<FirestoreSchedule>> callback) {
        return listenToSchedules(BUS_1, PERIOD_MORNING, direction, callback);
    }

    /** READ ALL – Fetch all schedules for admin timetable view. */
    public void getAllSchedules(Callback<List<FirestoreSchedule>> callback) {
        if (!isAvailable()) {
            callback.onError(new IllegalStateException(MSG_FIREBASE_SETUP_REQUIRED));
            return;
        }

        db.collection(COLLECTION_SCHEDULES)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<FirestoreSchedule> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        list.add(parseSchedule(doc));
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onError);
    }

    public void deleteSchedule(String scheduleId, Callback<Void> callback) {
        if (!isAvailable()) {
            callback.onError(new IllegalStateException(MSG_FIREBASE_SETUP_REQUIRED));
            return;
        }

        db.collection(COLLECTION_SCHEDULES)
                .document(scheduleId)
                .delete()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEMO DATA SEEDING (2 Buses × 2 Periods × 2 Directions = Complete)
    // ═══════════════════════════════════════════════════════════════════

    public void seedDemoDataIfEmpty(Callback<Boolean> callback) {
        if (!isAvailable()) {
            if (callback != null) callback.onError(new IllegalStateException(MSG_FIREBASE_SETUP_REQUIRED));
            return;
        }

        // Check if multi-bus schedules already exist in Firestore
        db.collection(COLLECTION_SCHEDULES)
                .whereEqualTo("busNumber", BUS_1)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots.isEmpty()) {
                        Log.d(TAG, "Firestore schedules missing multi-bus data. Seeding/syncing multi-bus demo data...");
                        seedDemoData(callback);
                    } else {
                        // Check if periods exist
                        seedPeriodsIfEmpty();
                        Log.d(TAG, "Firestore already has multi-bus schedules.");
                        if (callback != null) callback.onSuccess(false);
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e);
                });
    }

    private void seedPeriodsIfEmpty() {
        db.collection(COLLECTION_PERIODS).document("morning").get().addOnSuccessListener(doc -> {
            if (!doc.exists()) {
                WriteBatch pBatch = db.batch();
                DocumentReference p1Ref = db.collection(COLLECTION_PERIODS).document("morning");
                Map<String, Object> p1 = new HashMap<>();
                p1.put("id", "morning");
                p1.put("name", "Morning");
                p1.put("startTime", "10:00 AM");
                p1.put("endTime", "11:00 AM");
                pBatch.set(p1Ref, p1);

                DocumentReference p2Ref = db.collection(COLLECTION_PERIODS).document("evening");
                Map<String, Object> p2 = new HashMap<>();
                p2.put("id", "evening");
                p2.put("name", "Evening");
                p2.put("startTime", "05:00 PM");
                p2.put("endTime", "06:00 PM");
                pBatch.set(p2Ref, p2);

                pBatch.commit();
            }
        });
    }

    private void seedDemoData(Callback<Boolean> callback) {
        String[] stopNames = {"College", "Stop A", "Stop B", "Stop C", "Kathora"};

        // Timings matrix for 5 stops
        // 1. Bus 1 Morning
        String[] b1mArrK = {"10:00 AM", "10:10 AM", "10:20 AM", "10:30 AM", "10:40 AM"};
        String[] b1mDepK = {"10:05 AM", "10:12 AM", "10:22 AM", "10:32 AM", "10:40 AM"};
        String[] b1mArrC = {"11:10 AM", "11:00 AM", "10:55 AM", "10:50 AM", "10:45 AM"};
        String[] b1mDepC = {"11:10 AM", "11:02 AM", "10:57 AM", "10:52 AM", "10:45 AM"};

        // 2. Bus 2 Morning
        String[] b2mArrK = {"10:15 AM", "10:25 AM", "10:35 AM", "10:45 AM", "10:55 AM"};
        String[] b2mDepK = {"10:20 AM", "10:27 AM", "10:37 AM", "10:47 AM", "10:55 AM"};
        String[] b2mArrC = {"11:25 AM", "11:18 AM", "11:12 AM", "11:05 AM", "11:00 AM"};
        String[] b2mDepC = {"11:25 AM", "11:20 AM", "11:14 AM", "11:07 AM", "11:00 AM"};

        // 3. Bus 1 Evening
        String[] b1eArrK = {"05:00 PM", "05:10 PM", "05:20 PM", "05:30 PM", "05:40 PM"};
        String[] b1eDepK = {"05:05 PM", "05:12 PM", "05:22 PM", "05:32 PM", "05:40 PM"};
        String[] b1eArrC = {"06:10 PM", "06:00 PM", "05:55 PM", "05:50 PM", "05:45 PM"};
        String[] b1eDepC = {"06:10 PM", "06:02 PM", "05:57 PM", "05:52 PM", "05:45 PM"};

        // 4. Bus 2 Evening
        String[] b2eArrK = {"05:15 PM", "05:25 PM", "05:35 PM", "05:45 PM", "05:55 PM"};
        String[] b2eDepK = {"05:20 PM", "05:27 PM", "05:37 PM", "05:47 PM", "05:55 PM"};
        String[] b2eArrC = {"06:25 PM", "06:18 PM", "06:12 PM", "06:05 PM", "06:00 PM"};
        String[] b2eDepC = {"06:25 PM", "06:20 PM", "06:14 PM", "06:07 PM", "06:00 PM"};

        db.collection(COLLECTION_STOPS).get().addOnSuccessListener(stopsSnap -> {
            WriteBatch batch = db.batch();

            // Seed periods
            DocumentReference p1Ref = db.collection(COLLECTION_PERIODS).document("morning");
            Map<String, Object> p1 = new HashMap<>();
            p1.put("id", "morning");
            p1.put("name", "Morning");
            p1.put("startTime", "10:00 AM");
            p1.put("endTime", "11:00 AM");
            batch.set(p1Ref, p1);

            DocumentReference p2Ref = db.collection(COLLECTION_PERIODS).document("evening");
            Map<String, Object> p2 = new HashMap<>();
            p2.put("id", "evening");
            p2.put("name", "Evening");
            p2.put("startTime", "05:00 PM");
            p2.put("endTime", "06:00 PM");
            batch.set(p2Ref, p2);

            List<String> stopIdList = new ArrayList<>();

            if (stopsSnap.isEmpty()) {
                // Create stops if none exist
                for (int i = 0; i < stopNames.length; i++) {
                    DocumentReference stopRef = db.collection(COLLECTION_STOPS).document();
                    Map<String, Object> stopData = new HashMap<>();
                    stopData.put("stopName", stopNames[i]);
                    stopData.put("stopOrder", i);
                    batch.set(stopRef, stopData);
                    stopIdList.add(stopRef.getId());
                }
            } else {
                // Reuse existing stops sorted by stopOrder to prevent duplicate stops
                List<FirestoreStop> existingStops = new ArrayList<>();
                for (QueryDocumentSnapshot doc : stopsSnap) {
                    existingStops.add(parseStop(doc));
                }
                Collections.sort(existingStops, (a, b) -> Integer.compare(a.stopOrder, b.stopOrder));
                for (FirestoreStop s : existingStops) {
                    stopIdList.add(s.id);
                }
            }

            int count = Math.min(stopIdList.size(), stopNames.length);
            for (int i = 0; i < count; i++) {
                String stopId = stopIdList.get(i);

                addSchedBatch(batch, stopId, BUS_1, PERIOD_MORNING, DIR_TO_KATHORA, b1mArrK[i], b1mDepK[i]);
                addSchedBatch(batch, stopId, BUS_1, PERIOD_MORNING, DIR_TO_COLLEGE, b1mArrC[i], b1mDepC[i]);

                addSchedBatch(batch, stopId, BUS_2, PERIOD_MORNING, DIR_TO_KATHORA, b2mArrK[i], b2mDepK[i]);
                addSchedBatch(batch, stopId, BUS_2, PERIOD_MORNING, DIR_TO_COLLEGE, b2mArrC[i], b2mDepC[i]);

                addSchedBatch(batch, stopId, BUS_1, PERIOD_EVENING, DIR_TO_KATHORA, b1eArrK[i], b1eDepK[i]);
                addSchedBatch(batch, stopId, BUS_1, PERIOD_EVENING, DIR_TO_COLLEGE, b1eArrC[i], b1eDepC[i]);

                addSchedBatch(batch, stopId, BUS_2, PERIOD_EVENING, DIR_TO_KATHORA, b2eArrK[i], b2eDepK[i]);
                addSchedBatch(batch, stopId, BUS_2, PERIOD_EVENING, DIR_TO_COLLEGE, b2eArrC[i], b2eDepC[i]);
            }

            batch.commit()
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Multi-bus demo data seeded to Cloud Firestore.");
                        if (callback != null) callback.onSuccess(true);
                    })
                    .addOnFailureListener(e -> {
                        if (callback != null) callback.onError(e);
                    });
        }).addOnFailureListener(e -> {
            if (callback != null) callback.onError(e);
        });
    }

    private void addSchedBatch(WriteBatch batch, String stopId, String bus, String period,
                               String dir, String arr, String dep) {
        String periodNorm = period.toLowerCase();
        String docId = bus + "_" + periodNorm + "_" + stopId + "_" + dir;
        DocumentReference ref = db.collection(COLLECTION_SCHEDULES).document(docId);
        Map<String, Object> data = new HashMap<>();
        data.put("busNumber", bus);
        data.put("schedulePeriodId", periodNorm);
        data.put("schedulePeriod", periodNorm);
        data.put("stopId", stopId);
        data.put("direction", dir);
        data.put("arrivalTime", arr);
        data.put("departureTime", dep);
        batch.set(ref, data);
    }

    // ── Helper Parsers ──────────────────────────────────────────────────
    private FirestoreStop parseStop(DocumentSnapshot doc) {
        FirestoreStop s = new FirestoreStop();
        s.id = doc.getId();
        s.stopName = doc.getString("stopName");
        Long order = doc.getLong("stopOrder");
        s.stopOrder = order != null ? order.intValue() : 0;
        return s;
    }

    private FirestorePeriod parsePeriod(DocumentSnapshot doc) {
        FirestorePeriod p = new FirestorePeriod();
        p.id = doc.getId();
        p.name = doc.getString("name");
        p.startTime = doc.getString("startTime");
        p.endTime = doc.getString("endTime");
        return p;
    }

    private FirestoreSchedule parseSchedule(DocumentSnapshot doc) {
        FirestoreSchedule s = new FirestoreSchedule();
        s.id = doc.getId();
        s.busNumber = doc.getString("busNumber");
        if (s.busNumber == null) s.busNumber = BUS_1;
        s.schedulePeriodId = doc.getString("schedulePeriodId");
        if (s.schedulePeriodId == null) {
            s.schedulePeriodId = doc.getString("schedulePeriod");
        }
        if (s.schedulePeriodId == null) s.schedulePeriodId = PERIOD_MORNING;
        s.schedulePeriod = s.schedulePeriodId;
        s.stopId = doc.getString("stopId");
        s.direction = doc.getString("direction");
        s.arrivalTime = doc.getString("arrivalTime");
        s.departureTime = doc.getString("departureTime");
        return s;
    }
}
