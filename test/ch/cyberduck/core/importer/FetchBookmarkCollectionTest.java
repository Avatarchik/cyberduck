package ch.cyberduck.core.importer;

import ch.cyberduck.core.AbstractTestCase;
import ch.cyberduck.core.NullKeychain;
import ch.cyberduck.core.local.LaunchServicesApplicationFinder;
import ch.cyberduck.core.local.LocalFactory;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @version $Id: FetchBookmarkCollectionTest.java 10419 2012-10-18 14:16:42Z dkocher $
 */
public class FetchBookmarkCollectionTest extends AbstractTestCase {

    @BeforeClass
    public static void register() {
        LaunchServicesApplicationFinder.register();
        NullKeychain.register();
    }

    @Test
    public void testGetFile() throws Exception {
        FetchBookmarkCollection c = new FetchBookmarkCollection();
        assertEquals(0, c.size());
        c.parse(LocalFactory.createLocal("test/ch/cyberduck/core/importer/com.fetchsoftworks.Fetch.Shortcuts.plist"));
        assertEquals(2, c.size());
    }
}
