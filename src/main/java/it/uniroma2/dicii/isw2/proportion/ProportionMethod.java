package it.uniroma2.dicii.isw2.proportion;

import it.uniroma2.dicii.isw2.proportion.exception.ProportionException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The variants of the Proportion approach that can be selected at runtime to estimate the injected
 * version of a defect, as defined by Vandehei et al., "Leveraging the Defects Life Cycle to Label
 * Affected Versions and Defective Classes" (TOSEM 30(2), 2021), Section 3.2.
 */
@RequiredArgsConstructor
public enum ProportionMethod {

    /**
     * Assumes the injected version simply corresponds to the opening version.
     */
    SIMPLE("simple"),

    /**
     * Computes the proportion of the current defect as the average proportion of the defects fixed in
     * the previous versions of the same project.
     */
    INCREMENTAL("incremental");

    @Getter
    private final String method;

    /**
     * Converts the given method name to the corresponding {@code ProportionMethod} enum value.
     * The comparison is case-insensitive.
     * <p>
     * Unlike the other enums of this project, no fallback value is returned for an unknown name: the
     * method is read from the configuration, so a typo must fail loudly instead of silently applying an
     * unintended estimation method to the whole dataset.
     *
     * @param method the method name to convert, e.g. "simple" or "incremental"
     * @return the corresponding {@code ProportionMethod} enum value
     * @throws ProportionException if the name does not match any known method
     */
    public static ProportionMethod from(String method) throws ProportionException {
        for (ProportionMethod m : values()) {
            if (m.getMethod().equalsIgnoreCase(method)) {
                return m;
            }
        }
        String known = Arrays.stream(values()).map(ProportionMethod::getMethod).collect(Collectors.joining(", "));
        throw new ProportionException("Unknown proportion method '" + method + "'. Known methods are: " + known);
    }
}
