package ch.cyberduck.core.formatter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @version $Id: BinarySizeFormatterTest.java 10984 2013-05-02 10:33:23Z dkocher $
 */
public class BinarySizeFormatterTest {

    @Test
    public void testFormat() throws Exception {
        ch.cyberduck.core.formatter.BinarySizeFormatter f = new ch.cyberduck.core.formatter.BinarySizeFormatter();
        assertEquals("1.0 KB", f.format(1024));
        assertEquals("1.5 KB", f.format(1500));
        assertEquals("1.4 KB", f.format(1480));
        assertEquals("2.0 KB", f.format(2000));
        assertEquals("1.0 MB", f.format(1048576));
        assertEquals("1.0 GB", f.format(1073741824));
        assertEquals("375.3 MB", f.format(393495974));
    }
}
