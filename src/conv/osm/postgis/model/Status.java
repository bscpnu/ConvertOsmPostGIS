package conv.osm.postgis.model;

public enum Status {
    NONE(0), RECOGNIZE(1);

    private final int code;

    Status(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /**
     * Creates a Status from the numeric code.
     * 
     * @param code
     * @return
     */
    public static Status code(int code) {
        switch (code) {
        case 0:
            return NONE;
        case 1:
            return RECOGNIZE;
        default:
            throw new IllegalArgumentException("Unknown status code="
                    + code);
        }
    }

    @Override
    public String toString() {
        return String.valueOf(code);
    }
}