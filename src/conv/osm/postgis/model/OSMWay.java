package conv.osm.postgis.model;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.postgis.LineString;
import org.postgis.LinearRing;
import org.postgis.Point;
import org.xml.sax.Attributes;

import conv.osm.postgis.dao.WayDAO;

public class OSMWay extends OSMPrimitive
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    private static final int DIRECTION_PENDING = -2;
    public static final int ONE_WAY_REVERSE = -1;
    public static final int DIRECTION_UNDEFINED = 0;
    public static final int ONE_WAY = 1;
    public static final int TWO_WAY = 2;

    // INSTANCE

    private final ArrayList<Long> nodes = new ArrayList<Long>(16);

    private LineString lineString = null;
    private LinearRing ring = null;
    private Point[] points = null;

    private int direction = DIRECTION_PENDING;

    public OSMWay() {
    }

    public OSMWay(long id) {
        setId(id);
    }

    public ArrayList<Long> getNodes() {
        return nodes;
    }

    // SAX PARSER

    public void parseNdRef(Attributes attributes) {
        String ref = attributes.getValue("ref");
        getNodes().add(Long.parseLong(ref));
    }

    public int getDirection(Object means) {
        if (DIRECTION_PENDING == direction) {
            String v = getTagMap().get("oneway");
            if (null == v) {
            }
            else {
                if (v.equals("-1") || v.equals("reverse")) {
                    direction = ONE_WAY_REVERSE;
                }
                else if (v.equals("yes") || v.equals("true")
                        || v.equals("1")) {
                    direction = ONE_WAY;
                }
                else if (v.equals("no") || v.equals("false")) {
                    direction = TWO_WAY;
                }
                else {
                    direction = DIRECTION_UNDEFINED;
                    logger.fine(this + " undefined tag oneway=\"" + v
                            + "\" ignored.");
                }
            }
            // Do not override the explicitly set oneway tag.
            if (DIRECTION_PENDING == direction) {
                // The "oneway" tag was not set.
                String highway = getTagMap().get("highway");
                if ((null != highway) && (highway.startsWith("motorway"))) {
                    direction = ONE_WAY;
                }
                else if ("roundabout".equals(getTagMap().get("junction"))) {
                    direction = ONE_WAY;
                }
            }
            // Assume it's two-way.
            if (DIRECTION_PENDING == direction) {
                direction = TWO_WAY;
            }
        }
        return direction;
    }

    public LinearRing getLinearRing() throws SQLException, OSMDataException {
        if (null == ring) {
            ring = new LinearRing(getPointsClosed());
            ring.setSrid(SRID);
        }
        return ring;
    }

    public Point[] getPointsClosed() throws SQLException, OSMDataException {
        Point[] points = validatePoints();
        if (isClosed(points)) {
            // The ring is closed.
        }
        else {
            if (points.length < 3) {
                throw new OSMDataException(this,
                        "Not an area and cannot be closed");
            }
            // Make room for one more point.
            logger.fine("Was not closed: " + this);
            points = Arrays.copyOf(points, points.length + 1);
            // Close the ring.
            points[points.length - 1] = points[0];
        }
        return points;
    }

    private static boolean isClosed(Point[] points) throws SQLException {
        return points[0].equals(points[points.length - 1]);
    }

    public LineString getLineString() throws SQLException, OSMDataException {
        if (null == lineString) {
            lineString = new LineString(validatePoints());
            lineString.setSrid(SRID);
        }
        return lineString;
    }

    public Point[] validatePoints() throws SQLException, OSMDataException {
        if (null == points) {
            points = WayDAO.getInstance().validatePoints(this);
        }
        return points;
    }

    public ArrayList<Segment> split() throws SQLException {
        Map<Long, SegmentNode> nodeMap;
        try {
            nodeMap = WayDAO.getInstance().readSegmentNodes(this);
        }
        catch (OSMDataException e) {
            logger.log(Level.FINE, "Failed to read segment nodes: " + this,
                    e);
            return null;
        }

        ArrayList<Segment> segmentList = new ArrayList<Segment>();
        Segment segment = null;
        SegmentNode segNode = null;
        int sequence = 0;

        int nodes = getNodes().size();
        long nd = -1;
        for (Long nodeId : getNodes()) {
            nd = nodeId;
            sequence++;

            segNode = nodeMap.get(nd);
            if (null == segNode) {
                throw new RuntimeException("OSMNode.id=" + nd
                        + " not found in the node map.");
            }

            if (null == segment) {
                segment = new Segment(nd);
                segment.setWay(this);
            }
            segment.addPoint(segNode.getGeometry());

            if ((sequence > 1) && (sequence < nodes)) {
                // It's an intermediate node.
                if (segNode.getCrossed() > 1) {
                    /*
                     * An intermediate node is used more than once, so the
                     * way must be split here.
                     */
                    segment.setLastNd(nd);
                    /*
                     * There is also the
                     * AbstractDAO.spheroidLength(Geometry)
                     * function, but that is much slower.
                     */
                    segment.setGeodesicLength((float) segment.sphericalLength());
                    segmentList.add(segment);

                    segment = new Segment(nd);
                    segment.setWay(this);
                    segment.addPoint(segNode.getGeometry());
                }
            }
        }
        segment.setLastNd(nd);
        segment.setGeodesicLength((float) segment.sphericalLength());
        segmentList.add(segment);

        return segmentList;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        OSMWay other;
        if (obj instanceof OSMWay) {
            other = (OSMWay) obj;
        }
        else {
            return false;
        }
        return nodes.equals(other.nodes);
    }

    @Override
    public String toString() {
        return "OSMWay{id=" + getId() + ",changeset=" + getChangeSet()
                + ",time=" + getTime() + ",version=" + getVersion()
                + ",tags=" + getTagMap().toString() + ",nodes="
                + getNodes().toString() + "}";
    }
}
