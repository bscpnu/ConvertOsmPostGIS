package conv.osm.postgis;

import jargs.gnu.CmdLineParser;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import conv.osm.postgis.core.FeatureRecognizer;
import conv.osm.postgis.core.Monitor;
import conv.osm.postgis.core.OSMHandler;
import conv.osm.postgis.core.OSMParser;
import conv.osm.postgis.core.PostGISBuilder;
import conv.osm.postgis.dao.DAOFactory;
import conv.osm.postgis.dao.OutputTarget;
import conv.osm.postgis.dao.PostGISDAOFactory;

public class Main
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    
    private static CmdLineParser.Option databaseOpt;
    private static CmdLineParser.Option usernameOpt;
    private static CmdLineParser.Option passwordOpt;
    private static CmdLineParser.Option startOpt;
    private static CmdLineParser.Option featuresOpt;
    private static CmdLineParser.Option processOpt;

    
    public static void main(String[] args) {
        initLogger();
        CmdLineParser parser = parseCmdLine(args);

        Monitor monitor = new Monitor();

        DAOFactory.setDefaultDAOFactory(PostGISDAOFactory.getInstance());

        String featSpecUrl = (String) parser.getOptionValue(featuresOpt,
                "features.json");
        logger.config("Feature specification: " + featSpecUrl);

        FeatureRecognizer featRec = null;
        try {
            featRec = new FeatureRecognizer(featSpecUrl);
        }
        catch (Exception ex) {
            logger.log(Level.SEVERE,
                    "Feature specification parsing failed.", ex);
            ex.printStackTrace();
            System.exit(3);
        }

        long startLine = (Long) parser.getOptionValue(startOpt, 0L);
        String process = (String) parser.getOptionValue(processOpt,
                "changesets,nodes,ways,relations,features,topology");

        boolean processChangeSets = true;
        boolean processNodes = true;
        boolean processWays = true;
        boolean processRelations = true;
        boolean processTopology = true;
        boolean processFeatures = true;
        boolean processBounds = true;


        boolean expectFiles = processChangeSets || processNodes
                || processWays || processRelations;

        OSMParser osmParser = null;
        ArrayList<File> files = null;
        if (expectFiles) {
            try {
                files = checkFiles(parser.getRemainingArgs());
            }
            catch (FileNotFoundException fnfe) {
                fnfe.printStackTrace();
                System.exit(2);
            }

            osmParser = new OSMParser(monitor);

            logger.config("Processing: " + process);
            logger.config("Starting at line: " + startLine);

            OSMHandler handler = osmParser.getHandler();
            handler.setStartAt(startLine);
            handler.setProcessChangeSets(processChangeSets);
            handler.setProcessNodes(processNodes);
            handler.setProcessWays(processWays);
            handler.setProcessRelation(processRelations);
        }
        String dbURL = (String) parser.getOptionValue(databaseOpt,
                "localhost/routing");
        String dbUserName = (String) parser.getOptionValue(usernameOpt,
                System.getProperty("user.name"));
        String dbPassword = (String) parser.getOptionValue(passwordOpt);
        logger.config("Database: " + dbURL);
        logger.config("User name: " + dbUserName);
        OutputTarget output = null;
        try {
            output = new OutputTarget(dbURL, dbUserName, dbPassword);
            output.createModel();

            PostGISBuilder builder = new PostGISBuilder(output);
            builder.setFeatures(featRec);
            builder.setProcessTopology(processTopology);
            builder.setProcessFeatures(processFeatures);
            builder.setProcessBounds(processBounds);

            monitor.createWriter(builder);
        }
        catch (SQLException se) {
            logger.log(Level.SEVERE, "Database connection failed.", se);
            se.printStackTrace();
            System.exit(4);
        }

        new Thread(monitor.getWriter(), "Writer").start();

        if (expectFiles) {
            osmParser.setFiles(files);
            new Thread(osmParser, "Parser").start();
        }
        else {
            monitor.getWriter().inputDone();
        }
        monitor.run();
        System.exit(monitor.getStatus());
    }

    
     // Tries to make sure that the logger is properly configured.
    
    private static void initLogger() {
        String logConfFileName = System.getProperty("java.util.logging.config.file");
        System.out.println(logConfFileName);
        if (null == logConfFileName) {
            System.err.println("Please set java "
                    + "-Djava.util.logging.config.file=logging.properties");
            System.exit(1);
        }
        else {
            if (!new File(logConfFileName).isFile()) {
                System.err.println("The supposed logging configuration file \""
                        + logConfFileName + "\" is not a real file.");
                System.exit(1);
            }
            if (null == logger.getLevel()) {
                System.err.println("Please set osmtopostgis.level in "
                        + logConfFileName + ". If it's already set,"
                        + " try using an absolute path"
                        + " for the file location.");
                System.exit(1);
            }
            System.out.println("Log level: " + logger.getLevel());
            logger.config("Logging configuration: " + logConfFileName);
        }
    }

    private static CmdLineParser parseCmdLine(String[] args) {

        CmdLineParser parser = new CmdLineParser();
        databaseOpt = parser.addStringOption('d', "database");
        usernameOpt = parser.addStringOption('u', "username");
        passwordOpt = parser.addStringOption('W', "password");
        startOpt = parser.addLongOption('s', "start");
        featuresOpt = parser.addStringOption('f', "features");
        processOpt = parser.addStringOption('p', "process");

        try {
            parser.parse(args);
        }
        catch (CmdLineParser.OptionException e) {
            System.err.println(e.getMessage());
            logger.log(Level.SEVERE, "Command line parsing failed.", e);
            //printUsage();
            System.exit(2);
        }
        return parser;
    }

    
    private static ArrayList<File> checkFiles(String[] args)
            throws FileNotFoundException {
        ArrayList<File> files = new ArrayList<File>(args.length);
        for (String name : args) {
            File f = new File(name);
            if (f.exists()) {
                if (f.isFile()) {
                    files.add(f);
                }
                else {
                    logger.severe("Not a normal file: \"" + name + "\"");
                    throw new FileNotFoundException(name);
                }
            }
            else {
                throw new FileNotFoundException(name);
            }
        }
        return files;
    }
}
