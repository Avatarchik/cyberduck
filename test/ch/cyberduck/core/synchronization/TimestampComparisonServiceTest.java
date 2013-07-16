package ch.cyberduck.core.synchronization;

import ch.cyberduck.core.AbstractTestCase;
import ch.cyberduck.core.Attributes;
import ch.cyberduck.core.NullAttributes;
import ch.cyberduck.core.NullLocal;
import ch.cyberduck.core.NullPath;
import ch.cyberduck.core.NullPathAttributes;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathAttributes;
import ch.cyberduck.core.local.Local;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

/**
 * @version $Id: TimestampComparisonServiceTest.java 10269 2012-10-15 19:36:57Z dkocher $
 */
public class TimestampComparisonServiceTest extends AbstractTestCase {

    @Test
    public void testCompare() throws Exception {
        ComparisonService s = new TimestampComparisonService();
        final long timestmap = Calendar.getInstance().getTimeInMillis();
        assertEquals(Comparison.EQUAL, s.compare(new NullPath("t", Path.FILE_TYPE) {
            @Override
            public Local getLocal() {
                return new NullLocal(null, "t") {
                    @Override
                    public Attributes attributes() {
                        return new NullAttributes() {
                            @Override
                            public long getModificationDate() {
                                return timestmap;
                            }
                        };
                    }
                };
            }

            @Override
            public PathAttributes attributes() {
                return new NullPathAttributes() {
                    @Override
                    public long getModificationDate() {
                        return timestmap;
                    }
                };
            }
        }));
        assertEquals(Comparison.LOCAL_NEWER, s.compare(new NullPath("t", Path.FILE_TYPE) {
            @Override
            public Local getLocal() {
                return new NullLocal(null, "t") {
                    @Override
                    public Attributes attributes() {
                        return new NullAttributes() {
                            @Override
                            public long getModificationDate() {
                                return Calendar.getInstance().getTimeInMillis();
                            }
                        };
                    }
                };
            }

            @Override
            public PathAttributes attributes() {
                return new NullPathAttributes() {
                    @Override
                    public long getModificationDate() {
                        final Calendar c = Calendar.getInstance(TimeZone.getDefault());
                        c.set(Calendar.HOUR_OF_DAY, 0);
                        return c.getTimeInMillis();
                    }
                };
            }
        }));
    }
}
