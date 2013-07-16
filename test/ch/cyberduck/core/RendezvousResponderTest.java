package ch.cyberduck.core;

import ch.cyberduck.ui.cocoa.UserDefaultsDateFormatter;

import org.junit.BeforeClass;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @version $Id: RendezvousResponderTest.java 10546 2012-10-22 16:12:06Z dkocher $
 */
public class RendezvousResponderTest extends AbstractTestCase {

    @BeforeClass
    public static void register() {
        UserDefaultsDateFormatter.register();
        RendezvousResponder.register();
    }

    @Test
    public void testInit() throws Exception {
        Rendezvous r = RendezvousFactory.instance();
        final CountDownLatch wait = new CountDownLatch(1);
        final AssertionError[] failure = new AssertionError[1];
        r.addListener(new RendezvousListener() {
            @Override
            public void serviceResolved(final String identifier, final Host host) {
                try {
                    try {
                        assertNotNull(host);
                        assertEquals(String.format("%s.", InetAddress.getLocalHost().getHostName()), host.getHostname());
                    }
                    catch(UnknownHostException e) {
                        fail();
                    }
                }
                catch(AssertionError error) {
                    failure[0] = error;
                }
                finally {
                    wait.countDown();
                }
            }

            @Override
            public void serviceLost(final String servicename) {
                //
            }
        });
        r.init();
        wait.await(5L, TimeUnit.SECONDS);
        if(failure[0] != null) {
            fail(failure[0].getMessage());
        }
        r.quit();
    }
}