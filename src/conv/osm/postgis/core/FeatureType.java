package conv.osm.postgis.core;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.postgis.Geometry;

public class FeatureType
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    public static final int UNKNOWN = -1;

    private final Map<String, String[]> profile = new HashMap<String, String[]>();

    private String[] symbols;

    private int geometryType = UNKNOWN;

    private String[] topologyNames = null;

    FeatureType() {
    }

    public Map<String, String[]> getProfile() {
        return profile;
    }

    public int getGeometryType() {
        return geometryType;
    }

    public void setGeometryType(int geometryType) {
        this.geometryType = geometryType;
    }

    public boolean isTopological() {
        return null != topologyNames;
    }

    void setTopologyNames(String[] names) {
        topologyNames = names;
    }

    public String[] getTopologyNames() {
        return topologyNames;
    }

    public boolean match(Map<String, String> tags) {
        boolean match = false; // An empty profile matches nothing.
        for (Map.Entry<String, String[]> required : profile.entrySet()) {
            String value = tags.get(required.getKey());
            String[] options = required.getValue();

            match = false;
            for (String option : options) {
                if (null == option) {
                    // The key either should not appear at all or should
                    // have an empty value to match this option.
                    if ((null == value) || "".equals(value)) {
                        // Matches the null option.
                        match = true;
                        break;
                    }
                }
                else {
                    // The key should be present and have a matching
                    // value for this option.
                    if ("*".equals(option)) {
                        if (null != value) {
                            match = true;
                            break;
                        }
                    }
                    else if (option.equals(value)) {
                        match = true;
                        break;
                    }
                }
            }
            if (match) {
                // Keep on matching the rest of the required keys.
            }
            else {
                // One key did not match.
                break;
            }
        }
        return match;
    }

    void setSymbol(int i, String symbol) {
        symbols[i] = symbol;
    }

    public String getSymbol(int index) {
        return symbols[index];
    }

    void initSymbols(int n) {
        this.symbols = new String[n];
    }

    public String[] getSymbols() {
        return symbols;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        switch (geometryType) {
        case (Geometry.POINT):
            sb.append("point");
            break;
        case (Geometry.LINESTRING):
            sb.append("line");
            break;
        case (Geometry.LINEARRING):
            sb.append("ring");
            break;
        case (Geometry.POLYGON):
            sb.append("area");
            break;
        default:
            sb.append("unknown");
        }
        sb.append('{').append(symbols[0]);
        for (int i = 1; i < symbols.length; i++) {
            sb.append(',').append(symbols[i]);
        }
        sb.append('}');
        return sb.toString();
    }
}
