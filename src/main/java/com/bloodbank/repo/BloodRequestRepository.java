package com.bloodbank.repo;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.BloodRequest;
import com.bloodbank.domain.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Data access for {@link BloodRequest} (requirement FR-12). */
public interface BloodRequestRepository extends JpaRepository<BloodRequest, Long> {

    List<BloodRequest> findByStatus(RequestStatus status);

    List<BloodRequest> findByStatusIn(Collection<RequestStatus> statuses);

    long countByStatus(RequestStatus status);

    /**
     * Searches requests by status and blood group, either filter optional and
     * combined with AND, oldest first.
     *
     * <p>Ordering by urgency is deliberately <em>not</em> done here. The column is
     * {@code @Enumerated(STRING)}, so {@code order by r.urgency desc} sorts the
     * stored text and yields MEDIUM, HIGH, CRITICAL - alphabetical, and exactly
     * backwards for the two that matter (defect BUG-05). The semantic ordering
     * uses {@code Urgency.getWeight()} and is applied in {@code RequestService}.
     */
    @Query("""
           select r from BloodRequest r
           where (:status is null or r.status = :status)
             and (:bloodGroup is null or r.bloodGroup = :bloodGroup)
           order by r.createdAt asc
           """)
    List<BloodRequest> search(@Param("status") RequestStatus status,
                              @Param("bloodGroup") BloodGroup bloodGroup);

    /** Blood groups that have at least one open request — the demand side of the dashboard. */
    @Query("select distinct r.bloodGroup from BloodRequest r where r.status in :statuses")
    List<BloodGroup> findGroupsWithRequestsInStatus(@Param("statuses") Collection<RequestStatus> statuses);
}
