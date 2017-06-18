package conv.osm.postgis.dao;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import conv.osm.postgis.model.OSMPrimitive;

public final class Counter
{
    private static final Map<String, Counter> counterMap = new ConcurrentSkipListMap<String, Counter>();

    public static final Counter init(String counterName, long count) {
        Counter counter;
        synchronized (counterMap) {
            counter = counterMap.get(counterName);
            if (null == counter) {
                counter = new Counter(count);
                counterMap.put(counterName, counter);
            }
            else {
                throw new IllegalStateException("Counter \"" + counterName
                        + "\" already exists.");
            }
        }
        return counter;
    }

    protected static final void countInsert(DAO dao) {
        of(dao.getFullName() + ":created").one();
    }

    protected static final void countUpdate(DAO dao) {
        of(dao.getFullName() + ":updated").one();
    }

    public static final void countVerify(DAO dao) {
        of(dao.getFullName() + ":verified").one();
    }

    public static final void countIgnore(DAO dao) {
        of(dao.getFullName() + ":ignored").one();
    }

    public static long count(String counterName, long change) {
        synchronized (counterMap) {
            return counterMap.get(counterName).count(change);
        }
    }

    public static final Counter of(String counterName) {
        Counter counter;
        synchronized (counterMap) {
            counter = counterMap.get(counterName);
            if (null == counter) {
                counter = init(counterName, 0);
            }
        }
        return counter;
    }

    public static final Counter of(OSMPrimitive p, String suffix) {
        return of(DAOFactory.getDefaultDAOFactory().getFullName(
                p.getClass())
                + suffix);
    }

    private long count;

    private Counter(long count) {
        this.count = count;
    }

    public synchronized long count(long change) {
        return count += change;
    }

    /**
     * Increments the counter value by one.
     */
    public synchronized void one() {
        count++;
    }

    public synchronized long getCount() {
        return count;
    }

    public static long getTotal(String prefix) {
        long total = 0;
        synchronized (counterMap) {
            for (Map.Entry<String, Counter> entry : counterMap.entrySet()) {
                String name = entry.getKey();
                if (name.startsWith(prefix)) {
                    Counter counter = entry.getValue();
                    total += counter.getCount();
                }
            }
        }
        return total;
    }

    public static long getTotal() {
        long total = 0;
        synchronized (counterMap) {
            for (Counter counter : counterMap.values()) {
                total += counter.getCount();
            }
        }
        return total;
    }

    public static final void report(StringBuilder sb, String prefix,
            String separator) {
        synchronized (counterMap) {
            if (counterMap.isEmpty()) {
                sb.append(prefix + "nothing.");
            }
            else {
                for (Map.Entry<String, Counter> entry : counterMap.entrySet()) {
                    String name = entry.getKey();
                    Counter counter = entry.getValue();
                    sb.append(prefix + name + separator
                            + counter.getCount());
                }
            }
        }
    }

}
