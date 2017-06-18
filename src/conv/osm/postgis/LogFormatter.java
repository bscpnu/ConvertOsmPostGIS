package conv.osm.postgis;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter
{
    private static final String format = "{0,time,HH:mm}";

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
        s = (null == s) ? record.getLoggerName() : s;
        int i = s.length();
        i = (i > 24) ? i - 24 : 0;
        sb.append(s.substring(i));
        s = record.getSourceMethodName();
        if (null != s) sb.append(' ').append(s);
        sb.append(' ');
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
