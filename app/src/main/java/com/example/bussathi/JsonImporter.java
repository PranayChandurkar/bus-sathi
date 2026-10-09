package com.example.bussathi;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class JsonImporter {

    private static final String TAG = "JsonImporter";
    private static final String ASSET_FILE = "bus_route.json";

    /**
     * Import demo data from the JSON asset into SQLite.
     * Imports stops if stops table is empty, and schedules if schedules table is empty.
     */
    public static void importIfNeeded(Context context, DatabaseHelper dbHelper) {
        try {
            // Read JSON file from assets
            InputStream is = context.getAssets().open(ASSET_FILE);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);

            JSONObject root = new JSONObject(json);

            // ── Import stops if needed ──────────────────────────────
            if (!dbHelper.hasStops()) {
                JSONArray stopsArray = root.getJSONArray("stops");
                for (int i = 0; i < stopsArray.length(); i++) {
                    JSONObject stopObj = stopsArray.getJSONObject(i);
                    String name = stopObj.getString("name");
                    int order = stopObj.getInt("order");
                    dbHelper.addStop(name, order);
                }
                Log.d(TAG, "Imported " + stopsArray.length() + " stops from JSON.");
            }

            // ── Import schedule periods if present ──────────────────
            if (root.has("periods")) {
                JSONArray periodsArray = root.getJSONArray("periods");
                for (int i = 0; i < periodsArray.length(); i++) {
                    JSONObject p = periodsArray.getJSONObject(i);
                    String pId = p.getString("id");
                    String pName = p.getString("name");
                    String pStart = p.getString("start_time");
                    String pEnd = p.getString("end_time");
                    dbHelper.saveOrUpdatePeriod(pId, pName, pStart, pEnd);
                }
                Log.d(TAG, "Imported " + periodsArray.length() + " schedule periods from JSON.");
            }

            // ── Import schedules if needed ──────────────────────────
            if (!dbHelper.hasSchedules()) {
                dbHelper.clearSchedules();
                if (root.has("schedules")) {
                    Object schedVal = root.get("schedules");
                    if (schedVal instanceof JSONArray) {
                        JSONArray schedulesArray = (JSONArray) schedVal;
                        for (int i = 0; i < schedulesArray.length(); i++) {
                            JSONObject entry = schedulesArray.getJSONObject(i);
                            String bus = entry.optString("bus_number", DatabaseHelper.BUS_1);
                            String period = entry.optString("schedule_period_id",
                                    entry.optString("schedule_period", DatabaseHelper.PERIOD_MORNING));
                            String direction = entry.getString("direction");
                            String stopName = entry.getString("stop_name");
                            String arrival = entry.getString("arrival_time");
                            String departure = entry.getString("departure_time");

                            int stopId = dbHelper.getStopIdByName(stopName);
                            if (stopId != -1) {
                                dbHelper.addSchedule(bus, period, stopId, direction, arrival, departure);
                            }
                        }
                        Log.d(TAG, "Imported " + schedulesArray.length() + " multi-bus schedule records from JSON.");
                    }
                }
            }

            Log.d(TAG, "JSON import completed successfully.");

        } catch (Exception e) {
            Log.e(TAG, "Error importing JSON data: " + e.getMessage(), e);
        }
    }
}
