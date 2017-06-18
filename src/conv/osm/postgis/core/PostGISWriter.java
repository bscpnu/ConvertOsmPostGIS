package conv.osm.postgis.core;

import java.util.logging.Level;
import java.util.logging.Logger;

import conv.osm.postgis.Util;
import conv.osm.postgis.dao.Counter;
import conv.osm.postgis.dao.OutputTarget;
import conv.osm.postgis.model.OSMPrimitive;

public class PostGISWriter implements Runnable, DataWriter
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    
    private Monitor.QueueWrapper queue;

    private Monitor monitor;

    private long lastLineNumber = -1;
    private long mark;

    long getLastLineNumber() {
        return lastLineNumber;
    }

    private PostGISBuilder builder = null;

    
    private boolean moreInput = true;

    OutputTarget output;

    public PostGISWriter(Monitor.QueueWrapper queue, OutputTarget output) {
        this.queue = queue;
        this.output = output;
    }

    public void setBuilder(PostGISBuilder builder) {
        this.builder = builder;
    }

    @Override
    public void run() {
        logger.fine("Writing started");
        mark = System.currentTimeMillis();
        try {
            OSMPrimitive data = null;
            do {
                synchronized (queue) {
                    do { 
                        if (null == data) {
                            queue.wait(2000L);
                        }
                        data = queue.poll();
                    } while ((null == data) && moreInput);
                }
                if (null == data) {
                    // All OSM primitives have been imported.
                }
                else {
                    output.retryWrite(data, 5);
                    lastLineNumber = data.getLineNumberEnd();
                }
            } while (null != data);

            builder.generate();
            builder.processBounds();
        }
        catch (Throwable e) {
            logger.log(Level.SEVERE, "Problems writing.", e);
            e.printStackTrace();
            monitor.setStatus(1);
            output = null;
        }
        finally {
            monitor.stop();
        }
        if (null != output) {
            output.logStats();
        }
        long duration = getDuration();
        logger.fine("Writing ended");

        long imported = Counter.getTotal();
        float rate = (imported / (duration / 1000f));

        String msg =
                "Wrote " + imported + " entities in " + Util.dhms(duration)
                        + " (" + Util.oneDecimal(rate) + "/s on average).";
        logger.info(msg);
        System.out.println(msg);
    }

    long getDuration() {
        return System.currentTimeMillis() - mark;
    }

    
    public void inputDone() {
        moreInput = false;
    }

    public void setMonitor(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public void setBuilder(DataBuilder builder) {
        if (builder instanceof PostGISBuilder) {
            this.builder = (PostGISBuilder) builder;
        }
        else {
            throw new UnsupportedOperationException(
                    "Unknown GISBuilder of class "
                            + builder.getClass().getName() + ".");
        }
    }
}
