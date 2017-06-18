package conv.osm.postgis.core;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.postgis.Geometry;

import conv.osm.postgis.dao.DAO;
import conv.osm.postgis.dao.DAOFactory;
import conv.osm.postgis.dao.OutputTarget;
import conv.osm.postgis.dao.RouteSegmentDAO;
import conv.osm.postgis.dao.SegmentDAO;
import conv.osm.postgis.dao.WayDAO;
import conv.osm.postgis.model.Feature;
import conv.osm.postgis.model.OSMDataException;
import conv.osm.postgis.model.OSMNode;
import conv.osm.postgis.model.OSMPrimitive;
import conv.osm.postgis.model.OSMRelation;
import conv.osm.postgis.model.OSMWay;
import conv.osm.postgis.model.RouteSegment;
import conv.osm.postgis.model.Segment;
import conv.osm.postgis.model.Status;

public class PostGISBuilder implements DataBuilder
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    private OutputTarget output;
    private FeatureRecognizer features;

    private boolean processTopology;
    private boolean processFeatures;
    private boolean processBounds;

    public PostGISBuilder(OutputTarget output) {
        this.output = output;
    }

    public OutputTarget getOutputTarget() {
        return output;
    }

    public void setProcessTopology(boolean flag) {
        this.processTopology = flag;
    }

    public void setProcessBounds(boolean flag) {
        this.processBounds = flag;
    }

    public void setProcessFeatures(boolean flag) {
        this.processFeatures = flag;
    }

    public void setFeatures(FeatureRecognizer filter) {
        features = filter;
    }

    @Override
    public int generate(OSMPrimitive osm) {
        int result = 0;
        if (null == osm) {
            throw new NullPointerException("Missing OSM data primitive.");
        }
        else {
            try {
                if (osm instanceof OSMWay) {
                    result = generateWay((OSMWay) osm);
                }
                else if (osm instanceof OSMNode) {
                    result = generateNode((OSMNode) osm);
                }
                else if (osm instanceof OSMRelation) {
                    result = generateRelation((OSMRelation) osm);
                }
                else {
                    throw new IllegalArgumentException(
                            "Unknown primitive type "
                                    + osm.getClass().getName());
                }
            }
            catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
        return result;
    }

    /**
     *
     * @throws SQLException
     */
    public void generate() throws SQLException {
        String msg, goals;
        if (processTopology || processFeatures) {
            StringBuilder sb = new StringBuilder(64);
            if (processFeatures) {
                sb.append("PostGIS geometry layers");
                if (processTopology) {
                    sb.append(" and ");
                }
            }
            if (processTopology) {
                sb.append("routing topology");
            }
            goals = sb.toString();
            sb.setLength(0);
            sb.append("Generating ").append(goals).append(".");
            msg = sb.toString();
            logger.info(msg);
        }
        else {
            msg = "Generation not requested. "
                    + "Geometry layers and routing topology bypassed.";
            logger.info(msg);
            return;
        }
        WayDAO wayDAO = WayDAO.getInstance();
        long ways = 0;
        for (OSMWay way : wayDAO.readUnrecognizedNodes()) {
            ways++;
            logger.finest("Generating " + way);
            int rows = generateWay(way);
            way.setStatus(Status.NONE);
            wayDAO.updateStatus(way);
            output.commit(rows);
        }
        if (ways < 1) {
            msg = "Generation failed:"
                    + " Did not find any unprocessed OSM ways.";
            logger.severe(msg);
            output.rollback();
        }
        msg = "Done generating " + goals + ".";
        logger.info(msg);
    }

    private int generateNode(OSMNode node) throws SQLException {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    /**
     * 
     * @param way
     * @return
     * @throws SQLException
     */
    private int generateWay(OSMWay way) throws SQLException {
        FeatureType ft = features.recognize(way);
        if (null == ft) {
            logger.fine("Unrecognized " + way + ".");
            return 0;
        }
        else {
            logger.finest("Recognized " + way + " as " + ft + ".");
        }
        int result = 0;
        if (processTopology) {
            if (ft.isTopological()) {
                result += generateTopology(way, ft);
            }
        }
        if (processFeatures) {
            try {
                result += generateFeatures(way, ft);
            }
            catch (OSMDataException osm) {
                logger.log(Level.FINE, "Dropped " + way, osm);
            }
        }
        return result;
    }

    private int generateRelation(OSMRelation relation) throws SQLException {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    private int generateTopology(OSMWay way, FeatureType f)
            throws SQLException {
        ArrayList<Segment> segments = way.split();

        int rows = 0;
        for (String name : f.getTopologyNames()) {
            DAO dao = DAOFactory.getDefaultDAOFactory().getDAO(name);
            if (dao instanceof SegmentDAO) {
                
                SegmentDAO segmentDAO = (SegmentDAO) dao;
                segmentDAO.deleteSegments(way.getId());
                for (Segment s : segments) {
                    rows += segmentDAO.create(s);
                }
            }
            else if (dao instanceof RouteSegmentDAO) {
                RouteSegmentDAO routeDAO = (RouteSegmentDAO) dao;
                routeDAO.deleteSegments(way.getId());
                for (Segment s : segments) {
                    RouteSegment route = new RouteSegment(s);
                    route.setRule(null); // TODO: Turning restriction
                    route.setToCost(0); // TODO: Cost of restricted passage
                    route.calculateCost(way, f);
                    rows += routeDAO.create(route);
                }
            }
            else throw new IllegalArgumentException(
                    "Unknown topological graph: \"" + name + "\".");
        }
        if (rows < 1) {
            logger.fine("Couldn't create topology for " + way
                    + " recognized as " + f + ".");
        }
        return rows;
    }

    private int generateFeatures(OSMWay way, FeatureType featureType)
            throws SQLException, OSMDataException {
        if (null == featureType.getSymbol(0)) {
            logger.finer("Skipped " + way + " recognized as " + featureType
                    + ".");
            return 0;
        }
        if ((featureType.getGeometryType() == Geometry.POLYGON)
                || (featureType.getGeometryType() == Geometry.LINEARRING)) {
            if (way.validatePoints().length < 3) {
                logger.fine("Ignored Way id=" + way.getId()
                        + " for not having enough points to be an area."
                        + " Was recognized as " + featureType
                        + " from tags " + way.getTagMap());
                return 0;
            }
        }
        DAO<Feature, Long> dao = DAOFactory.getDefaultDAOFactory().getDAO(
                featureType.getSymbol(0));
        Feature feature = new Feature(way);
        feature.setType(featureType);
        int result = 0;
        if (OutputTarget.merge) {
            result = dao.update(feature);
        }
        if (result > 0) {
            return result;
        }
        else {
            result = dao.create(feature);
        }
        if (result < 0) {
            result = -result;
        }
        else if (0 == result) {
            logger.fine("Couldn't create feature for " + way
                    + " recognized as " + featureType + ".");
        }
        return result;
    }

    public void processBounds() {
        if (!processBounds) return;
        logger.warning("RESULT: Processing bounds."
                + " incorrect results, and"
                + " you may run out of memory with large data sets.");
        for (SegmentDAO dao : SegmentDAO.getInstances()) {
            logger.info("Processing bounds for " + dao.getFullName() + ".");
            SortedSet<Segment> queue = new TreeSet<Segment>();
            try {
                // Read all segments in memory.
                for (Segment s : dao.readAll()) {
                    queue.add(s);
                }
                logger.fine("Loaded " + queue.size()
                        + " segments in memory.");

                while (!queue.isEmpty()) {
                    Segment s = queue.first();
                    Deque<Segment> path;
                    try {
                        path = dao.readJoining(s, true);
                    }
                    catch (SQLException ex) {
                        throw new SQLException(s.toString(), ex);
                    }
                    if (null != path) {
                        long faceId = path.getFirst().getId() * 2;
                        queue.removeAll(path);
                        Iterator<Segment> it = path.descendingIterator();
                        while (it.hasNext()) {
                            Segment ps = it.next();
                            logger.finest("Face.id=" + faceId + " " + ps);
                        }
                    }
                    else {
                        queue.remove(s);
                    }
                    logger.finest(queue.size()
                            + " segments to be processed.");
                }
            }
            catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed processing bounds for "
                        + dao.getFullName() + ".", e);
            }
        }
        logger.info("Done processing bounds.");
    }

}
