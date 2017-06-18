package conv.osm.postgis.core;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import conv.osm.postgis.Util;

public class OSMParser implements Runnable
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    private Monitor monitor;
    private XMLReader xr;

    private ArrayList<File> files;

    public void setFiles(ArrayList<File> files) {
        this.files = files;
    }

    private OSMHandler osmHandler = null;

    public OSMParser(Monitor monitor) {
        this.monitor = monitor;
    }

    public OSMHandler getHandler() {
        if (null == osmHandler) {
            osmHandler = new OSMHandler();
            osmHandler.setQueue(monitor.getQueue());
        }
        return osmHandler;
    }

    public void parse(ArrayList<File> files) throws IOException,
            SAXException {
        long mark = System.currentTimeMillis();
        logger.info("Parsing started.");

        xr = XMLReaderFactory.createXMLReader();
        xr.setContentHandler(osmHandler);
        xr.setErrorHandler(osmHandler);

        long totalBytes = 0L;
        for (File f : files) {
            long bytes = f.length();
            System.out.println("Input file: \"" + f.getPath() + "\" ("
                    + bytes + " B)");
            totalBytes += bytes;
        }
        System.out.println("Input total: " + files.size() + " file(s) ("
                + (totalBytes) + " B)");

        for (File f : files) {
            System.out.println("Parsing file: " + f);
            parseFile(f);
            System.out.println("Done file: " + f);
        }
        long duration = System.currentTimeMillis() - mark;
        logger.info("Parsing ended.");
        System.out.println("Parsed " + totalBytes + " B in "
                + Util.dhms(duration) + ".");
    }

    private void parseFile(File f) throws IOException, SAXException {
        FileReader r = new FileReader(f);
        xr.parse(new InputSource(r));
    }

    @Override
    public void run() {
        if (null == files) {
            // Nothing to parse
        }
        else {
            try {
                parse(files);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "OSM parsing failed.", e);
                e.printStackTrace();
                System.exit(5);
            }
        }
        monitor.getWriter().inputDone();
    }
}
