package conv.osm.postgis.model;

public enum OSMDataType {
    NODE {
        @Override
        public String toString() {
            return "n";
        }
    },
    WAY {
        @Override
        public String toString() {
            return "w";
        }
    },
    RELATION {
        @Override
        public String toString() {
            return "r";
        }
    };

    public static final OSMDataType parse(String type) {
        switch (type.charAt(0)) {
        case ('n'):
            return NODE;
        case ('w'):
            return WAY;
        case ('r'):
            return RELATION;
        }
        throw new IllegalArgumentException("Unknown type: \"" + type + "\"");
    }
}