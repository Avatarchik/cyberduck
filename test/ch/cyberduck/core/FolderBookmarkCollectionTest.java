package ch.cyberduck.core;

import ch.cyberduck.core.local.Local;
import ch.cyberduck.core.serializer.HostWriterFactory;
import ch.cyberduck.core.serializer.Writer;

import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.*;

/**
 * @version $Id: FolderBookmarkCollectionTest.java 10544 2012-10-22 15:31:43Z dkocher $
 */
public class FolderBookmarkCollectionTest extends AbstractTestCase {

    @Test
    public void testIndex() {
        HostWriterFactory.addFactory(Factory.NATIVE_PLATFORM, new HostWriterFactory() {
            @Override
            protected Writer<Host> create() {
                return new Writer<Host>() {
                    @Override
                    public void write(Collection<Host> collection, Local file) {
                        fail();
                    }

                    @Override
                    public void write(Host item, Local file) {
                        assertNotNull(item.getUuid());
                    }
                };
            }
        });
        FolderBookmarkCollection c = new FolderBookmarkCollection(new NullLocal("", "f")) {
            @Override
            protected void save(Host bookmark) {
                assertNotNull(bookmark.getUuid());
            }
        };
        final Host b = new Host("b");
        c.add(b);
        final Host a = new Host("a");
        c.add(a);
        c.set(0, b);
        c.sort();
        assertEquals(b, c.get(0));
        assertEquals(a, c.get(1));
    }
}
