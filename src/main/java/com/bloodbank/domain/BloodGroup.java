package com.bloodbank.domain;

import java.util.Arrays;
import java.util.List;

/**
 * The eight ABO/Rh blood groups, and the red-cell compatibility rule between them.
 *
 * <p>The compatibility logic lives on the enum rather than in a service, because
 * it is a property of blood groups themselves and not of any workflow. That also
 * makes requirement FR-14 provable by a plain unit test over all 64 recipient and
 * donor pairs, with no Spring context and no browser.
 *
 * <p>A group is modelled by the antigens it carries, which is what actually drives
 * compatibility:
 * <ul>
 *   <li>ABO — a recipient can accept a donor only if the donor carries no antigen
 *       the recipient lacks, because the recipient's plasma holds antibodies
 *       against any antigen it does not have.</li>
 *   <li>Rh — an Rh-negative recipient can accept only Rh-negative red cells.</li>
 * </ul>
 *
 * <p>Consequences worth knowing for the viva: {@code O_NEG} is the universal red-cell
 * donor (compatible with all eight recipients), {@code AB_POS} is the universal
 * recipient (can accept from all eight), and exactly 27 of the 64 ordered pairs are
 * compatible.
 */
public enum BloodGroup {

    O_NEG ("O-",  false, false, false),
    O_POS ("O+",  false, false, true),
    B_NEG ("B-",  false, true,  false),
    B_POS ("B+",  false, true,  true),
    A_NEG ("A-",  true,  false, false),
    A_POS ("A+",  true,  false, true),
    AB_NEG("AB-", true,  true,  false),
    AB_POS("AB+", true,  true,  true);

    private final String label;
    private final boolean antigenA;
    private final boolean antigenB;
    private final boolean rhPositive;

    BloodGroup(String label, boolean antigenA, boolean antigenB, boolean rhPositive) {
        this.label = label;
        this.antigenA = antigenA;
        this.antigenB = antigenB;
        this.rhPositive = rhPositive;
    }

    /** Display form used in the UI and the API, for example {@code "AB-"}. */
    public String getLabel() {
        return label;
    }

    public boolean isAntigenA() {
        return antigenA;
    }

    public boolean isAntigenB() {
        return antigenB;
    }

    public boolean isRhPositive() {
        return rhPositive;
    }

    /**
     * Whether a recipient of <em>this</em> group may receive red cells from
     * {@code donor}. Implements the matrix in {@code docs/SRS.md} section 3.2.
     *
     * @param donor the prospective donor's blood group, never {@code null}
     * @return {@code true} when the transfusion is compatible
     */
    public boolean canReceiveFrom(BloodGroup donor) {
        if (donor == null) {
            throw new IllegalArgumentException("donor blood group must not be null");
        }
        boolean aboCompatible = (!donor.antigenA || this.antigenA)
                             && (!donor.antigenB || this.antigenB);
        boolean rhCompatible = this.rhPositive || !donor.rhPositive;
        return aboCompatible && rhCompatible;
    }

    /** The groups a recipient of this group may receive from, in registry order. */
    public List<BloodGroup> compatibleDonorGroups() {
        return Arrays.stream(values()).filter(this::canReceiveFrom).toList();
    }

    /** The groups this group may donate to — the inverse view, used by the dashboard. */
    public List<BloodGroup> canDonateTo() {
        return Arrays.stream(values()).filter(recipient -> recipient.canReceiveFrom(this)).toList();
    }

    /**
     * Parses either the enum name ({@code "AB_NEG"}) or the display label
     * ({@code "AB-"}), case-insensitively. Used when binding query parameters and
     * form fields, so that {@code ?group=O-} works as naturally as {@code ?group=O_NEG}.
     *
     * @throws IllegalArgumentException when the text matches no group (surfaced as HTTP 400)
     */
    public static BloodGroup fromText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("blood group must not be blank");
        }
        String trimmed = text.trim();
        for (BloodGroup group : values()) {
            if (group.label.equalsIgnoreCase(trimmed) || group.name().equalsIgnoreCase(trimmed)) {
                return group;
            }
        }
        throw new IllegalArgumentException("unknown blood group: " + text);
    }
}
