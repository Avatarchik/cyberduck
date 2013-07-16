package ch.cyberduck.core.filter;

import ch.cyberduck.core.AbstractTestCase;
import ch.cyberduck.core.NullPath;
import ch.cyberduck.core.Path;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @version $Id: DownloadRegexFilterTest.java 10251 2012-10-15 18:36:13Z dkocher $
 */
public class DownloadRegexFilterTest extends AbstractTestCase {

    @Test
    public void testAccept() throws Exception {
        assertFalse(new DownloadRegexFilter().accept(new NullPath(".DS_Store", Path.FILE_TYPE)));
        assertTrue(new DownloadRegexFilter().accept(new NullPath("f", Path.FILE_TYPE)));
    }
}