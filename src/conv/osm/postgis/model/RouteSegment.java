package conv.osm.postgis.model;

import java.util.Map;
import java.util.logging.Logger;

import org.postgis.LineString;
import org.postgis.Point;

import conv.osm.postgis.core.FeatureType;
import conv.osm.postgis.traffic.Rules;

public class RouteSegment extends Segment
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    
    private static final double COST_PROHIBITIVE = 24 * 60 * 60;

    // INSTANCE

    private double cost, reverseCost, toCost;
    private String rule;

    
    public RouteSegment(Segment s) {
        super(s.getFirstNd());
        setWayId(s.getWayId());
        setLastNd(s.getLastNd());
        setGeodesicLength(s.getGeodesicLength());
        // Do not copy geometry because the rules are different for routes.
        setPoints(s.getPoints());
    }

    public double getCost() {
        return cost;
    }

    public void setCost(double cost) {
        this.cost = cost;
    }

    public double getReverseCost() {
        return reverseCost;
    }

    public void setReverseCost(double reverseCost) {
        this.reverseCost = reverseCost;
    }

    public double getToCost() {
        return toCost;
    }

    public void setToCost(double toCost) {
        this.toCost = toCost;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    
    @Override
    public LineString getGeometry() {
        if (null == geometry) { // lazy initialization
            if (length() < 2) {
                throw new IllegalStateException(
                        "RouteSegment geometry not built yet.");
            }
            else if (length() == 3) {
                if (getPoints().get(0).equals(getPoints().get(2))) {
                    logger.fine("Way id=" + getWayId()
                            + " has a cycle between two nodes.");
                    /*
                     * This is a special case of a line from A to B to A.
                     * Even if this is a one-way road section, it goes both
                     * directions, so it can be converted into a simple
                     * two-way section.
                     */
                    if (getCost() < getReverseCost()) {
                        setReverseCost(getCost());
                    }
                    else {
                        setCost(getReverseCost());
                    }
                    getPoints().remove(2);
                    setLength(2);
                }
            }
            geometry = new LineString(getPoints().toArray(
                    new Point[getPoints().size()]));
            geometry.setSrid(SRID);
        }
        return geometry;
    }

    // TAG INTERPRETER

    public void calculateCost(OSMWay way, FeatureType feature) {
        Map<String, String> tags = way.getTagMap();
        Rules rules = Rules.forRegion(null); // TODO: rules for region
        float speed;
        try {
            speed = rules.getMaxSpeed(tags);
        }
        catch (NumberFormatException ex) {
            logger.fine("Unrecognized maximum speed ignored: "
                    + ex.getMessage() + " at " + way);
            speed = rules.getDefaultMaxSpeed(tags.get("highway"));
        }

        float cost_s = getGeodesicLength() / speed; // Cost in seconds.

        // TODO: inclined? other factors?
        switch (way.getDirection(null)) { // TODO: means of transportation
        case OSMWay.ONE_WAY:
            this.setCost(cost_s);
            this.setReverseCost(COST_PROHIBITIVE);
            break;
        case OSMWay.ONE_WAY_REVERSE:
            this.setCost(COST_PROHIBITIVE);
            this.setReverseCost(cost_s);
            break;
        default:
        case OSMWay.TWO_WAY:
            this.setCost(cost_s);
            this.setReverseCost(cost_s);
            break;
        }
    }
}
