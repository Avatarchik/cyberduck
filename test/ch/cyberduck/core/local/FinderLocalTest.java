package ch.cyberduck.core.local;

import ch.cyberduck.core.AbstractTestCase;
import ch.cyberduck.core.Permission;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.Callable;

import static org.junit.Assert.*;

/**
 * @version $Id: FinderLocalTest.java 10387 2012-10-18 08:13:09Z dkocher $
 */
public class FinderLocalTest extends AbstractTestCase {

    @BeforeClass
    public static void register() {
        FinderLocal.register();
    }

    @Test
    public void testEqual() throws Exception {
        final String name = UUID.randomUUID().toString();
        Local l = new FinderLocal(System.getProperty("java.io.tmpdir"), name);
        assertEquals(new FinderLocal(System.getProperty("java.io.tmpdir"), name), l);
        l.touch();
        assertEquals(new FinderLocal(System.getProperty("java.io.tmpdir"), name), l);
        final FinderLocal other = new FinderLocal(System.getProperty("java.io.tmpdir"), name + "-");
        assertNotSame(other, l);
        other.touch();
        assertNotSame(other, l);
    }

    @Test
    public void testTilde() throws Exception {
        assertEquals(System.getProperty("user.home") + "/f", new FinderLocal("~/f").getAbsolute());
        assertEquals("~/f", new FinderLocal("~/f").getAbbreviatedPath());
    }

    @Test
    public void testDisplayName() throws Exception {
        assertEquals("f/a", new FinderLocal(System.getProperty("java.io.tmpdir"), "f:a").getDisplayName());
    }

    @Test
    public void testTrash() throws Exception {
        Local l = new FinderLocal(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
        l.touch();
        assertTrue(l.exists());
        l.trash();
        assertFalse(l.exists());
    }

    @Test
    public void testTrashRepeated() throws Exception {
        this.repeat(new Callable<Local>() {
            @Override
            public Local call() throws Exception {
                Local l = new FinderLocal(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
                l.touch();
                assertTrue(l.exists());
                l.trash();
                assertFalse(l.exists());
                return l;
            }
        }, 10);
    }

    @Test
    public void testWriteUnixPermission() throws Exception {
        this.repeat(new Callable<Local>() {
            @Override
            public Local call() throws Exception {
                Local l = new FinderLocal(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
                l.touch();
                final Permission permission = new Permission(644);
                l.writeUnixPermission(permission);
                assertEquals(permission, l.attributes().getPermission());
                l.delete();
                return l;
            }
        }, 10);
    }

    @Test
    public void testTouch() {
        Local l = new FinderLocal(System.getProperty("java.io.tmpdir") + "/p/", UUID.randomUUID().toString());
        l.touch();
        assertTrue(l.exists());
        l.touch();
        assertTrue(l.exists());
        l.delete();
    }

    @Test
    public void testMkdir() {
        Local l = new FinderLocal(System.getProperty("java.io.tmpdir") + "/p/", UUID.randomUUID().toString());
        l.mkdir();
        assertTrue(l.exists());
        l.mkdir();
        assertTrue(l.exists());
        l.delete();
    }
}
