package conv.osm.postgis.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.NoSuchElementException;

public abstract class IterableResultSet<T> implements Iterable<T>,
        Iterator<T>
{
    ResultSet rs;
    Boolean next = null;

    public IterableResultSet(ResultSet rs) {
        this.rs = rs;
    }

    @Override
    public Iterator<T> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        if (null == next) {
            try {
                next = rs.next();
            }
            catch (SQLException e) {
                close();
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
        if (!next) close();
        return next;
    }

    protected ResultSet advance() {
        if (hasNext()) {
            next = null;
            return rs;
        }
        throw new NoSuchElementException();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    public void close() {
        next = false;
        if (null != rs) {
            try {
                rs.close();
            }
            catch (SQLException e) {
            }
            rs = null;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }
}
