package it.uniroma2.dicii.isw2.ml.exception;

public class MlException extends Exception {

    public MlException(String message) {
        super(message);
    }

    public MlException(String message, Throwable cause) {
        super(message, cause);
    }

}
