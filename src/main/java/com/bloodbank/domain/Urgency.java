package com.bloodbank.domain;

/**
 * How quickly a blood request must be satisfied.
 *
 * <p>Urgency affects presentation order on the dashboard and the request list; it
 * deliberately does <em>not</em> affect matching. Relaxing the compatibility or
 * eligibility rules under time pressure is exactly the kind of shortcut the
 * portal exists to prevent, so a CRITICAL request draws the same shortlist as a
 * LOW one.
 */
public enum Urgency {

    LOW("Low", 1),
    MEDIUM("Medium", 2),
    HIGH("High", 3),
    CRITICAL("Critical", 4);

    private final String label;
    private final int weight;

    Urgency(String label, int weight) {
        this.label = label;
        this.weight = weight;
    }

    public String getLabel() { return label; }

    /** Higher means more urgent; used to sort the request list. */
    public int getWeight() { return weight; }

    public boolean isEscalated() { return this == HIGH || this == CRITICAL; }

    public static Urgency fromText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("urgency must not be blank");
        }
        for (Urgency urgency : values()) {
            if (urgency.name().equalsIgnoreCase(text.trim()) || urgency.label.equalsIgnoreCase(text.trim())) {
                return urgency;
            }
        }
        throw new IllegalArgumentException("unknown urgency: " + text);
    }
}
