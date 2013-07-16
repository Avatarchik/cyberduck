package ch.cyberduck.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @version $Id: ArchiveTest.java 10670 2012-12-19 10:59:26Z dkocher $
 */
public class ArchiveTest {

    @Test
    public void testForName() throws Exception {
        assertEquals(Archive.TAR, Archive.forName("tar"));
        assertEquals(Archive.TARGZ, Archive.forName("tar.gz"));
        assertEquals(Archive.ZIP, Archive.forName("zip"));
    }

    @Test
    public void testEscape() throws Exception {
        Archive a = Archive.TAR;
        assertEquals("file\\ name", a.escape("file name"));
        assertEquals("file\\(name", a.escape("file(name"));
        assertEquals("\\$filename", a.escape("$filename"));
    }
}