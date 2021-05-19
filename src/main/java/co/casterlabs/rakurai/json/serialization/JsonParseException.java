package co.casterlabs.rakurai.json.serialization;

import lombok.NonNull;

public class JsonParseException extends Exception {
    private static final long serialVersionUID = 898397259977630282L;

    public JsonParseException(@NonNull String reason) {
        super(reason);
    }

    public JsonParseException(@NonNull Exception e) {
        super(e);
    }

}
