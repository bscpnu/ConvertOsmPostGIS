package conv.osm.postgis.core;

import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Logger;

import conv.osm.postgis.Util;
import conv.osm.postgis.dao.Counter;
import conv.osm.postgis.dao.OutputTarget;
import conv.osm.postgis.model.OSMPrimitive;

public class Monitor implements Runnable
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    private int status = 0;

    public void setStatus(int code) {
        status = code;
    }

    public int getStatus() {
        return status;
    }

    
    private static final QueueWrapper queue = new QueueWrapper(
            new LinkedList<OSMPrimitive>());

    static class QueueWrapper
    {
        private Queue<OSMPrimitive> queue;

        public QueueWrapper(Queue<OSMPrimitive> queue) {
            this.queue = queue;
        }

        public synchronized boolean add(OSMPrimitive e) {
            return queue.add(e);
        }

        public synchronized OSMPrimitive poll() {
            return queue.poll();
        }

        public synchronized int size() {
            return queue.size();
        }
    }

    private static long monitorCycleMillis = 15000L;

    private OutputTarget output = null;
    private PostGISWriter writer = null;
    private boolean running = true;

    QueueWrapper getQueue() {
        return queue;
    }

    public void createWriter(PostGISBuilder builder) {
        output = builder.getOutputTarget();
        writer = new PostGISWriter(queue, output);
        writer.setBuilder(builder);
        writer.setMonitor(this);
    }

    public PostGISWriter getWriter() {
        return writer;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("Monitor");
        long lastImported = 0L;
        long lastLine = 0L;
        long lastMark = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder(256);
        while (running) {
            long mark = System.currentTimeMillis();
            long cycle = mark - lastMark;
            lastMark = mark;
            int queueSize = queue.size();

            long memFree = Runtime.getRuntime().freeMemory();
            long memTotal = Runtime.getRuntime().totalMemory();
            long memUsed = memTotal - memFree;
            long committedLine = writer.getLastLineNumber();
            sb.setLength(0);
            sb.append("Time elapsed " + Util.dhms(writer.getDuration()));
            if (committedLine > -1) {
                sb.append("; Committed up to line " + committedLine);
            }
            long totalImported = Counter.getTotal();
            long throughputEntities = totalImported - lastImported;
            long throughputLines = committedLine - lastLine;

            // Only report some statistics from normal cycles.
            if (cycle > 50L) {
                sb.append("; Throughput/s "
                        + Util.oneDecimal((throughputLines / (cycle / 1000f)))
                        + " lines = "
                        + Util.oneDecimal((throughputEntities / (cycle / 1000f)))
                        + " entities");
            }
            sb.append(".");
            logger.info(sb.toString());

            sb.setLength(0);
            sb.append("Cumulative:");
            Counter.report(sb, " ", "=");
            logger.info(sb.toString());
            lastImported = totalImported;
            lastLine = committedLine;
            if (0 == throughputEntities) {
                /*
                 * When skipping millions of lines of XML, it may take a
                 * relatively long time before the database connection is
                 * actually used. This should keep the connection alive
                 * while throughput is zero.
                 */
                if (output.keepAlive()) {
                    // Good.
                }
                else {
                    throw new IllegalStateException(
                            "Database connection died.");
                }
            }

            sb.setLength(0);
            sb.append("JVM " + Util.megabytes(memFree) + "/"
                    + Util.megabytes(memTotal) + " MiB");
            sb.append(" (" + Util.oneDecimal((memUsed * 100f) / memTotal)
                    + " % used)");
            if (queueSize > 0) {
                sb.append("; In-memory queue " + queueSize);
            }
            sb.append('.');
            logger.config(sb.toString());

            try {
                synchronized (this) {
                    wait(monitorCycleMillis);
                }
            }
            catch (InterruptedException e) {
            }
        }
    }

    public void stop() {
        synchronized (this) {
            running = false;
            notifyAll();
        }
    }
}
