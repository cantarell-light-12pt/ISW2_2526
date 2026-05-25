package it.uniroma2.dicii.isw2.issues.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum IssueType {
    BUG("bug"), NEW_FEATURE("new feature"), OTHER("other");

    @Getter
    private final String type;

    /**
     * Converts the given type string to a corresponding {@code IssueType} enum value.
     * If the type string does not match any predefined {@code IssueType} value,
     * this method returns {@code OTHER}.
     *
     * @param type the type string to convert to an {@code IssueType}.
     *             This string is compared in a case-insensitive manner
     *             against the type values of the {@code IssueType} enum.
     * @return the corresponding {@code IssueType} enum value if a match is found,
     * otherwise {@code OTHER}.
     */
    public static IssueType from(String type) {
        for (IssueType t : values()) {
            if (t.getType().equalsIgnoreCase(type)) {
                return t;
            }
        }
        return OTHER;
    }
}
