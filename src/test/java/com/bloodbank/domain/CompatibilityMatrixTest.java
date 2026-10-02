package com.bloodbank.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Requirement FR-14: red-cell compatibility must be correct for all 64 ordered
 * recipient/donor pairs (docs/SRS.md section 3.2).
 *
 * <p>This is the single most important test in the project: an error here means the
 * portal recommends an incompatible transfusion.
 */
@DisplayName("FR-14 red-cell compatibility matrix")
class CompatibilityMatrixTest {

    /**
     * The expected matrix, written out independently of the implementation so the
     * test does not simply restate the production logic. Rows are recipients in
     * declaration order (O- O+ B- B+ A- A+ AB- AB+), columns are donors in the same
     * order, '1' means compatible.
     */
    private static final String[] EXPECTED = {
        //        O-  O+  B-  B+  A-  A+ AB- AB+     recipient
                 "1   0   0   0   0   0   0   0",  // O-
                 "1   1   0   0   0   0   0   0",  // O+
                 "1   0   1   0   0   0   0   0",  // B-
                 "1   1   1   1   0   0   0   0",  // B+
                 "1   0   0   0   1   0   0   0",  // A-
                 "1   1   0   0   1   1   0   0",  // A+
                 "1   0   1   0   1   0   1   0",  // AB-
                 "1   1   1   1   1   1   1   1"   // AB+
    };

    private static boolean expected(BloodGroup recipient, BloodGroup donor) {
        String[] cells = EXPECTED[recipient.ordinal()].trim().split("\\s+");
        return "1".equals(cells[donor.ordinal()]);
    }

    @Test
    @DisplayName("all 64 recipient/donor pairs match the reference matrix")
    void allSixtyFourPairsAreCorrect() {
        int checked = 0;
        int compatible = 0;
        for (BloodGroup recipient : BloodGroup.values()) {
            for (BloodGroup donor : BloodGroup.values()) {
                boolean actual = recipient.canReceiveFrom(donor);
                assertThat(actual)
                        .as("recipient %s receiving from donor %s", recipient.getLabel(), donor.getLabel())
                        .isEqualTo(expected(recipient, donor));
                checked++;
                if (actual) {
                    compatible++;
                }
            }
        }
        assertThat(checked).isEqualTo(64);
        // 27 of 64 pairs are compatible; asserting the total catches a matrix that is
        // self-consistently wrong (for example Rh ignored, which would give 36).
        assertThat(compatible).as("number of compatible ordered pairs").isEqualTo(27);
    }

    @Nested
    @DisplayName("universal donor and universal recipient")
    class UniversalGroups {

        @ParameterizedTest(name = "{0} can receive from O-")
        @EnumSource(BloodGroup.class)
        @DisplayName("O- is the universal red-cell donor")
        void oNegativeDonatesToEveryone(BloodGroup recipient) {
            assertThat(recipient.canReceiveFrom(BloodGroup.O_NEG)).isTrue();
        }

        @ParameterizedTest(name = "AB+ can receive from {0}")
        @EnumSource(BloodGroup.class)
        @DisplayName("AB+ is the universal recipient")
        void abPositiveReceivesFromEveryone(BloodGroup donor) {
            assertThat(BloodGroup.AB_POS.canReceiveFrom(donor)).isTrue();
        }

        @Test
        @DisplayName("O- can donate to all eight groups but receive from only itself")
        void oNegativeIsTheHardestToSupply() {
            assertThat(BloodGroup.O_NEG.canDonateTo()).hasSize(8);
            assertThat(BloodGroup.O_NEG.compatibleDonorGroups()).containsExactly(BloodGroup.O_NEG);
        }
    }

    @Nested
    @DisplayName("the two rules the matrix encodes")
    class Rules {

        @ParameterizedTest(name = "Rh-negative {0} must not receive Rh-positive blood")
        @EnumSource(value = BloodGroup.class, names = {"O_NEG", "B_NEG", "A_NEG", "AB_NEG"})
        void rhNegativeRecipientsRefuseRhPositiveDonors(BloodGroup recipient) {
            Arrays.stream(BloodGroup.values())
                  .filter(BloodGroup::isRhPositive)
                  .forEach(donor -> assertThat(recipient.canReceiveFrom(donor))
                          .as("%s receiving from %s", recipient.getLabel(), donor.getLabel())
                          .isFalse());
        }

        @ParameterizedTest
        @CsvSource({
            // recipient, donor, compatible, reason
            "O_POS,  A_POS,  false",   // recipient lacks antigen A
            "O_POS,  B_POS,  false",   // recipient lacks antigen B
            "A_POS,  B_POS,  false",   // A cannot take B antigen
            "B_POS,  A_POS,  false",   // B cannot take A antigen
            "A_POS,  AB_POS, false",   // AB carries both antigens
            "AB_POS, A_NEG,  true",    // AB accepts A, and Rh+ accepts Rh-
            "B_POS,  O_NEG,  true",    // universal donor
            "A_NEG,  A_NEG,  true"     // identical group
        })
        @DisplayName("a recipient accepts a donor only when the donor carries no antigen the recipient lacks")
        void antigenSubsetRule(BloodGroup recipient, BloodGroup donor, boolean compatible) {
            assertThat(recipient.canReceiveFrom(donor)).isEqualTo(compatible);
        }

        @Test
        @DisplayName("every group is compatible with itself")
        void selfCompatibility() {
            for (BloodGroup group : BloodGroup.values()) {
                assertThat(group.canReceiveFrom(group)).as("%s with itself", group.getLabel()).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("parsing input")
    class Parsing {

        @ParameterizedTest
        @CsvSource({"O-,O_NEG", "o-,O_NEG", "AB+,AB_POS", "ab+,AB_POS", "O_NEG,O_NEG", " B+ ,B_POS"})
        @DisplayName("accepts both the display label and the enum name, case-insensitively")
        void acceptsLabelAndName(String text, BloodGroup expected) {
            assertThat(BloodGroup.fromText(text)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({"C+", "O", "AB", "positive", "''", "'   '"})
        @DisplayName("rejects anything that is not one of the eight groups (surfaced as HTTP 400)")
        void rejectsUnknownText(String text) {
            assertThatIllegalArgumentException().isThrownBy(() -> BloodGroup.fromText(text));
        }

        @Test
        @DisplayName("rejects null rather than silently treating it as compatible")
        void rejectsNullDonor() {
            assertThatIllegalArgumentException().isThrownBy(() -> BloodGroup.AB_POS.canReceiveFrom(null));
        }
    }

    @Test
    @DisplayName("labels are the eight clinical group names")
    void labels() {
        assertThat(Arrays.stream(BloodGroup.values()).map(BloodGroup::getLabel).toList())
                .containsExactly("O-", "O+", "B-", "B+", "A-", "A+", "AB-", "AB+");
    }
}
