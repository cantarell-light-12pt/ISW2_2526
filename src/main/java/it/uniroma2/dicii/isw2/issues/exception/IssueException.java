package it.uniroma2.dicii.isw2.issues.exception;

public class IssueException extends Exception {

    public IssueException(String message) {
        super(message);
    }

    public IssueException(String message, Throwable cause) {
        super(message, cause);
    }
}
