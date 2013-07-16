package ch.cyberduck.core;

import ch.cyberduck.ui.cocoa.foundation.NSString;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

/**
 * @version $Id: AttributedListTest.java 10977 2013-05-02 08:38:48Z dkocher $
 */
public class AttributedListTest extends AbstractTestCase {

    @Test
    public void testAdd() throws Exception {
        AttributedList<Path> list = new AttributedList<Path>();
        assertTrue(list.add(new NullPath("/a", Path.DIRECTORY_TYPE)));
        assertTrue(list.contains(new NSObjectPathReference(NSString.stringWithString("/a"))));
    }

    @Test
    public void testFilter() throws Exception {
        AttributedList<Path> list = new AttributedList<Path>();
        final NullPath a = new NullPath("/a", Path.DIRECTORY_TYPE);
        assertTrue(list.add(a));
        assertTrue(list.filter(new NullComparator(), new PathFilter() {
            @Override
            public boolean accept(final AbstractPath file) {
                return !file.getName().equals("a");
            }
        }).isEmpty());
        assertEquals(Collections.<Path>singletonList(a), list.attributes().getHidden());
        assertFalse(list.filter(new NullComparator(), new PathFilter() {
            @Override
            public boolean accept(final AbstractPath file) {
                return !file.getName().equals("b");
            }
        }).isEmpty());
        assertEquals(Collections.emptyList(), list.attributes().getHidden());
    }
}
