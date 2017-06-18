package conv.osm.postgis;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogDetailFormatter extends Formatter
{
    private static final String format =
            "{0,date,yyyy-MM-dd} {0,time,HH:mm:ss.SSS}";

    private final Date date = new Date();
    private final StringBuffer dsb = new StringBuffer();
    private final StringBuilder sb = new StringBuilder();
    private final MessageFormat formatter = new MessageFormat(format);

    private final Object args[] = { date };

    public synchronized String format(LogRecord record) {
        sb.setLength(0);
        date.setTime(record.getMillis());
        dsb.setLength(0);
        formatter.format(args, dsb, null);
        sb.append(dsb);
        sb.append(' ');
        String s;
        s = record.getSourceClassName();
        sb.append(null == s ? record.getLoggerName() : s);
        s = record.getSourceMethodName();
        if (null != s) sb.append(' ').append(s);
        sb.append('\n');
        String message = formatMessage(record);
        sb.append(record.getLevel().getLocalizedName());
        sb.append(": ").append(message).append('\n');
        if (null != record.getThrown()) {
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                record.getThrown().printStackTrace(pw);
                pw.close();
                sb.append(sw.toString());
            }
            catch (Exception ex) {
            }
        }
        return sb.toString();
    }
}
