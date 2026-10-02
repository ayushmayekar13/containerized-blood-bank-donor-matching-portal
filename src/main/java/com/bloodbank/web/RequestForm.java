package com.bloodbank.web;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.BloodRequest;
import com.bloodbank.domain.Urgency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Form backing object for recording a blood request (requirement FR-10). */
public class RequestForm {

    @NotBlank(message = "Patient name is required")
    @Size(min = 3, max = 80, message = "Patient name must be between 3 and 80 characters")
    private String patientName;

    @NotNull(message = "Select a blood group")
    private BloodGroup bloodGroup;

    @Min(value = 1, message = "At least 1 unit is required")
    @Max(value = 10, message = "A single request may ask for at most 10 units")
    private int unitsNeeded = 1;

    @NotBlank(message = "Hospital is required")
    @Size(min = 3, max = 100, message = "Hospital must be between 3 and 100 characters")
    private String hospital;

    @NotBlank(message = "City is required")
    @Size(min = 2, max = 60, message = "City must be between 2 and 60 characters")
    private String city;

    @NotNull(message = "Select an urgency")
    private Urgency urgency = Urgency.MEDIUM;

    public BloodRequest toRequest() {
        return new BloodRequest(patientName, bloodGroup, unitsNeeded, hospital, city, urgency);
    }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public BloodGroup getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(BloodGroup bloodGroup) { this.bloodGroup = bloodGroup; }

    public int getUnitsNeeded() { return unitsNeeded; }
    public void setUnitsNeeded(int unitsNeeded) { this.unitsNeeded = unitsNeeded; }

    public String getHospital() { return hospital; }
    public void setHospital(String hospital) { this.hospital = hospital; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Urgency getUrgency() { return urgency; }
    public void setUrgency(Urgency urgency) { this.urgency = urgency; }
}
