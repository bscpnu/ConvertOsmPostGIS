package conv.osm.postgis;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TimeZone;

import org.postgis.Point;

public class Util
{
    public static final DateFormat ISO8601;
    static {
        ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        ISO8601.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private static final DecimalFormat twoDig = new DecimalFormat("00");
    private static final DecimalFormat oneDec = new DecimalFormat("0.0");
    private static final DecimalFormat threeDec = new DecimalFormat("0.000");

    private static final long SECOND = 1000, MINUTE = 60 * SECOND,
            HOUR = 60 * MINUTE, DAY = 24 * HOUR;

    private static final double MULTI = 10000000d;

    public static String megabytes(long bytes) {
        return oneDec.format(bytes / (1024f * 1024f));

    }

    public static String oneDecimal(float f) {
        return oneDec.format(f);
    }

    public static String threeDecimal(float f) {
        return threeDec.format(f);
    }

    public static String dhms(long duration) {
        int days = (int) (duration / DAY);
        duration %= DAY;
        int hours = (int) (duration / HOUR);
        duration %= HOUR;
        int minutes = (int) (duration / MINUTE);
        duration %= MINUTE;
        float seconds = duration / 1000f;

        return days + " d " + twoDig.format(hours) + ":"
                + twoDig.format(minutes) + ":" + twoDig.format(seconds);
    }

    public static final int intify(double coord) {
        return (int) StrictMath.floor((coord * MULTI) + .5d);
    }

    public static final double doublify(int coord) {
        return coord / MULTI;
    }

    public static String validateNull(String value) {
        if (null == value) {
        }
        else if ("".equals(value) || "null".equals(value)) {
            // fail in JSON Parser
            value = null;
        }
        return value;
    }

    public static boolean equalsOrNull(Object a, Object b) {
        return (null == a) ? (null == b) : a.equals(b);
    }

    public static double greatCircleArcLength(Point fromDeg, Point toDeg) {

        double fromLat = Math.toRadians(fromDeg.getY());
        double toLat = Math.toRadians(toDeg.getY());
        double dLon = Math.toRadians(circularDifference(fromDeg.getX(),
                toDeg.getX()));
        double dLat = Math.toRadians(circularDifference(fromDeg.getY(),
                toDeg.getY()));

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(fromLat) * Math.cos(toLat) * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);

        return 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static double circularDifference(double from, double to) {
        final double diff = (to - from) % 360d;
        return diff > 180d ? 360d - diff : diff;
    }

    public static Point[] sphericalToCartesian(Point[] degrees) {
        Point[] cartesian = new Point[degrees.length];
        for (int i = 0; i < degrees.length; i++) {
            cartesian[i] = sphericalToCartesian(degrees[i]);
        }
        return cartesian;
    }

    public static Point sphericalToCartesian(Point deg) {
        double longitude = deg.getX();
        double latitude = deg.getY();

        // angle from North Pole down to latitude in radians
        double phi = Math.toRadians(90d - latitude);

        // angle from Greenwich to longitude in radians
        double theta = Math.toRadians(longitude);

        double sinPhi = Math.sin(phi);
        double x = sinPhi * Math.cos(theta);
        double y = sinPhi * Math.sin(theta);
        double z = Math.cos(phi);

        return new Point(x, y, z);
    }

    /**
     * @param polygon3d
     *            a polygon of 3D Cartesian coordinates on a unit sphere.
     * @return
     */
    private static ArrayList<Point[]> triangulate(Point[] polygon3d) {
        ArrayList<Point[]> triangles;
        if (3 == polygon3d.length) {
            // It's already a triangle.
            triangles = new ArrayList<Point[]>(1);
            triangles.add(polygon3d);
            return triangles;
        }
        /*
         * To avoid rounding errors or hitting any of the vertices of the
         * polygon, take a point within its first three vertices and use it
         * as the anchor.
         * TODO: This doesn't guarantee anything.
         */
        Point[] firstTriangle = Arrays.copyOf(polygon3d, 3);
        Point anchor = Util.sphericalCentroid(firstTriangle);

        triangles = new ArrayList<Point[]>(polygon3d.length);
        /*
         * Triangulate using consecutive points of the polygon and the
         * chosen anchor point.
         */
        for (int i = 0; i < polygon3d.length; i++) {
            Point first = polygon3d[i];
            Point second = polygon3d[(i + 1) % polygon3d.length];
            triangles.add(new Point[] { first, second, anchor });
        }
        return triangles;
    }

    public static Point sphericalCentroid(Point[] triangle) {
        double x = 0, y = 0, z = 0; // centroid vector
        for (int i = 0; i < 3; i++) {
            x += triangle[i].getX();
            y += triangle[i].getY();
            z += triangle[i].getZ();
        }
        x /= 3d;
        y /= 3d;
        z /= 3d;

        // centroid vector length
        double c = Math.sqrt((x * x) + (y * y) + (z * z));

        return new Point(x / c, y / c, z / c);
    }

    public static double sphericalAngle(Point a, Point b) {
        return Math.acos(dotProduct(a, b));
    }

    public static double dotProduct(Point a, Point b) {
        return (a.getX() * b.getX()) + (a.getY() * b.getY())
                + (a.getZ() * b.getZ());
    }

    public static Point crossProduct(Point a, Point b) {
        return new Point((a.getY() * b.getZ()) - (a.getZ() * b.getY()),
                (a.getZ() * b.getX()) - (a.getX() * b.getZ()),
                (a.getX() * b.getY()) - (a.getY() * b.getX()));
    }

    public static double sphericalExcess(Point[] triangle) {
        /*
         * Formula for the spherical excess
         * 
         * tan(E/4) = sqrt(tan(s/2)*tan((s-a)/2)*tan((s-b)/2)*tan((s-c)/2))
         * 
         * where
         * 
         * a, b, c = sides of spherical triangle
         * s = (a + b + c)/2
         */

        // Calculate the angular length of each side in radians.
        double[] arc = new double[3]; // { a, b, c }
        double s = 0;
        for (int i = 0; i < 3; i++) {
            arc[i] = sphericalAngle(triangle[i], triangle[(i + 1) % 3]);
            s += arc[i];
        }
        s /= 2;

        double p = Math.tan(s / 2);
        for (int i = 0; i < 3; i++) {
            p *= Math.tan((s - arc[i]) / 2);
        }
        return (clockwise(triangle) ? 4 : -4) * Math.atan(Math.sqrt(p));
    }

    public static double angle3D(Point a, Point b, Point c) {
        Point ab = vectorDifference(a, b);
        Point bc = vectorDifference(b, c);
        double angle = Math.acos(dotProduct(ab, bc)
                / (length3D(ab) * length3D(bc)));
        if (0 != angle) {
            // TODO: Optimize this! The clockwise method has overlaps.
            if (clockwise(new Point[] { a, b, c })) {
                angle *= -1;
            }
        }
        return angle;
    }

    public static Point normalize3D(Point v) {
        System.out.println("Normalizing " + v);
        double len = length3D(v);
        return new Point(v.getX() / len, v.getY() / len, v.getZ() / len);
    }

    private static boolean clockwise(Point[] triangle) {
        
        Point a = vectorDifference(triangle[0], triangle[1]);
        Point b = vectorDifference(triangle[1], triangle[2]);
        Point normal = crossProduct(a, b);
        double normalLen = squaredLength(normal);

        
        Point reference = vectorSum(triangle);
        double refLen = squaredLength(reference);

        Point combined = vectorSum(reference, normal);
        double combinedLen = squaredLength(combined);

        
        return combinedLen < (normalLen < refLen ? refLen : normalLen);
    }

    public static Point vectorDifference(Point from, Point to) {
        return new Point(to.getX() - from.getX(), to.getY() - from.getY(),
                to.getZ() - from.getZ());
    }

    public static Point vectorSum(Point[] v) {
        double x = 0, y = 0, z = 0;
        for (int i = 0; i < 3; i++) {
            x += v[i].getX();
            y += v[i].getY();
            z += v[i].getZ();
        }
        return new Point(x, y, z);
    }

    public static Point vectorSum(Point a, Point b) {
        return new Point(b.getX() + a.getX(), b.getY() + a.getY(), b.getZ()
                + a.getZ());
    }

    private static double squaredLength(Point v) {
        return v.getX() * v.getX() + v.getY() * v.getY() + v.getZ()
                * v.getZ();
    }

    private static double length3D(Point v) {
        return Math.sqrt(squaredLength(v));
    }

    public static double sphericalArea(Point[] polygon) {
        if (polygon.length < 3) {
            throw new IllegalArgumentException(
                    "Less than three vertices cannot form an area.");
        }
        double area = 0;
        for (Point[] triangle : triangulate(sphericalToCartesian(polygon))) {
            area += sphericalExcess(triangle);
        }
        return area;
    }
}
