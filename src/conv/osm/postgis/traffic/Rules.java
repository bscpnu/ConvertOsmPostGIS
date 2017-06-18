package conv.osm.postgis.traffic;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class Rules
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    private static final Rules defaultRules;
    static {
        defaultRules = new Rules();
    }

    private static final Map<String, Rules> regionRules = new HashMap<String, Rules>();

    public static final Rules forRegion(String region) {
        if (null != region) {
            Rules rules = regionRules.get(region);
            if (null != rules) {
                return rules;
            }
        }
        return defaultRules;
    }

    private final Map<String, Float> defaultMinSpeeds = new HashMap<String, Float>();

    private final Map<String, Float> defaultMaxSpeeds = new HashMap<String, Float>();

    private float defaultMaxSpeed = 50f * (1000f / 3600f);

    private Rules() {
    }

    /**
     * Creates a new traffic Rules object for the given region.
     * 
     * @param region
     */
    public Rules(String region) {
        Rules rules = regionRules.get(region);
        if (null == rules) {
            regionRules.put(region, this);
        }
        else {
            throw new IllegalArgumentException("Rules for " + region
                    + " already exist.");
        }
    }

    /**
     * Returns the default minimum speed limit for a road of unknown type.
     * 
     * @return speed in meters per second.
     */
    public float getDefaultMinSpeed() {
        return 0;
    }

    /**
     * Returns the default minimum speed limit for the given road type.
     * 
     * @param type
     * @return speed in meters per second.
     */
    public float getDefaultMinSpeed(String type) {
        Float speed = defaultMinSpeeds.get(type);
        if (null == speed) {
            return getDefaultMinSpeed();
        }
        return speed;
    }

    /**
     * Returns the default maximum speed limit for a road of unknown type.
     * 
     * @return speed in meters per second.
     */
    public float getDefaultMaxSpeed() {
        return defaultMaxSpeed;
    }

    /**
     * Returns the default maximum speed limit for the given road type.
     * 
     * @param type
     * @return speed in meters per second.
     */
    public float getDefaultMaxSpeed(String type) {
        Float speed = defaultMaxSpeeds.get(type);
        if (null == speed) {
            return getDefaultMaxSpeed();
        }
        return speed;
    }

    /**
     * @param tags
     * @return
     */
    public float getMaxSpeed(Map<String, String> tags)
            throws NumberFormatException {
        float speed = 0;
        String v = tags.get("maxspeed");
        if (null == v) {
            // TODO: better filtering system for detecting different roads
            speed = getDefaultMaxSpeed(tags.get("highway"));
        }
        else {
            // The max speed value has been explicitly set.
            if (v.endsWith("mph")) {
                // Interpret from the Imperial system (miles per hour)
                speed = Float.valueOf(v.substring(0, v.length() - 3));
                speed *= (1609.344 / 3600f); // Convert to m/s
            }
            else {
                speed = Float.valueOf(v);
                speed *= (1000f / 3600f); // Convert to m/s
            }
        }
        return speed;
    }
}
