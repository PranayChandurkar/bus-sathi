package com.example.bussathi;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;


public class DatabaseHelper extends SQLiteOpenHelper {

    // ── Database constants ──────────────────────────────────────────────
    private static final String DATABASE_NAME = "CampusBus.db";
    private static final int DATABASE_VERSION = 3; // Upgraded to v3 for dynamic Schedule Periods and period-specific timetable

    // ── Bus constants ───────────────────────────────────────────────────
    public static final String BUS_1 = "BUS_1";
    public static final String BUS_2 = "BUS_2";

    // ── Schedule Period constants ───────────────────────────────────────
    public static final String PERIOD_MORNING = "morning";
    public static final String PERIOD_EVENING = "evening";

    // ── Direction constants ─────────────────────────────────────────────
    public static final String DIR_TO_KATHORA = "TO_KATHORA";
    public static final String DIR_TO_COLLEGE = "TO_COLLEGE";

    // ── Table: admins ───────────────────────────────────────────────────
    public static final String TABLE_ADMINS = "admins";
    public static final String COL_ADMIN_ID = "id";
    public static final String COL_ADMIN_USERNAME = "username";
    public static final String COL_ADMIN_PASSWORD = "password";

    // ── Table: stops ────────────────────────────────────────────────────
    public static final String TABLE_STOPS = "stops";
    public static final String COL_STOP_ID = "id";
    public static final String COL_STOP_NAME = "stop_name";
    public static final String COL_STOP_ORDER = "stop_order";

    // ── Table: periods ──────────────────────────────────────────────────
    public static final String TABLE_PERIODS = "periods";
    public static final String COL_PERIOD_ID = "id";
    public static final String COL_PERIOD_NAME = "name";
    public static final String COL_PERIOD_START = "start_time";
    public static final String COL_PERIOD_END = "end_time";

    // ── Table: schedules ────────────────────────────────────────────────
    public static final String TABLE_SCHEDULES = "schedules";
    public static final String COL_SCHED_ID = "id";
    public static final String COL_SCHED_BUS = "bus_number";
    public static final String COL_SCHED_PERIOD_ID = "schedule_period_id";
    public static final String COL_SCHED_PERIOD = "schedule_period";
    public static final String COL_SCHED_STOP_ID = "stop_id";
    public static final String COL_SCHED_DIRECTION = "direction";
    public static final String COL_SCHED_ARRIVAL = "arrival_time";
    public static final String COL_SCHED_DEPARTURE = "departure_time";

    // ── SQL: create tables ──────────────────────────────────────────────
    private static final String CREATE_TABLE_ADMINS =
            "CREATE TABLE " + TABLE_ADMINS + " (" +
                    COL_ADMIN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_ADMIN_USERNAME + " TEXT UNIQUE NOT NULL, " +
                    COL_ADMIN_PASSWORD + " TEXT NOT NULL)";

    private static final String CREATE_TABLE_STOPS =
            "CREATE TABLE " + TABLE_STOPS + " (" +
                    COL_STOP_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_STOP_NAME + " TEXT NOT NULL, " +
                    COL_STOP_ORDER + " INTEGER NOT NULL)";

    private static final String CREATE_TABLE_PERIODS =
            "CREATE TABLE " + TABLE_PERIODS + " (" +
                    COL_PERIOD_ID + " TEXT PRIMARY KEY, " +
                    COL_PERIOD_NAME + " TEXT NOT NULL, " +
                    COL_PERIOD_START + " TEXT NOT NULL, " +
                    COL_PERIOD_END + " TEXT NOT NULL)";

    private static final String CREATE_TABLE_SCHEDULES =
            "CREATE TABLE " + TABLE_SCHEDULES + " (" +
                    COL_SCHED_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_SCHED_BUS + " TEXT NOT NULL DEFAULT 'BUS_1', " +
                    COL_SCHED_PERIOD_ID + " TEXT NOT NULL DEFAULT 'morning', " +
                    COL_SCHED_PERIOD + " TEXT NOT NULL DEFAULT 'morning', " +
                    COL_SCHED_STOP_ID + " INTEGER NOT NULL, " +
                    COL_SCHED_DIRECTION + " TEXT NOT NULL, " +
                    COL_SCHED_ARRIVAL + " TEXT NOT NULL, " +
                    COL_SCHED_DEPARTURE + " TEXT NOT NULL, " +
                    "FOREIGN KEY(" + COL_SCHED_STOP_ID + ") REFERENCES " +
                    TABLE_STOPS + "(" + COL_STOP_ID + "))";

    // ═══════════════════════════════════════════════════════════════════
    // Constructor & lifecycle
    // ═══════════════════════════════════════════════════════════════════

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.execSQL("PRAGMA foreign_keys = ON");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_ADMINS);
        db.execSQL(CREATE_TABLE_STOPS);
        db.execSQL(CREATE_TABLE_PERIODS);
        db.execSQL(CREATE_TABLE_SCHEDULES);

        // Insert default admin credentials
        ContentValues cv = new ContentValues();
        cv.put(COL_ADMIN_USERNAME, "admin");
        cv.put(COL_ADMIN_PASSWORD, "admin123");
        db.insert(TABLE_ADMINS, null, cv);

        // Insert default schedule periods
        insertDefaultPeriods(db);
    }

    private void insertDefaultPeriods(SQLiteDatabase db) {
        ContentValues m = new ContentValues();
        m.put(COL_PERIOD_ID, "morning");
        m.put(COL_PERIOD_NAME, "Morning");
        m.put(COL_PERIOD_START, "10:00 AM");
        m.put(COL_PERIOD_END, "11:00 AM");
        db.insertWithOnConflict(TABLE_PERIODS, null, m, SQLiteDatabase.CONFLICT_IGNORE);

        ContentValues e = new ContentValues();
        e.put(COL_PERIOD_ID, "evening");
        e.put(COL_PERIOD_NAME, "Evening");
        e.put(COL_PERIOD_START, "05:00 PM");
        e.put(COL_PERIOD_END, "06:00 PM");
        db.insertWithOnConflict(TABLE_PERIODS, null, e, SQLiteDatabase.CONFLICT_IGNORE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_SCHEDULES + " ADD COLUMN " + COL_SCHED_BUS + " TEXT NOT NULL DEFAULT 'BUS_1'");
                db.execSQL("ALTER TABLE " + TABLE_SCHEDULES + " ADD COLUMN " + COL_SCHED_PERIOD + " TEXT NOT NULL DEFAULT 'morning'");
                db.delete(TABLE_SCHEDULES, null, null);
            } catch (Exception e) {
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_SCHEDULES);
                db.execSQL(CREATE_TABLE_SCHEDULES);
            }
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PERIODS + " (" +
                        COL_PERIOD_ID + " TEXT PRIMARY KEY, " +
                        COL_PERIOD_NAME + " TEXT NOT NULL, " +
                        COL_PERIOD_START + " TEXT NOT NULL, " +
                        COL_PERIOD_END + " TEXT NOT NULL)");
                db.execSQL("ALTER TABLE " + TABLE_SCHEDULES + " ADD COLUMN " + COL_SCHED_PERIOD_ID + " TEXT NOT NULL DEFAULT 'morning'");
            } catch (Exception ignored) {}
            insertDefaultPeriods(db);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Admin methods
    // ═══════════════════════════════════════════════════════════════════

    public boolean checkAdminLogin(String username, String password) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_ADMINS +
                        " WHERE " + COL_ADMIN_USERNAME + " = ? AND " +
                        COL_ADMIN_PASSWORD + " = ?",
                new String[]{username, password});
        boolean valid = cursor.getCount() > 0;
        cursor.close();
        return valid;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Stop CRUD
    // ═══════════════════════════════════════════════════════════════════

    public long addStop(String name, int order) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_STOP_NAME, name);
        cv.put(COL_STOP_ORDER, order);
        return db.insert(TABLE_STOPS, null, cv);
    }

    public List<Stop> getAllStops() {
        List<Stop> stops = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_STOPS + " ORDER BY " + COL_STOP_ORDER + " ASC",
                null);
        while (cursor.moveToNext()) {
            Stop s = new Stop();
            s.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_STOP_ID));
            s.name = cursor.getString(cursor.getColumnIndexOrThrow(COL_STOP_NAME));
            s.order = cursor.getInt(cursor.getColumnIndexOrThrow(COL_STOP_ORDER));
            stops.add(s);
        }
        cursor.close();
        return stops;
    }

    public Stop getStopById(int id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_STOPS + " WHERE " + COL_STOP_ID + " = ?",
                new String[]{String.valueOf(id)});
        Stop s = null;
        if (cursor.moveToFirst()) {
            s = new Stop();
            s.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_STOP_ID));
            s.name = cursor.getString(cursor.getColumnIndexOrThrow(COL_STOP_NAME));
            s.order = cursor.getInt(cursor.getColumnIndexOrThrow(COL_STOP_ORDER));
        }
        cursor.close();
        return s;
    }

    public int updateStop(int id, String name, int order) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_STOP_NAME, name);
        cv.put(COL_STOP_ORDER, order);
        return db.update(TABLE_STOPS, cv,
                COL_STOP_ID + " = ?",
                new String[]{String.valueOf(id)});
    }

    public void deleteStop(int stopId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_SCHEDULES,
                    COL_SCHED_STOP_ID + " = ?",
                    new String[]{String.valueOf(stopId)});
            db.delete(TABLE_STOPS,
                    COL_STOP_ID + " = ?",
                    new String[]{String.valueOf(stopId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Schedule Period CRUD
    // ═══════════════════════════════════════════════════════════════════

    public List<SchedulePeriod> getAllPeriods() {
        List<SchedulePeriod> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_PERIODS + " ORDER BY " + COL_PERIOD_ID + " ASC", null);
        while (cursor.moveToNext()) {
            SchedulePeriod p = new SchedulePeriod();
            p.id = cursor.getString(cursor.getColumnIndexOrThrow(COL_PERIOD_ID));
            p.name = cursor.getString(cursor.getColumnIndexOrThrow(COL_PERIOD_NAME));
            p.startTime = cursor.getString(cursor.getColumnIndexOrThrow(COL_PERIOD_START));
            p.endTime = cursor.getString(cursor.getColumnIndexOrThrow(COL_PERIOD_END));
            list.add(p);
        }
        cursor.close();
        if (list.isEmpty()) {
            insertDefaultPeriods(getWritableDatabase());
            return getAllPeriods();
        }
        return list;
    }

    public SchedulePeriod getPeriod(String id) {
        if (id == null) return null;
        String normId = id.toLowerCase().trim();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_PERIODS + " WHERE LOWER(" + COL_PERIOD_ID + ") = ?",
                new String[]{normId});
        SchedulePeriod period = null;
        if (cursor.moveToFirst()) {
            period = new SchedulePeriod();
            period.id = cursor.getString(cursor.getColumnIndexOrThrow(COL_PERIOD_ID));
            period.name = cursor.getString(cursor.getColumnIndexOrThrow(COL_PERIOD_NAME));
            period.startTime = cursor.getString(cursor.getColumnIndexOrThrow(COL_PERIOD_START));
            period.endTime = cursor.getString(cursor.getColumnIndexOrThrow(COL_PERIOD_END));
        }
        cursor.close();
        return period;
    }

    public int updatePeriodTimes(String id, String startTime, String endTime) {
        if (id == null) return 0;
        String normId = id.toLowerCase().trim();
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_PERIOD_START, startTime);
        cv.put(COL_PERIOD_END, endTime);
        return db.update(TABLE_PERIODS, cv,
                "LOWER(" + COL_PERIOD_ID + ") = ?",
                new String[]{normId});
    }

    public long saveOrUpdatePeriod(String id, String name, String startTime, String endTime) {
        if (id == null) return -1;
        String normId = id.toLowerCase().trim();
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_PERIOD_ID, normId);
        cv.put(COL_PERIOD_NAME, name);
        cv.put(COL_PERIOD_START, startTime);
        cv.put(COL_PERIOD_END, endTime);
        return db.insertWithOnConflict(TABLE_PERIODS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Schedule CRUD (Two Buses & Period-Specific Timetables)
    // ═══════════════════════════════════════════════════════════════════

    /** CREATE – Add a schedule entry with bus number and schedule period. */
    public long addSchedule(String busNumber, String schedulePeriod, int stopId,
                            String direction, String arrivalTime, String departureTime) {
        SQLiteDatabase db = getWritableDatabase();
        String bus = busNumber != null ? busNumber : BUS_1;
        String period = schedulePeriod != null ? schedulePeriod.toLowerCase().trim() : PERIOD_MORNING;

        ContentValues cv = new ContentValues();
        cv.put(COL_SCHED_BUS, bus);
        cv.put(COL_SCHED_PERIOD_ID, period);
        cv.put(COL_SCHED_PERIOD, period);
        cv.put(COL_SCHED_STOP_ID, stopId);
        cv.put(COL_SCHED_DIRECTION, direction);
        cv.put(COL_SCHED_ARRIVAL, arrivalTime);
        cv.put(COL_SCHED_DEPARTURE, departureTime);
        return db.insert(TABLE_SCHEDULES, null, cv);
    }

    /** Backward-compatible overload defaulting to BUS_1 and MORNING. */
    public long addSchedule(int stopId, String direction,
                            String arrivalTime, String departureTime) {
        return addSchedule(BUS_1, PERIOD_MORNING, stopId, direction, arrivalTime, departureTime);
    }

    /** READ – retrieve schedule for a given bus, period, stop, and direction. */
    public Schedule getSchedule(String busNumber, String schedulePeriod, int stopId, String direction) {
        SQLiteDatabase db = getReadableDatabase();
        String bus = busNumber != null ? busNumber : BUS_1;
        String period = schedulePeriod != null ? schedulePeriod.trim() : PERIOD_MORNING;
        String pLower = period.toLowerCase();
        String pUpper = period.toUpperCase();

        Cursor cursor = db.rawQuery(
                "SELECT s.*, st." + COL_STOP_NAME +
                        " FROM " + TABLE_SCHEDULES + " s " +
                        " INNER JOIN " + TABLE_STOPS + " st ON s." + COL_SCHED_STOP_ID +
                        " = st." + COL_STOP_ID +
                        " WHERE s." + COL_SCHED_BUS + " = ? AND (" +
                        "LOWER(s." + COL_SCHED_PERIOD_ID + ") = ? OR LOWER(s." + COL_SCHED_PERIOD + ") = ? OR UPPER(s." + COL_SCHED_PERIOD + ") = ?) AND s." +
                        COL_SCHED_STOP_ID + " = ? AND s." +
                        COL_SCHED_DIRECTION + " = ?",
                new String[]{bus, pLower, pLower, pUpper, String.valueOf(stopId), direction});
        Schedule schedule = null;
        if (cursor.moveToFirst()) {
            schedule = parseScheduleCursor(cursor);
        }
        cursor.close();
        return schedule;
    }

    /** Backward-compatible overload defaulting to BUS_1 and MORNING. */
    public Schedule getSchedule(int stopId, String direction) {
        return getSchedule(BUS_1, PERIOD_MORNING, stopId, direction);
    }

    /** READ – retrieve all schedule entries for a specific bus, period, and direction. */
    public List<Schedule> getSchedules(String busNumber, String schedulePeriod, String direction) {
        List<Schedule> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String bus = busNumber != null ? busNumber : BUS_1;
        String period = schedulePeriod != null ? schedulePeriod.trim() : PERIOD_MORNING;
        String pLower = period.toLowerCase();
        String pUpper = period.toUpperCase();

        Cursor cursor = db.rawQuery(
                "SELECT s.*, st." + COL_STOP_NAME + ", st." + COL_STOP_ORDER +
                        " FROM " + TABLE_SCHEDULES + " s " +
                        " INNER JOIN " + TABLE_STOPS + " st ON s." + COL_SCHED_STOP_ID +
                        " = st." + COL_STOP_ID +
                        " WHERE s." + COL_SCHED_BUS + " = ? AND (" +
                        "LOWER(s." + COL_SCHED_PERIOD_ID + ") = ? OR LOWER(s." + COL_SCHED_PERIOD + ") = ? OR UPPER(s." + COL_SCHED_PERIOD + ") = ?) AND s." +
                        COL_SCHED_DIRECTION + " = ?" +
                        " ORDER BY st." + COL_STOP_ORDER + " ASC",
                new String[]{bus, pLower, pLower, pUpper, direction});
        while (cursor.moveToNext()) {
            list.add(parseScheduleCursor(cursor));
        }
        cursor.close();
        return list;
    }

    /** Backward-compatible overload for direction. */
    public List<Schedule> getSchedulesForDirection(String direction) {
        return getSchedules(BUS_1, PERIOD_MORNING, direction);
    }

    /** READ – retrieve all schedule entries for admin timetable view. */
    public List<Schedule> getAllSchedules() {
        List<Schedule> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT s.*, st." + COL_STOP_NAME + ", st." + COL_STOP_ORDER +
                        " FROM " + TABLE_SCHEDULES + " s " +
                        " INNER JOIN " + TABLE_STOPS + " st ON s." + COL_SCHED_STOP_ID +
                        " = st." + COL_STOP_ID +
                        " ORDER BY s." + COL_SCHED_BUS + ", s." + COL_SCHED_PERIOD_ID +
                        ", s." + COL_SCHED_DIRECTION + ", st." + COL_STOP_ORDER + " ASC",
                null);
        while (cursor.moveToNext()) {
            list.add(parseScheduleCursor(cursor));
        }
        cursor.close();
        return list;
    }

    public int updateSchedule(int scheduleId, String arrivalTime, String departureTime) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_SCHED_ARRIVAL, arrivalTime);
        cv.put(COL_SCHED_DEPARTURE, departureTime);
        return db.update(TABLE_SCHEDULES, cv,
                COL_SCHED_ID + " = ?",
                new String[]{String.valueOf(scheduleId)});
    }

    public int deleteSchedule(int scheduleId) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_SCHEDULES,
                COL_SCHED_ID + " = ?",
                new String[]{String.valueOf(scheduleId)});
    }

    public void clearSchedules() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_SCHEDULES, null, null);
    }

    public boolean hasStops() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_STOPS, null);
        boolean result = false;
        if (cursor.moveToFirst()) {
            result = cursor.getInt(0) > 0;
        }
        cursor.close();
        return result;
    }

    public boolean hasSchedules() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_SCHEDULES, null);
        boolean result = false;
        if (cursor.moveToFirst()) {
            // Full multi-bus matrix: 2 buses * 2 periods * 2 directions * 5 stops = 40 schedules
            result = cursor.getInt(0) >= 40;
        }
        cursor.close();
        return result;
    }

    public int getStopIdByName(String name) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COL_STOP_ID + " FROM " + TABLE_STOPS +
                        " WHERE " + COL_STOP_NAME + " = ?",
                new String[]{name});
        int id = -1;
        if (cursor.moveToFirst()) {
            id = cursor.getInt(0);
        }
        cursor.close();
        return id;
    }

    private Schedule parseScheduleCursor(Cursor cursor) {
        Schedule sc = new Schedule();
        sc.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SCHED_ID));
        sc.busNumber = cursor.getString(cursor.getColumnIndexOrThrow(COL_SCHED_BUS));
        int periodIdIndex = cursor.getColumnIndex(COL_SCHED_PERIOD_ID);
        if (periodIdIndex != -1 && !cursor.isNull(periodIdIndex)) {
            sc.schedulePeriodId = cursor.getString(periodIdIndex);
        } else {
            sc.schedulePeriodId = cursor.getString(cursor.getColumnIndexOrThrow(COL_SCHED_PERIOD));
        }
        sc.schedulePeriod = sc.schedulePeriodId;
        sc.stopId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SCHED_STOP_ID));
        sc.direction = cursor.getString(cursor.getColumnIndexOrThrow(COL_SCHED_DIRECTION));
        sc.arrivalTime = cursor.getString(cursor.getColumnIndexOrThrow(COL_SCHED_ARRIVAL));
        sc.departureTime = cursor.getString(cursor.getColumnIndexOrThrow(COL_SCHED_DEPARTURE));
        sc.stopName = cursor.getString(cursor.getColumnIndexOrThrow(COL_STOP_NAME));
        return sc;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Inner model classes
    // ═══════════════════════════════════════════════════════════════════

    public static class SchedulePeriod {
        public String id = "morning";
        public String name = "Morning";
        public String startTime = "10:00 AM";
        public String endTime = "11:00 AM";

        public SchedulePeriod() {}

        public SchedulePeriod(String id, String name, String startTime, String endTime) {
            this.id = id;
            this.name = name;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public String getDisplayText() {
            return name + " (" + startTime + " – " + endTime + ")";
        }

        @Override
        public String toString() {
            return getDisplayText();
        }
    }

    public static class Stop {
        public int id;
        public String name;
        public int order;

        @Override
        public String toString() {
            return name;
        }
    }

    public static class Schedule {
        public int id;
        public String busNumber = BUS_1;
        public String schedulePeriodId = PERIOD_MORNING;
        public String schedulePeriod = PERIOD_MORNING;
        public int stopId;
        public String direction;
        public String arrivalTime;
        public String departureTime;
        public String stopName;
    }
}
