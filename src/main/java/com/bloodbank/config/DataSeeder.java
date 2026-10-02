package com.bloodbank.config;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.BloodRequest;
import com.bloodbank.domain.Donor;
import com.bloodbank.domain.Urgency;
import com.bloodbank.repo.BloodRequestRepository;
import com.bloodbank.repo.DonorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Seeds demonstration data into an <em>empty</em> database only.
 *
 * <p>Two properties make this safe and useful: it never touches a database that
 * already has donors, so restarting a container does not duplicate or overwrite
 * anything; and the last-donation dates are expressed relative to today, so the
 * eligibility boundary is visible during a demonstration on any date.
 *
 * <p>Deleting the Docker volume is therefore a recoverable, five-second
 * operation rather than the loss of the demonstration dataset.
 */
@Configuration
@Profile("!test")
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    ApplicationRunner seedDemonstrationData(DonorRepository donors,
                                            BloodRequestRepository requests,
                                            Clock clock) {
        // One runner, not two: separate ApplicationRunner beans have no
        // guaranteed order, and seeding requests before donors silently produced
        // an empty request table (defect BUG-04).
        return args -> {
            seedDonors(donors, clock);
            seedRequests(requests, donors);
        };
    }

    private void seedDonors(DonorRepository donors, Clock clock) {
        {
            if (donors.count() > 0) {
                log.info("Database already contains {} donors; skipping seed", donors.count());
                return;
            }
            LocalDate today = LocalDate.now(clock);

            // Spread across all eight groups and four cities, with a deliberate mix
            // of donation recency: never donated, far past, just past the 90-day
            // boundary, one day short of it, and very recent.
            List<Donor> seed = List.of(
                donor("Rohit Deshmukh",  BloodGroup.O_NEG,  "9820010001", "Thane",       today.minusDays(160), true),
                donor("Sneha Patil",     BloodGroup.B_POS,  "9820010002", "Thane",       today.minusDays(142), true),
                donor("Imran Shaikh",    BloodGroup.O_POS,  "9820010003", "Pune",        null,                 true),
                donor("Kavita Rane",     BloodGroup.B_NEG,  "9820010004", "Mumbai",      today.minusDays(90),  true),
                donor("Arjun Mehta",     BloodGroup.A_POS,  "9820010005", "Mumbai",      today.minusDays(89),  true),
                donor("Nikhil Bhosale",  BloodGroup.AB_POS, "9820010006", "Navi Mumbai", today.minusDays(240), true),
                donor("Priya Nair",      BloodGroup.A_NEG,  "9820010007", "Thane",       today.minusDays(45),  true),
                donor("Rahul Joshi",     BloodGroup.O_NEG,  "9820010008", "Pune",        today.minusDays(10),  true),
                donor("Fatima Ansari",   BloodGroup.AB_NEG, "9820010009", "Mumbai",      today.minusDays(300), false),
                donor("Sagar Kulkarni",  BloodGroup.O_POS,  "9820010010", "Navi Mumbai", today.minusDays(120), true),
                donor("Ananya Iyer",     BloodGroup.A_POS,  "9820010011", "Thane",       null,                 true),
                donor("Vikram Shinde",   BloodGroup.B_POS,  "9820010012", "Pune",        today.minusDays(200), true),
                donor("Meera Gokhale",   BloodGroup.A_POS,  "9820010013", "Mumbai",      today.minusDays(400), true),
                donor("Zoya Qureshi",    BloodGroup.AB_POS, "9820010014", "Thane",       today.minusDays(30),  true),
                donor("Tanmay Pawar",    BloodGroup.O_POS,  "9820010015", "Thane",       today.minusDays(95),  true),
                donor("Harshada Sawant", BloodGroup.B_NEG,  "9820010016", "Navi Mumbai", null,                 false)
            );

            donors.saveAll(seed);
            log.info("Seeded {} demonstration donors across {} blood groups",
                     seed.size(), BloodGroup.values().length);
        }
    }

    /**
     * Seeds three open requests, so that the dashboard, the ranked shortlist and
     * the role-based workflow are all exercisable the moment the application
     * starts.
     *
     * <p>The three are chosen to show different things:
     * <ul>
     *   <li><strong>B+ in Thane</strong> — a healthy shortlist with every ranking
     *       rule visible at once: exact group, same city, and longest rested.</li>
     *   <li><strong>O- in Mumbai</strong> — the scarcest group. Exactly one donor
     *       in the seed register is callable for it (the other O- donor donated ten
     *       days ago), so marking that one donor unavailable turns this request into
     *       a <strong>shortage</strong> live on screen. That cause-and-effect is far
     *       more informative than a shortage that is simply present at startup.</li>
     *   <li><strong>AB- in Thane</strong> — shows that compatibility is wider than
     *       "same group only": the only AB- donor is unavailable, yet the request
     *       is still satisfiable from a B- donor.</li>
     * </ul>
     */
    private void seedRequests(BloodRequestRepository requests, DonorRepository donors) {
        if (requests.count() > 0 || donors.count() == 0) {
            return;
        }
        requests.saveAll(List.of(
            request("Meera Joshi",   BloodGroup.B_POS,  2, "Civil Hospital, Thane",   "Thane",  Urgency.HIGH),
            request("Aditya Kamat",  BloodGroup.O_NEG,  1, "Sion Hospital",           "Mumbai", Urgency.CRITICAL),
            request("Shalini Desai", BloodGroup.AB_NEG, 1, "Jupiter Hospital, Thane", "Thane",  Urgency.MEDIUM)
        ));
        log.info("Seeded {} demonstration blood requests", requests.count());
    }

    private static BloodRequest request(String patient, BloodGroup group, int units,
                                        String hospital, String city, Urgency urgency) {
        BloodRequest request = new BloodRequest(patient, group, units, hospital, city, urgency);
        request.recordCreation("staff", java.time.Instant.now());
        return request;
    }

    private static Donor donor(String name, BloodGroup group, String phone, String city,
                               LocalDate lastDonation, boolean available) {
        String email = name.toLowerCase().replace(' ', '.') + "@example.com";
        Donor donor = new Donor(name, group, phone, email, city);
        donor.setLastDonationDate(lastDonation);
        donor.setAvailable(available);
        return donor;
    }
}
