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

import static org.junit.Assert.assertEquals;

/**
 * @version $Id: ChecksumComparisonServiceTest.java 10147 2012-10-15 10:31:19Z dkocher $
 */
public class ChecksumComparisonServiceTest extends AbstractTestCase {

    @Test
    public void testCompare() throws Exception {
        ComparisonService s = new ChecksumComparisonService();
        assertEquals(Comparison.EQUAL, s.compare(new NullPath("t", Path.FILE_TYPE) {
            @Override
            public Local getLocal() {
                return new NullLocal(null, "t") {
                    @Override
                    public Attributes attributes() {
                        return new NullAttributes() {
                            @Override
                            public String getChecksum() {
                                return "a";
                            }
                        };
                    }
                };
            }

            @Override
            public PathAttributes attributes() {
                return new NullPathAttributes() {
                    @Override
                    public String getChecksum() {
                        return "a";
                    }
                };
            }
        }));
        assertEquals(Comparison.UNEQUAL, s.compare(new NullPath("t", Path.FILE_TYPE) {
            @Override
            public Local getLocal() {
                return new NullLocal(null, "t") {
                    @Override
                    public Attributes attributes() {
                        return new NullAttributes() {
                            @Override
                            public String getChecksum() {
                                return "a";
                            }
                        };
                    }
                };
            }

            @Override
            public PathAttributes attributes() {
                return new NullPathAttributes() {
                    @Override
                    public String getChecksum() {
                        return "b";
                    }
                };
            }
        }));
    }
}
