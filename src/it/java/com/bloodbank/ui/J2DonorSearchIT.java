package com.bloodbank.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 2 — searching the registry, and the ranked match shortlist.
 *
 * <p><strong>Why this journey is critical:</strong> search and ranking are the
 * two things the portal exists to replace. The manual process fails precisely
 * because nobody can answer "who can we call for a B+ patient in Thane right
 * now?", so a regression here would return the blood bank to the phone tree
 * while every unit test stayed green.
 *
 * <p>Covers AC-02.1 (filtering by group returns only that group), AC-02.4 (an
 * empty result says so explicitly) and AC-06.8 (the shortlist is displayed with
 * group, city and the reason for each rank).
 */
@DisplayName("J2 - Search the registry and read the ranked shortlist")
class J2DonorSearchIT extends BaseJourney {

    @Test
    @DisplayName("AC-02.1 filtering by blood group returns only donors of that group")
    void filtersByBloodGroup() {
        signInAsStaff();

        open("/donors");
        select("filter-group", "O-");
        click("search-submit");

        // Wait for the filtered page before reading anything from it. Clicking
        // Search is a full navigation, and reading elements located before it
        // completed is how this test previously threw StaleElementReference in
        // Jenkins - a flaky failure in a test whose subject was never timing.
        wait.until(org.openqa.selenium.support.ui.ExpectedConditions
                .presenceOfElementLocated(testId("donor-table")));

        // Read the group badge of every row rather than trusting the filter: the
        // assertion must be about what the user sees, not about the query string.
        var shownGroups = driver.findElements(
                org.openqa.selenium.By.cssSelector("[data-testid^='donor-row-'] .bb-group"))
                .stream().map(e -> e.getText().trim()).distinct().toList();

        assertThat(shownGroups)
                .as("only O- donors may be listed when filtering by O-")
                .containsExactly("O-");
    }

    @Test
    @DisplayName("AC-02.2 group and city filters combine with AND, not OR")
    void filtersCombineWithAnd() {
        signInAsStaff();

        open("/donors?group=O-");
        int oNegativeAnywhere = findAll("donor-name").size();

        open("/donors?city=Thane");
        int everyoneInThane = findAll("donor-name").size();

        open("/donors?group=O-&city=Thane");
        int both = findAll("donor-name").size();

        assertThat(both)
                .as("an AND of two filters cannot return more than either filter alone")
                .isLessThanOrEqualTo(oNegativeAnywhere)
                .isLessThanOrEqualTo(everyoneInThane);
        assertThat(oNegativeAnywhere + everyoneInThane)
                .as("if the filters were OR-ed the combined result would be larger, not smaller")
                .isGreaterThan(both);
    }

    @Test
    @DisplayName("AC-02.4 a search matching nothing shows an explicit message, not a blank page")
    void showsAMessageWhenNothingMatches() {
        signInAsStaff();

        open("/donors?group=AB-&city=Nagpur");

        assertThat(isPresent("no-donors"))
                .as("an empty result must be stated, not left as an empty table")
                .isTrue();
        assertThat(textOf("no-donors")).containsIgnoringCase("no donors found");
        assertThat(isPresent("donor-table"))
                .as("the table should not be rendered at all when there is nothing in it")
                .isFalse();
    }

    @Test
    @DisplayName("AC-06.8 the shortlist for a B+ patient shows only compatible donors, ranked, with the reason")
    void showsTheRankedShortlist() {
        signInAsStaff();

        open("/match?group=B%2B&city=Thane");

        // The page must state which groups are compatible - this is the piece of
        // knowledge the manual process gets wrong.
        assertThat(textOf("compatible-groups"))
                .as("the screen should explain that a B+ patient can receive from four of the eight groups")
                .contains("4");

        var rows = driver.findElements(
                org.openqa.selenium.By.cssSelector("[data-testid^='match-row-']"));
        assertThat(rows).as("the seeded register must yield a shortlist for B+").isNotEmpty();

        var shownGroups = driver.findElements(
                org.openqa.selenium.By.cssSelector("[data-testid^='match-row-'] .bb-group"))
                .stream().map(e -> e.getText().trim()).distinct().sorted().toList();
        assertThat(shownGroups)
                .as("FR-14: a B+ recipient may only be offered B+, B-, O+ or O- donors")
                .isSubsetOf("B+", "B-", "O+", "O-");

        // Rank 1 must be the best available match, and the screen must say why.
        //
        // Asserting that rank 1 is literally "exact group AND same city" would be
        // wrong: journeys J4 and J5 record donations, which makes those donors
        // ineligible, so the best *available* match legitimately changes
        // depending on what has already run. Asserting the ranking INVARIANT
        // instead is both stronger and order-independent - if any donor in the
        // list is an exact group match, the top-ranked one must be too.
        assertThat(isPresent("match-row-1")).isTrue();

        var rowTexts = rows.stream().map(r -> r.getText()).toList();
        boolean anyExactMatch = rowTexts.stream().anyMatch(t -> t.contains("Exact group"));
        if (anyExactMatch) {
            assertThat(rowTexts.get(0))
                    .as("FR-15: if any shortlisted donor is an exact group match, rank 1 must be one")
                    .contains("Exact group");
        }
        boolean anySameCity = rowTexts.stream().anyMatch(t -> t.contains("Same city"));
        if (anySameCity && !anyExactMatch) {
            assertThat(rowTexts.get(0))
                    .as("FR-15: with no exact match available, a same-city donor must rank first")
                    .contains("Same city");
        }
        // And the ranks themselves must be a contiguous 1..n sequence.
        var ranks = driver.findElements(
                org.openqa.selenium.By.cssSelector("[data-testid^='match-row-'] .bb-rank"))
                .stream().map(e -> Integer.parseInt(e.getText().trim())).toList();
        assertThat(ranks).isEqualTo(
                java.util.stream.IntStream.rangeClosed(1, ranks.size()).boxed().toList());
    }
}
