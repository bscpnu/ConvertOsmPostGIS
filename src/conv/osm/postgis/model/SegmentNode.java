package conv.osm.postgis.model;

import org.postgis.Point;

public class SegmentNode
{
    private int crossed;
    private Point geometry;

    public int getCrossed() {
        return crossed;
    }

    public void setCrossed(int crossed) {
        this.crossed = crossed;
    }

    public Point getGeometry() {
        if (null == geometry) { // Fail fast
            throw new IllegalStateException("Missing geometry.");
        }
        return geometry;
    }

    public void setGeometry(Point geometry) {
        if (null == geometry) {
            throw new IllegalArgumentException("Missing geometry.");
        }
        this.geometry = geometry;
    }
}
