package it.uniroma2.dicii.isw2.issues.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum ResolutionType {

    SOLVED("solved"), FIXED("fixed"), WON_T_FIX("won't fix"), OTHER("other");

    @Getter
    private final String resolution;

    /**
     * Converts the given resolution string to a corresponding {@code ResolutionType} enum value.
     * If the resolution string does not match any predefined {@code ResolutionType} value,
     * or if the input is null, this method returns {@code null}.
     *
     * @param resolution the resolution string to convert to a {@code ResolutionType}.
     *                   This string is compared in a case-insensitive manner against
     *                   the resolution values of the {@code ResolutionType} enum.
     * @return the corresponding {@code ResolutionType} enum value if a match is found,
     * or {@code null} if no match exists or the input is null.
     */
    public static ResolutionType from(String resolution) {
        if (resolution == null) {
            return null;
        }
        for (ResolutionType type : ResolutionType.values()) {
            if (type.getResolution().equalsIgnoreCase(resolution)) {
                return type;
            }
        }
        return null;
    }
}
