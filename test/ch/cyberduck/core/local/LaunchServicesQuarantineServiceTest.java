package ch.cyberduck.core.local;

import ch.cyberduck.core.AbstractTestCase;
import ch.cyberduck.core.NullLocal;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * @version $Id: LaunchServicesQuarantineServiceTest.java 10224 2012-10-15 17:25:15Z dkocher $
 */
public class LaunchServicesQuarantineServiceTest extends AbstractTestCase {

    @BeforeClass
    public static void register() {
        LaunchServicesQuarantineService.register();
    }

    @Test
    public void testSetQuarantine() throws Exception {
        final QuarantineService q = QuarantineServiceFactory.get();
        Callable<Local> c = new Callable<Local>() {
            @Override
            public Local call() throws Exception {
                final NullLocal l = new NullLocal(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
                l.touch();
                q.setQuarantine(l,
                        "http://cyberduck.ch", "http://cyberduck.ch");
                l.delete();
                return l;
            }
        };
        this.repeat(c, 20);
    }

    @Test
    public void testSetWhereFrom() throws Exception {
        final QuarantineService q = QuarantineServiceFactory.get();
        Callable<Local> c = new Callable<Local>() {
            @Override
            public Local call() throws Exception {
                final NullLocal l = new NullLocal(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
                l.touch();
                q.setWhereFrom(l,
                        "http://cyberduck.ch");
                l.delete();
                return l;
            }
        };
        this.repeat(c, 20);
    }
}