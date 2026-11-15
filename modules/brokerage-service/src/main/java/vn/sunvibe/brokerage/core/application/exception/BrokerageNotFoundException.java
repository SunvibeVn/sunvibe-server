package vn.sunvibe.brokerage.core.application.exception;

/** The lead is unknown, malformed or not visible to the caller; one answer for all three, naming no lead. */
public class BrokerageNotFoundException extends RuntimeException {

    public BrokerageNotFoundException() {
        super("The lead was not found.");
    }
}
