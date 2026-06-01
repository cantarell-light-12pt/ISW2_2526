package it.uniroma2.dicii.isw2.repo.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.ZonedDateTime;

/**
 * An immutable representation of a Git Tag.
 */
public record Tag(String label, String commitId,
                  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ") ZonedDateTime date) {
}
