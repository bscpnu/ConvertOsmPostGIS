package conv.osm.postgis.core;

import java.text.ParseException;
import java.util.Stack;
import java.util.logging.Logger;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import conv.osm.postgis.dao.Counter;
import conv.osm.postgis.model.OSMChangeSet;
import conv.osm.postgis.model.OSMNode;
import conv.osm.postgis.model.OSMPrimitive;
import conv.osm.postgis.model.OSMRelation;
import conv.osm.postgis.model.OSMWay;

public class OSMHandler extends DefaultHandler
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    static final int QUEUE_LIMIT = 2000;

    private final Stack<OSMPrimitive> stack = new Stack<OSMPrimitive>();

    private Monitor.QueueWrapper queue = null;

    private Locator locator = null;
    private boolean osmFound = false; // if the <osm> element has been found.

    private long throttle = 0L;

    private long startAt = 0L;

    private boolean processChangeSets;
    private boolean processNodes;
    private boolean processWays;
    private boolean processRelations;

    OSMHandler() {
        super();
    }

    void setQueue(Monitor.QueueWrapper queue) {
        this.queue = queue;
    }

    /**
     * 
     * @param line
     */
    public void setStartAt(long line) {
        startAt = line;
    }

    long getThrottle() {
        return throttle;
    }

    @Override
    public void setDocumentLocator(Locator loc) {
        setLocator(loc);
    }

    @Override
    public void startElement(String uri, String localName, String qName,
            Attributes attributes) throws SAXException {
        OSMPrimitive p = null;
        if ("node".equals(localName)) {
            p = new OSMNode();
        }
        else if ("way".equals(localName)) {
            p = new OSMWay();
        }
        else if ("relation".equals(localName)) {
            p = new OSMRelation();
        }
        else if ("changeset".equals(localName)) {
            p = new OSMChangeSet();
        }
        if (null == p) { // It's not a data primitive.
            if (stack.isEmpty()) {
                if ("osm".equals(localName)) {
                    logger.finer("Start of OSM data: <osm>");
                    osmFound = true;
                }
                else if ("bound".equals(localName)) {
                    // TODO: Bounding boxes are not used, nothing to do.
                }
            }
            else {
                if ("tag".equals(localName)) {
                    OSMPrimitive parent = stack.peek();
                    parent.parseSAXTag(attributes);
                }
                else if ("nd".equals(localName)) {
                    OSMWay parent = (OSMWay) stack.peek();
                    parent.parseNdRef(attributes);
                }
                else if ("member".equals(localName)) {
                    OSMRelation parent = (OSMRelation) stack.peek();
                    parent.parseMember(attributes);
                }
                else {
                    logger.finest("Ignored <" + localName + ">");
                }
            }
        }
        else { // It's one of the data primitives.
            if (!osmFound) {
                throw new IllegalArgumentException(
                        "Enclosing <osm> element not found");
            }
            try {
                p.parseSAX(attributes);
            }
            catch (ParseException ex) {
                ex.printStackTrace();
                throw new SAXParseException("Could not parse " + localName,
                        getLocator());
            }
            stack.push(p);
        }

    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        if ("node".equals(localName) || "way".equals(localName)
                || "relation".equals(localName)
                || "changeset".equals(localName)) {
            OSMPrimitive p = stack.pop();
            p.setLineNumberEnd(locator.getLineNumber());
            logger.finest("Parsed " + p);
            boolean process = false;
            long line = locator.getLineNumber();
            if (line < startAt) {
                // User asked to skip this.
                process = false;
            }
            else if (p instanceof OSMChangeSet) {
                process = processChangeSets;
            }
            else if (p instanceof OSMNode) {
                process = processNodes;
            }
            else if (p instanceof OSMWay) {
                process = processWays;
            }
            else if (p instanceof OSMRelation) {
                process = processRelations;
            }
            if (process) {
                // queue
                synchronized (queue) {
                    queue.add(p);
                    queue.notify();
                }
                if (throttle > 0L) {
                    synchronized (queue) {
                        if (queue.size() < QUEUE_LIMIT) {
                            throttle = 0;
                        }
                    }
                    // Avoid using too much memory.
                    try {
                        Thread.sleep(throttle);
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    // Ensure the queue doesn't grow too fast.
                    synchronized (queue) {
                        if (queue.size() > QUEUE_LIMIT) {
                            throttle = 200;
                        }
                    }
                }
            }
            else {
                // Processing was set off.
                Counter.of(p, ":ignored").one();
            }
        }
        else if ("tag".equals(localName)) {
            // nothing to do here.
        }
        else if ("bound".equals(localName)) {
            // nothing to do.
        }
        else if ("osm".equals(localName)) {
            // From now on, we are no longer inside the <osm> element.
            osmFound = false;
            logger.finer("End of OSM data: </osm>");
        }
        else {
            logger.finest("Ignored </" + localName + ">");
        }
    }

    @Override
    public void endDocument() throws SAXException {
        logger.finer("End of XML document.");
    }

    public void setLocator(Locator locator) {
        this.locator = locator;
    }

    private Locator getLocator() {
        return locator;
    }

    public void setProcessChangeSets(boolean flag) {
        processChangeSets = flag;
    }

    public void setProcessNodes(boolean flag) {
        processNodes = flag;
    }

    public void setProcessWays(boolean flag) {
        processWays = flag;
    }

    public void setProcessRelation(boolean flag) {
        processRelations = flag;
    }

}
