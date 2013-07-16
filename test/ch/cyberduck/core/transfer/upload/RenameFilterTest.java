package ch.cyberduck.core.transfer.upload;

import ch.cyberduck.core.AbstractTestCase;
import ch.cyberduck.core.NullLocal;
import ch.cyberduck.core.NullPath;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.transfer.symlink.NullSymlinkResolver;

import org.junit.Test;

import static org.junit.Assert.assertNotSame;

/**
 * @version $Id: RenameFilterTest.java 10452 2012-10-18 18:40:07Z dkocher $
 */
public class RenameFilterTest extends AbstractTestCase {

    @Test
    public void testPrepare() throws Exception {
        RenameFilter f = new RenameFilter(new NullSymlinkResolver());
        final NullPath t = new NullPath("t", Path.FILE_TYPE) {
            @Override
            public boolean exists() {
                return this.getName().equals("t");
            }
        };
        t.setLocal(new NullLocal(null, "t"));
        f.prepare(t);
        assertNotSame("t", t.getName());
    }
}