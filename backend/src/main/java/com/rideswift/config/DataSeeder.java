package com.rideswift.config;

import com.rideswift.model.Driver;
import com.rideswift.model.Gateway;
import com.rideswift.model.Payment;
import com.rideswift.model.PaymentStatus;
import com.rideswift.model.Ride;
import com.rideswift.model.RideStatus;
import com.rideswift.model.Role;
import com.rideswift.model.User;
import com.rideswift.model.Vehicle;
import com.rideswift.model.VehicleType;
import com.rideswift.model.VerificationStatus;
import com.rideswift.repository.DriverRepository;
import com.rideswift.repository.PaymentRepository;
import com.rideswift.repository.RideRepository;
import com.rideswift.repository.UserRepository;
import com.rideswift.repository.VehicleRepository;
import com.rideswift.util.GeoUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a self-contained demo dataset (admin, passengers, verified + pending drivers
 * with vehicles, and ~30 historical completed rides with successful payments) so a
 * reviewer can exercise all three roles immediately.
 *
 * <p>Enable with {@code rideswift.seed.enabled=true}. Idempotent: it is a no-op once
 * the primary demo admin already exists.
 */
@Component
@ConditionalOnProperty(name = "rideswift.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String PASSWORD = "password123";

    // Coimbatore-ish anchor (covers the Coimbatore–Ettimadai area) for scattered
    // driver/ride coordinates.
    private static final double BASE_LAT = 11.0168;
    private static final double BASE_LNG = 76.9558;

    // Ettimadai (Amrita University area), ~14 km SW of Coimbatore centre — a second
    // driver cluster so riders there see cars nearby too.
    private static final double ETTIMADAI_LAT = 10.9018;
    private static final double ETTIMADAI_LNG = 76.9006;

    private final UserRepository users;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final RideRepository rides;
    private final PaymentRepository payments;
    private final PasswordEncoder encoder;
    private final Random rnd = new Random(42);

    public DataSeeder(UserRepository users, DriverRepository drivers, VehicleRepository vehicles,
                      RideRepository rides, PaymentRepository payments, PasswordEncoder encoder) {
        this.users = users;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.rides = rides;
        this.payments = payments;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.findByEmailIgnoreCase("admin@rideswift.io").isPresent()) {
            log.info("Demo data already present — skipping seed.");
            return;
        }
        log.info("Seeding RideSwift demo data…");

        createUser("Ops Admin", "admin@rideswift.io", "+919900000001", Role.ADMIN);

        List<User> passengers = new ArrayList<>();
        passengers.add(createUser("Alice Rivera", "alice@rideswift.io", "+919900000010", Role.PASSENGER));
        passengers.add(createUser("Bob Chen", "bob@rideswift.io", "+919900000011", Role.PASSENGER));
        passengers.add(createUser("Carol Diaz", "carol@rideswift.io", "+919900000012", Role.PASSENGER));
        passengers.add(createUser("Dave Patel", "dave@rideswift.io", "+919900000013", Role.PASSENGER));
        passengers.add(createUser("Eve Novak", "eve@rideswift.io", "+919900000014", Role.PASSENGER));

        String[][] cars = {
            // 0–9: Coimbatore-centre cluster
            {"Toyota", "Camry", "ECONOMY"}, {"Honda", "Accord", "ECONOMY"},
            {"Tesla", "Model 3", "PREMIUM"}, {"BMW", "5 Series", "PREMIUM"},
            {"Toyota", "Corolla", "ECONOMY"}, {"Ford", "Explorer", "XL"},
            {"Honda", "Odyssey", "XL"}, {"Mercedes", "E-Class", "PREMIUM"},
            {"Hyundai", "Sonata", "ECONOMY"}, {"Chevrolet", "Suburban", "XL"},
            // 10–17: Ettimadai (Amrita University) cluster
            {"Maruti Suzuki", "Swift Dzire", "ECONOMY"}, {"Tata", "Tigor", "ECONOMY"},
            {"Hyundai", "Aura", "ECONOMY"}, {"Toyota", "Innova Crysta", "XL"},
            {"Mahindra", "XUV700", "XL"}, {"Honda", "City", "PREMIUM"},
            {"Maruti Suzuki", "Ertiga", "XL"}, {"Tata", "Nexon", "ECONOMY"},
        };
        String[] driverNames = {
            // Coimbatore centre
            "Mike Johnson", "Sara Lee", "Omar Haddad", "Nina Volkov", "Liam Murphy",
            "Priya Singh", "Tom Becker", "Yuki Tanaka", "Diego Costa", "Hana Kim",
            // Ettimadai
            "Karthik Raja", "Anjali Menon", "Suresh Kumar", "Deepa Nair",
            "Ravi Shankar", "Meena Iyer", "Arjun Pillai", "Lakshmi Devi",
        };
        // First 10 spawn around Coimbatore centre; the rest around Ettimadai.
        int coimbatoreCount = 10;

        List<Driver> verifiedDrivers = new ArrayList<>();
        for (int i = 0; i < driverNames.length; i++) {
            String email = i == 0 ? "mike@rideswift.io" : "driver" + (i + 1) + "@rideswift.io";
            User u = createUser(driverNames[i], email, "+919900001" + String.format("%03d", i), Role.DRIVER);
            VehicleType type = VehicleType.valueOf(cars[i][2]);
            boolean nearEttimadai = i >= coimbatoreCount;
            double anchorLat = nearEttimadai ? ETTIMADAI_LAT : BASE_LAT;
            double anchorLng = nearEttimadai ? ETTIMADAI_LNG : BASE_LNG;
            double spread = nearEttimadai ? 0.02 : 0.03; // keep clusters tight to their town
            Driver d = drivers.save(Driver.builder()
                    .user(u)
                    .licenseNumber("DL-" + String.format("%08d", 10000000 + i))
                    .rating(BigDecimal.valueOf(4.5 + rnd.nextDouble() * 0.5).setScale(2, RoundingMode.HALF_UP))
                    .verificationStatus(VerificationStatus.VERIFIED)
                    .available(true)
                    .currentLocation(GeoUtils.point(jitter(anchorLat, spread), jitter(anchorLng, spread)))
                    .build());
            vehicles.save(Vehicle.builder()
                    .driver(d)
                    .make(cars[i][0]).model(cars[i][1]).year(2019 + (i % 6))
                    .licensePlate("RSW-" + String.format("%04d", 1000 + i))
                    .vehicleType(type)
                    .build());
            verifiedDrivers.add(d);
        }

        // One pending driver so the admin verification flow has something to act on.
        User pendingUser = createUser("Pat Pending", "pending@rideswift.io", "+919900009999", Role.DRIVER);
        Driver pending = drivers.save(Driver.builder()
                .user(pendingUser)
                .licenseNumber("DL-99999999")
                .verificationStatus(VerificationStatus.PENDING)
                .available(false)
                .build());
        vehicles.save(Vehicle.builder()
                .driver(pending)
                .make("Kia").model("Optima").year(2021)
                .licensePlate("RSW-9999").vehicleType(VehicleType.ECONOMY)
                .build());

        seedHistory(passengers, verifiedDrivers, 32);

        log.info("Seed complete: {} users, {} drivers, {} rides.",
                users.count(), drivers.count(), rides.count());
    }

    private void seedHistory(List<User> passengers, List<Driver> driverPool, int count) {
        VehicleType[] types = VehicleType.values();
        Gateway[] gateways = Gateway.values();
        for (int i = 0; i < count; i++) {
            User passenger = passengers.get(rnd.nextInt(passengers.size()));
            Driver driver = driverPool.get(rnd.nextInt(driverPool.size()));
            VehicleType type = types[rnd.nextInt(types.length)];

            double pLat = jitter(BASE_LAT, 0.04), pLng = jitter(BASE_LNG, 0.04);
            double dLat = jitter(BASE_LAT, 0.05), dLng = jitter(BASE_LNG, 0.05);
            double distanceKm = round2(GeoUtils.haversineKm(pLat, pLng, dLat, dLng) + 0.5);
            int duration = (int) Math.max(5, Math.round(distanceKm / 30.0 * 60));
            BigDecimal fare = fareFor(type, distanceKm, duration);

            Instant requestedAt = Instant.now().minus(rnd.nextInt(30), ChronoUnit.DAYS)
                    .minus(rnd.nextInt(24), ChronoUnit.HOURS);
            Instant startedAt = requestedAt.plus(2 + rnd.nextInt(6), ChronoUnit.MINUTES);
            Instant completedAt = startedAt.plus(duration, ChronoUnit.MINUTES);

            Ride ride = rides.save(Ride.builder()
                    .passenger(passenger)
                    .driver(driver)
                    .vehicleType(type)
                    .pickupLocation(GeoUtils.point(pLat, pLng))
                    .dropoffLocation(GeoUtils.point(dLat, dLng))
                    .pickupAddress(address(pLat, pLng))
                    .dropoffAddress(address(dLat, dLng))
                    .status(RideStatus.COMPLETED)
                    .requestedAt(requestedAt)
                    .startedAt(startedAt)
                    .completedAt(completedAt)
                    .estimatedFare(fare)
                    .actualFare(fare)
                    .distanceKm(BigDecimal.valueOf(distanceKm))
                    .durationMinutes(duration)
                    .build());

            payments.save(Payment.builder()
                    .ride(ride)
                    .amount(fare)
                    .currency("USD")
                    .gateway(gateways[rnd.nextInt(gateways.length)])
                    .gatewayTransactionId("seed_" + ride.getId())
                    .status(PaymentStatus.SUCCESS)
                    .processedAt(completedAt)
                    .build());
        }
    }

    private User createUser(String name, String email, String phone, Role role) {
        return users.save(User.builder()
                .name(name)
                .email(email)
                .phone(phone)
                .hashedPassword(encoder.encode(PASSWORD))
                .role(role)
                .build());
    }

    private BigDecimal fareFor(VehicleType type, double km, int minutes) {
        BigDecimal base, perKm, perMin;
        switch (type) {
            case PREMIUM -> { base = bd("100.00"); perKm = bd("22.00"); perMin = bd("2.50"); }
            case XL -> { base = bd("80.00"); perKm = bd("18.00"); perMin = bd("2.00"); }
            default -> { base = bd("50.00"); perKm = bd("14.00"); perMin = bd("1.50"); }
        }
        return base.add(perKm.multiply(BigDecimal.valueOf(km)))
                .add(perMin.multiply(BigDecimal.valueOf(minutes)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private double jitter(double base, double spread) {
        return base + (rnd.nextDouble() - 0.5) * spread;
    }

    private static String address(double lat, double lng) {
        return String.format("%.4f, %.4f", lat, lng);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
