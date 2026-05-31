package it.uniroma2.dicii.isw2.repo.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * An immutable representation of a Git commit.
 */
public record Commit(String id, String shortMessage, String fullMessage, String authorName, String authorEmail,

                     @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ") ZonedDateTime date,

                     List<String> parentIds) {
}