package ch.cyberduck.core.local;

import ch.cyberduck.core.AbstractTestCase;
import ch.cyberduck.core.Permission;

import org.junit.Test;

import java.io.File;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @version $Id: FinderLocalAttributesTest.java 10552 2012-10-22 16:42:10Z dkocher $
 */
public class FinderLocalAttributesTest extends AbstractTestCase {

    @Test
    public void testGetSize() throws Exception {
        assertEquals(-1, new FinderLocalAttributes(UUID.randomUUID().toString()).getSize());
        final File f = new File(UUID.randomUUID().toString());
        f.createNewFile();
        FinderLocalAttributes a = new FinderLocalAttributes(f.getAbsolutePath());
        assertEquals(0, a.getSize());
        f.delete();
    }

    @Test
    public void testGetPermission() throws Exception {
        assertEquals(Permission.EMPTY, new FinderLocalAttributes(UUID.randomUUID().toString()).getPermission());
    }

    @Test
    public void testGetCreationDate() throws Exception {
        assertEquals(-1, new FinderLocalAttributes(UUID.randomUUID().toString()).getCreationDate());
        final File f = new File(UUID.randomUUID().toString());
        f.createNewFile();
        FinderLocalAttributes a = new FinderLocalAttributes(f.getAbsolutePath());
        assertTrue(a.getCreationDate() > 0);
        f.delete();
    }

    @Test
    public void testGetAccessedDate() throws Exception {
        assertEquals(-1, new FinderLocalAttributes(UUID.randomUUID().toString()).getAccessedDate());
        final File f = new File(UUID.randomUUID().toString());
        f.createNewFile();
        FinderLocalAttributes a = new FinderLocalAttributes(f.getAbsolutePath());
        assertTrue(a.getAccessedDate() > 0);
        f.delete();
    }

    @Test
    public void getGetModificationDate() throws Exception {
        assertEquals(-1, new FinderLocalAttributes(UUID.randomUUID().toString()).getModificationDate());
        final File f = new File(UUID.randomUUID().toString());
        f.createNewFile();
        FinderLocalAttributes a = new FinderLocalAttributes(f.getAbsolutePath());
        assertTrue(a.getModificationDate() > 0);
        f.delete();
    }

    @Test
    public void testGetOwner() throws Exception {
        FinderLocalAttributes a = new FinderLocalAttributes(UUID.randomUUID().toString());
        assertEquals("Unknown", a.getOwner());
    }

    @Test
    public void testGetGroup() throws Exception {
        FinderLocalAttributes a = new FinderLocalAttributes(UUID.randomUUID().toString());
        assertEquals("Unknown", a.getGroup());
    }

    @Test
    public void testGetInode() throws Exception {
        assertEquals(-1, new FinderLocalAttributes(UUID.randomUUID().toString()).getInode());
        final File f = new File(UUID.randomUUID().toString());
        f.createNewFile();
        FinderLocalAttributes a = new FinderLocalAttributes(f.getAbsolutePath());
        assertTrue(a.getInode() > 0);
        f.delete();
    }

    @Test
    public void testIsBundle() throws Exception {
        FinderLocalAttributes a = new FinderLocalAttributes(UUID.randomUUID().toString());
        assertFalse(a.isBundle());
    }

    @Test
    public void testIsSymbolicLink() throws Exception {
        FinderLocalAttributes a = new FinderLocalAttributes(UUID.randomUUID().toString());
        assertFalse(a.isSymbolicLink());
    }
}
