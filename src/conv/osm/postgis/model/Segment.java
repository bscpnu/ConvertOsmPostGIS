package conv.osm.postgis.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;

import org.postgis.LineString;
import org.postgis.Point;

import conv.osm.postgis.Util;

public class Segment extends Entity
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    // INSTANCE

    private long wayId = -1;
    private long firstNd, lastNd;
    private long leftFace, rightFace;

    public void setLeftFace(long faceId) {
        this.leftFace = faceId;
    }

    public long getLeftFace() {
        return leftFace;
    }

    public void setRightFace(long faceId) {
        this.rightFace = faceId;
    }

    public long getRightFace() {
        return rightFace;
    }

    private int nodes = 0;
    private float geodesicLength = -1;

    protected LineString geometry = null;
    private ArrayList<Point> points = new ArrayList<Point>();

    public Segment(long firstNd) {
        setId(-1);
        setFirstNd(firstNd);
    }

    public long getWayId() {
        return wayId;
    }

    public void setWayId(Long wayId) {
        this.wayId = wayId;
    }

    public void setWay(OSMWay way) {
        wayId = way.getId();
    }

    public float getGeodesicLength() {
        return geodesicLength;
    }

    public void setGeodesicLength(float meters) {
        geodesicLength = meters;
    }

    public long getFirstNd() {
        return firstNd;
    }

    public void setFirstNd(long nd) {
        firstNd = nd;
    }

    public long getLastNd() {
        return lastNd;
    }

    public void setLastNd(long nd) {
        lastNd = nd;
    }

    public double getX1() {
        return getXY1().getX();
    }

    public double getY1() {
        return getXY1().getY();
    }

    Point getXY1() {
        return points.get(0);
    }

    public double getX2() {
        return getXY2().getX();
    }

    public double getY2() {
        return getXY2().getY();
    }

    Point getXY2() {
        return points.get(nodes - 1);
    }

    void setPoints(ArrayList<Point> points) {
        geometry = null;
        this.points = points;
        nodes = points.size();
    }

    private void setPoints(Point[] points) {
        this.points.clear();
        this.points.addAll(Arrays.asList(points));
        nodes = points.length;
    }

    protected ArrayList<Point> getPoints() {
        return points;
    }

    public void addPoint(Point p) {
        points.add(p);
        nodes = points.size();
    }

    public Point getPoint(int i) {
        return points.get(i);
    }

    public void setLength(int length) {
        nodes = length;
    }

    public int length() {
        return nodes;
    }

    public LineString getGeometry() {
        Point[] p = null;
        if (null == geometry) { // lazy initialization
            if (nodes < 2) {
                throw new IllegalStateException(
                        "Segment geometry not built yet.");
            }
            else if (nodes == 3) {
                if (points.get(0).equals(points.get(2))) {
                    logger.fine("Way id=" + wayId
                            + " has a cycle between two nodes.");
                    
                    p = new Point[] { points.get(0), points.get(1) };
                }
            }
            if (null == p) {
                p = points.toArray(new Point[nodes]);
            }
            geometry = new LineString(p);
            geometry.setSrid(SRID);
        }
        return geometry;
    }

    public void setGeometry(LineString geometry) {
        this.geometry = geometry;
        setPoints(geometry.getPoints());
    }

    public double sphericalLength() {
        double length = 0d;
        int i = 0;
        Point previous = points.get(0);
        do {
            final Point current = points.get(++i);
            length += Util.greatCircleArcLength(previous, current)
                    * SPHERICAL_EARTH_RADIUS;
            previous = current;
        } while (i < nodes - 1);
        return length;
    }

    @Override
    public String toString() {
        return "Segment{id=" + getId() + ",way=" + getWayId() + ",nodes="
                + length() + ",first_nd=" + getFirstNd() + ",last_nd="
                + getLastNd() + "}";
    }
}
