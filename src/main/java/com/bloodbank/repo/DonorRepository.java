package com.bloodbank.repo;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.Donor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link Donor}.
 *
 * <p>The search query takes every filter as a nullable parameter and ignores the
 * ones that are null, so one query serves "all donors", "all O- donors",
 * "available A+ donors in Thane" and every combination in between. The filters
 * combine with logical AND, which is requirement FR-04.
 */
public interface DonorRepository extends JpaRepository<Donor, Long> {

    Optional<Donor> findByPhone(String phone);

    boolean existsByPhone(String phone);

    List<Donor> findByBloodGroup(BloodGroup bloodGroup);

    /**
     * Searches donors by any combination of blood group, city and availability.
     *
     * @param bloodGroup   exact group, or {@code null} for any group
     * @param city         case-insensitive partial city match, or {@code null} for any city
     * @param availableOnly {@code true} to exclude donors flagged unavailable;
     *                      {@code null} or {@code false} includes everyone
     */
    @Query("""
           select d from Donor d
           where (:bloodGroup is null or d.bloodGroup = :bloodGroup)
             and (:city is null or lower(d.city) like lower(concat('%', :city, '%')))
             and (:availableOnly = false or d.available = true)
           order by d.fullName asc
           """)
    List<Donor> search(@Param("bloodGroup") BloodGroup bloodGroup,
                       @Param("city") String city,
                       @Param("availableOnly") boolean availableOnly);

    /**
     * Donors whose group is one of the groups compatible with a recipient. The
     * eligibility and availability predicates are applied in the service layer,
     * because eligibility is computed from today's date and cannot be a SQL
     * constant without making the query wrong at midnight.
     */
    @Query("select d from Donor d where d.bloodGroup in :groups")
    List<Donor> findByBloodGroupIn(@Param("groups") List<BloodGroup> groups);

    long countByBloodGroup(BloodGroup bloodGroup);
}
