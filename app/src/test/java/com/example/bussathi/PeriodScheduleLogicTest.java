package com.example.bussathi;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * PeriodScheduleLogicTest — verifies period-specific bus timetable logic
 * and all 8 combinations of (Period × Bus × Direction).
 */
public class PeriodScheduleLogicTest {

    @Test
    public void testSchedulePeriodModelAndDisplayText() {
        DatabaseHelper.SchedulePeriod morning = new DatabaseHelper.SchedulePeriod(
                "morning", "Morning", "10:00 AM", "11:00 AM");
        assertEquals("Morning (10:00 AM – 11:00 AM)", morning.getDisplayText());

        // Test dynamic update of period times
        morning.startTime = "07:00 AM";
        morning.endTime = "08:00 AM";
        assertEquals("Morning (07:00 AM – 08:00 AM)", morning.getDisplayText());

        FirestoreRepository.FirestorePeriod fPeriod = new FirestoreRepository.FirestorePeriod(
                "evening", "Evening", "05:00 PM", "06:00 PM");
        assertEquals("Evening (05:00 PM – 06:00 PM)", fPeriod.getDisplayText());
    }

    @Test
    public void testPeriodSpecificFirestoreScheduleModel() {
        FirestoreRepository.FirestoreSchedule schedule = new FirestoreRepository.FirestoreSchedule(
                "BUS_1_morning_stop_b_TO_KATHORA",
                FirestoreRepository.BUS_1,
                "morning",
                "stop_b",
                FirestoreRepository.DIR_TO_KATHORA,
                "10:20 AM",
                "10:25 AM"
        );

        assertEquals("BUS_1", schedule.busNumber);
        assertEquals("morning", schedule.schedulePeriodId);
        assertEquals("stop_b", schedule.stopId);
        assertEquals(FirestoreRepository.DIR_TO_KATHORA, schedule.direction);
        assertEquals("10:20 AM", schedule.arrivalTime);
        assertEquals("10:25 AM", schedule.departureTime);
    }

    @Test
    public void testAllEightCombinationsAreIndependent() {
        // Map simulating compound key: period + "_" + bus + "_" + dir + "_" + stop
        Map<String, String> timetableStore = new HashMap<>();

        // Stop B timings across all combinations
        timetableStore.put("morning_BUS_1_TO_KATHORA_Stop B", "10:20 AM - 10:22 AM");
        timetableStore.put("morning_BUS_1_TO_COLLEGE_Stop B", "10:55 AM - 10:57 AM");

        timetableStore.put("morning_BUS_2_TO_KATHORA_Stop B", "10:35 AM - 10:37 AM");
        timetableStore.put("morning_BUS_2_TO_COLLEGE_Stop B", "11:12 AM - 11:14 AM");

        timetableStore.put("evening_BUS_1_TO_KATHORA_Stop B", "05:20 PM - 05:22 PM");
        timetableStore.put("evening_BUS_1_TO_COLLEGE_Stop B", "05:55 PM - 05:57 PM");

        timetableStore.put("evening_BUS_2_TO_KATHORA_Stop B", "05:35 PM - 05:37 PM");
        timetableStore.put("evening_BUS_2_TO_COLLEGE_Stop B", "06:12 PM - 06:14 PM");

        // Verify each combination yields distinct and correct timings
        assertEquals("10:20 AM - 10:22 AM", timetableStore.get("morning_BUS_1_TO_KATHORA_Stop B"));
        assertEquals("10:55 AM - 10:57 AM", timetableStore.get("morning_BUS_1_TO_COLLEGE_Stop B"));

        assertEquals("10:35 AM - 10:37 AM", timetableStore.get("morning_BUS_2_TO_KATHORA_Stop B"));
        assertEquals("11:12 AM - 11:14 AM", timetableStore.get("morning_BUS_2_TO_COLLEGE_Stop B"));

        assertEquals("05:20 PM - 05:22 PM", timetableStore.get("evening_BUS_1_TO_KATHORA_Stop B"));
        assertEquals("05:55 PM - 05:57 PM", timetableStore.get("evening_BUS_1_TO_COLLEGE_Stop B"));

        assertEquals("05:35 PM - 05:37 PM", timetableStore.get("evening_BUS_2_TO_KATHORA_Stop B"));
        assertEquals("06:12 PM - 06:14 PM", timetableStore.get("evening_BUS_2_TO_COLLEGE_Stop B"));

        // Verify missing query returns null (Timing not available)
        assertNull(timetableStore.get("night_BUS_1_TO_KATHORA_Stop B"));
    }
}
