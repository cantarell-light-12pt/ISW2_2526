package it.uniroma2.dicii.isw2.issues.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum IssueStatus {

    OPEN("open"), IN_PROGRESS("in progress"), REOPENED("reopened"), ON_HOLD("on hold"), BLOCKED("blocked"), DUPLICATE("duplicate"), VERIFIED("verified"), INVESTIGATED("investigated"), ASSIGNED("assigned"), FIXED("fixed"), RESOLVED("resolved"), CLOSED("closed"), OTHER("other");

    @Getter
    private final String status;

    /**
     * Converts the given text to a corresponding {@code IssueStatus} enum value.
     * If the text does not match any predefined {@code IssueStatus} value,
     * this method returns {@code OTHER}.
     *
     * @param text the status text to convert to an {@code IssueStatus}.
     *             This string is compared in a case-insensitive manner
     *             against the status values of the {@code IssueStatus} enum.
     * @return the corresponding {@code IssueStatus} enum value if a match is found,
     * otherwise {@code OTHER}.
     * @throws IllegalArgumentException if the provided text is null or empty.
     */
    public static IssueStatus from(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }

        for (IssueStatus status : IssueStatus.values()) {
            if (status.getStatus().equalsIgnoreCase(text)) {
                return status;
            }
        }
        return OTHER;
    }

}
