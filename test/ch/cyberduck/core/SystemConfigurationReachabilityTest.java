package ch.cyberduck.core;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @version $Id: SystemConfigurationReachabilityTest.java 10225 2012-10-15 17:25:33Z dkocher $
 */
public class SystemConfigurationReachabilityTest extends AbstractTestCase {

    @BeforeClass
    public static void register() {
        SystemConfigurationReachability.register();
    }

    @Test
    public void testIsReachable() throws Exception {
        assertTrue(ReachabilityFactory.get().isReachable(
                new Host("cyberduck.ch", 80)
        ));
        assertTrue(ReachabilityFactory.get().isReachable(
                new Host("cyberduck.ch", 22)
        ));
        assertFalse(ReachabilityFactory.get().isReachable(
                new Host("a.cyberduck.ch", 22)
        ));
    }
}
