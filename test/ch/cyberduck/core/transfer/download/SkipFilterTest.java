package ch.cyberduck.core.transfer.download;

import ch.cyberduck.core.AbstractTestCase;
import ch.cyberduck.core.NullLocal;
import ch.cyberduck.core.NullPath;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.local.Local;
import ch.cyberduck.core.local.WorkspaceApplicationLauncher;
import ch.cyberduck.core.transfer.symlink.NullSymlinkResolver;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @version $Id: SkipFilterTest.java 10535 2012-10-22 13:42:25Z dkocher $
 */
public class SkipFilterTest extends AbstractTestCase {

    @BeforeClass
    public static void register() {
        WorkspaceApplicationLauncher.register();
    }

    @Test
    public void testAccept() throws Exception {
        SkipFilter f = new SkipFilter(new NullSymlinkResolver());
        assertTrue(f.accept(new NullPath("a", Path.FILE_TYPE) {
            @Override
            public Local getLocal() {
                return new NullLocal("a", "b") {
                    @Override
                    public boolean exists() {
                        return false;
                    }
                };
            }
        }));
        assertFalse(f.accept(new NullPath("a", Path.FILE_TYPE) {
            @Override
            public Local getLocal() {
                return new NullLocal("a", "b") {
                    @Override
                    public boolean exists() {
                        return true;
                    }
                };
            }
        }));
    }
}
