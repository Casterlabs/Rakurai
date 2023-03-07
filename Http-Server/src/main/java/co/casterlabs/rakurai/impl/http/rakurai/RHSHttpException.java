package co.casterlabs.rakurai.impl.http.rakurai;

import co.casterlabs.rakurai.io.http.HttpStatus;

public class RHSHttpException extends Exception {
    private static final long serialVersionUID = 4899100353178913026L;

    public final HttpStatus status;

    public RHSHttpException(HttpStatus reason) {
        super(reason.getStatusString());
        this.status = reason;
    }

}
