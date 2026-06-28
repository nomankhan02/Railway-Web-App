package com.example;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/reservation")
@CrossOrigin(origins = "*")
public class ReservationController {

    // ── Price Constants ────────────────────────────────────────────
    private static final double BIZ_PRICE = 4500; // 🟩 Fixed backend pricing metrics
    private static final double ECO_PRICE = 1800; // 🟩 Fixed backend pricing metrics

    // ── In-memory state ────────────────────────────────────────────
    // 0 = available, 1 = booked
    private final int[] businessSeats = new int[20];   // seats 1-20
    private final int[] economySeats  = new int[80];   // seats 21-100

    // Passenger details stored alongside booking state
    private final Map<Integer, PassengerInfo> bookings = new HashMap<>();

    // ── DTO: incoming booking request ─────────────────────────────
    public static class BookingRequest {
        public String name;
        public int    age;
        public String seatType;   // "Business" or "Economy"
        public int    seatNumber; // 1-100
    }

    // ── DTO: stored passenger info ────────────────────────────────
    public static class PassengerInfo {
        public String name;
        public int    age;
        public double price;
        public String seatType;

        public PassengerInfo(String name, int age, double price, String seatType) {
            this.name     = name;
            this.age      = age;
            this.price    = price;
            this.seatType = seatType;
        }
    }

    // ── DTO: individual seat state returned to frontend ───────────
    public static class SeatState {
        public int           seatNumber;
        public String        seatType;
        public boolean       booked;
        public PassengerInfo passenger;

        public SeatState(int seatNumber, String seatType, boolean booked, PassengerInfo passenger) {
            this.seatNumber = seatNumber;
            this.seatType   = seatType;
            this.booked     = booked;
            this.passenger  = passenger;
        }
    }

    // ── Endpoints ──────────────────────────────────────────────────

    /**
     * GET /api/reservation/seats
     * Returns an array of all 100 seats with booking/passenger details.
     */
    @GetMapping("/seats")
    public List<SeatState> getAllSeats() {
        List<SeatState> list = new ArrayList<>();

        // 1. Add Business Seats (1 to 20)
        for (int i = 0; i < 20; i++) {
            int seatNum = i + 1;
            boolean isBooked = (businessSeats[i] == 1);
            PassengerInfo info = isBooked ? bookings.get(seatNum) : null;
            list.add(new SeatState(seatNum, "Business", isBooked, info));
        }

        // 2. Add Economy Seats (21 to 100)
        for (int i = 0; i < 80; i++) {
            int seatNum = i + 21;
            boolean isBooked = (economySeats[i] == 1);
            PassengerInfo info = isBooked ? bookings.get(seatNum) : null;
            list.add(new SeatState(seatNum, "Economy", isBooked, info));
        }

        return list;
    }

    /**
     * POST /api/reservation/book
     * Processes a booking request, calculates pricing/discounts, and claims the seat.
     */
    @PostMapping("/book")
    public Map<String, Object> bookSeat(@RequestBody BookingRequest req) {
        Map<String, Object> response = new HashMap<>();

        // Basic Boundary Validations
        if (req.name == null || req.name.trim().length() < 2) {
            response.put("success", false);
            response.put("message", "Invalid passenger name.");
            return response;
        }
        if (req.age < 1 || req.age > 120) {
            response.put("success", false);
            response.put("message", "Invalid passenger age.");
            return response;
        }

        double basePrice = 0;
        int targetIndex = -1;

        // Route logic based on Seat Type (Business vs Economy)
        if ("Business".equalsIgnoreCase(req.seatType)) {
            if (req.seatNumber < 1 || req.seatNumber > 20) {
                response.put("success", false);
                response.put("message", "Invalid Business seat number.");
                return response;
            }
            targetIndex = req.seatNumber - 1; // 0-indexed
            
            if (businessSeats[targetIndex] == 1) {
                response.put("success", false);
                response.put("message", "Seat " + req.seatNumber + " has already been booked.");
                return response;
            }

            // Secure seat & set pricing
            businessSeats[targetIndex] = 1;
            basePrice = BIZ_PRICE; // 🟩 Updated dynamically to 4500

        } else if ("Economy".equalsIgnoreCase(req.seatType)) {
            if (req.seatNumber < 21 || req.seatNumber > 100) {
                response.put("success", false);
                response.put("message", "Invalid Economy seat number.");
                return response;
            }
            targetIndex = req.seatNumber - 21; // 0-indexed
            
            if (economySeats[targetIndex] == 1) {
                response.put("success", false);
                return response;
            }

            // Secure seat & set pricing
            economySeats[targetIndex] = 1;
            basePrice = ECO_PRICE; // 🟩 Updated dynamically to 1800

        } else {
            response.put("success", false);
            response.put("message", "Invalid seat type specified.");
            return response;
        }

        // Apply Age Discounts & Save Passenger State
        double finalPrice = calculatePrice(basePrice, req.age);
        PassengerInfo passenger = new PassengerInfo(req.name.trim(), req.age, finalPrice, req.seatType);
        bookings.put(req.seatNumber, passenger);

        // Build Confirmation Data structure
        buildSuccessResponse(response, req, finalPrice, req.seatType);
        return response;
    }

    /**
     * POST /api/reservation/cancel
     * Frees up a booking slot.
     */
    @PostMapping("/cancel")
    public Map<String, Object> cancelSeat(@RequestBody Map<String, Integer> payload) {
        Map<String, Object> response = new HashMap<>();
        Integer seatNumber = payload.get("seatNumber");

        if (seatNumber == null || seatNumber < 1 || seatNumber > 100) {
            response.put("success", false);
            response.put("message", "Invalid seat number for cancellation.");
            return response;
        }

        if (seatNumber <= 20) {
            if (businessSeats[seatNumber - 1] == 1) {
                businessSeats[seatNumber - 1] = 0;
                bookings.remove(seatNumber);
                response.put("success", true);
                response.put("message", "Seat " + seatNumber + " cancelled successfully.");
            } else {
                response.put("success", false);
                response.put("message", "Seat " + seatNumber + " was not booked.");
            }
        } else {
            if (economySeats[seatNumber - 21] == 1) {
                economySeats[seatNumber - 21] = 0;
                bookings.remove(seatNumber);
                response.put("success", true);
                response.put("message", "Seat " + seatNumber + " cancelled successfully.");
            } else {
                response.put("success", false);
                response.put("message", "Seat " + seatNumber + " was not booked.");
            }
        }

        return response;
    }


    // ── Helpers ────────────────────────────────────────────────────

    private double calculatePrice(double baseFare, int age) {
        if (age < 12)  return baseFare * 0.50;
        if (age >= 60) return baseFare * 0.70;
        return baseFare;
    }

    private void buildSuccessResponse(Map<String, Object> response,
                                      BookingRequest req,
                                      double finalPrice,
                                      String seatClass) {
        String discountNote = "";
        if (req.age < 12)       discountNote = "50% child discount (under 12)"; // 🟩 Styled to match frontend labels
        else if (req.age >= 60) discountNote = "30% senior discount (60+)";     // 🟩 Styled to match frontend labels

        response.put("success",      true);
        response.put("message",      "Seat " + req.seatNumber + " booked successfully!");
        response.put("seatNumber",   req.seatNumber);
        response.put("seatType",     seatClass);
        response.put("passengerName", req.name.trim());
        response.put("age",          req.age);
        response.put("finalPrice",   finalPrice);
        response.put("discountNote", discountNote);
        response.put("bookingRef",   "BR-" + Long.toHexString(System.currentTimeMillis()).toUpperCase());
    }
}