package com.bloodbank.web;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.Donor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Form backing object for the donor screens.
 *
 * <p>A separate object from the {@link Donor} entity so that a browser cannot
 * bind fields it has no business setting — identifiers and audit timestamps —
 * and so that validation messages can be written for the person filling in the
 * form rather than for the database.
 */
public class DonorForm {

    @NotBlank(message = "Full name is required")
    @Size(min = 3, max = 80, message = "Full name must be between 3 and 80 characters")
    private String fullName;

    @NotNull(message = "Select a blood group")
    private BloodGroup bloodGroup;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "\\d{10}", message = "Phone number must be exactly 10 digits")
    private String phone;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;

    @NotBlank(message = "City is required")
    @Size(min = 2, max = 60, message = "City must be between 2 and 60 characters")
    private String city;

    /** Blank means the donor has never donated, which counts as eligible. */
    private LocalDate lastDonationDate;

    private boolean available = true;

    @Size(max = 255, message = "Notes must be at most 255 characters")
    private String notes;

    public static DonorForm from(Donor donor) {
        DonorForm form = new DonorForm();
        form.fullName = donor.getFullName();
        form.bloodGroup = donor.getBloodGroup();
        form.phone = donor.getPhone();
        form.email = donor.getEmail();
        form.city = donor.getCity();
        form.lastDonationDate = donor.getLastDonationDate();
        form.available = donor.isAvailable();
        form.notes = donor.getNotes();
        return form;
    }

    public Donor toDonor() {
        Donor donor = new Donor(fullName, bloodGroup, phone, email, city);
        donor.setLastDonationDate(lastDonationDate);
        donor.setAvailable(available);
        donor.setNotes(notes);
        return donor;
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public BloodGroup getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(BloodGroup bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public LocalDate getLastDonationDate() { return lastDonationDate; }
    public void setLastDonationDate(LocalDate lastDonationDate) { this.lastDonationDate = lastDonationDate; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
