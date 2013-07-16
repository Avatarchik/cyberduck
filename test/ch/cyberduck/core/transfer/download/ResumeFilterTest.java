package ch.cyberduck.core.transfer.download;

import ch.cyberduck.core.AbstractTestCase;
import ch.cyberduck.core.Attributes;
import ch.cyberduck.core.NullAttributes;
import ch.cyberduck.core.NullLocal;
import ch.cyberduck.core.NullPath;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.local.Local;
import ch.cyberduck.core.local.WorkspaceApplicationLauncher;
import ch.cyberduck.core.transfer.TransferStatus;
import ch.cyberduck.core.transfer.symlink.NullSymlinkResolver;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @version $Id: ResumeFilterTest.java 10535 2012-10-22 13:42:25Z dkocher $
 */
public class ResumeFilterTest extends AbstractTestCase {

    @BeforeClass
    public static void register() {
        WorkspaceApplicationLauncher.register();
    }

    @Test
    public void testAcceptExistsTrue() throws Exception {
        ResumeFilter f = new ResumeFilter(new NullSymlinkResolver());
        Path p = new NullPath("a", Path.DIRECTORY_TYPE) {
            @Override
            public Local getLocal() {
                return new NullLocal(null, "a") {
                    @Override
                    public boolean exists() {
                        return true;
                    }
                };
            }
        };
        p.attributes().setSize(2L);
        assertFalse(f.accept(p));
    }

    @Test
    public void testAcceptExistsFalse() throws Exception {
        ResumeFilter f = new ResumeFilter(new NullSymlinkResolver());
        Path p = new NullPath("a", Path.FILE_TYPE) {
            @Override
            public Local getLocal() {
                return new NullLocal("~/Downloads", "a") {
                    @Override
                    public boolean exists() {
                        return false;
                    }
                };
            }
        };
        p.attributes().setSize(2L);
        assertTrue(f.accept(p));
    }

    @Test
    public void testPrepareFile() throws Exception {
        ResumeFilter f = new ResumeFilter(new NullSymlinkResolver());
        Path p = new NullPath("a", Path.FILE_TYPE) {
            @Override
            public Local getLocal() {
                return new NullLocal("~/Downloads", "a") {
                    @Override
                    public Attributes attributes() {
                        return new NullAttributes() {
                            @Override
                            public int getType() {
                                return Path.FILE_TYPE;
                            }

                            @Override
                            public long getSize() {
                                return 1L;
                            }
                        };
                    }

                    @Override
                    public boolean exists() {
                        return true;
                    }
                };
            }
        };
        p.attributes().setSize(2L);
        final TransferStatus status = f.prepare(p);
        assertTrue(status.isResume());
        assertEquals(1L, status.getCurrent(), 0L);
    }

    @Test
    public void testPrepareDirectory() throws Exception {
        ResumeFilter f = new ResumeFilter(new NullSymlinkResolver());
        Path p = new NullPath("a", Path.DIRECTORY_TYPE);
        p.setLocal(new NullLocal(null, "a"));
        final TransferStatus status = f.prepare(p);
        assertFalse(status.isResume());
    }
}
