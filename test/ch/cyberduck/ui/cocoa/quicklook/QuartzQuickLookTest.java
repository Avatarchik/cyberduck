package ch.cyberduck.ui.cocoa.quicklook;

import ch.cyberduck.core.AbstractTestCase;
import ch.cyberduck.core.NullLocal;
import ch.cyberduck.core.local.Local;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @version $Id: QuartzQuickLookTest.java 10530 2012-10-22 12:03:11Z dkocher $
 */
public class QuartzQuickLookTest extends AbstractTestCase {

    @BeforeClass
    public static void register() {
        QuartzQuickLook.register();
    }

    @Test
    public void testSelect() throws Exception {
        QuickLook q = QuickLookFactory.get();
        final List<Local> files = new ArrayList<Local>();
        files.add(new NullLocal(null, "f"));
        files.add(new NullLocal(null, "b"));
        q.select(files);
    }

    @Test
    public void testIsAvailable() throws Exception {
        assertTrue(QuickLookFactory.get().isAvailable());
    }

    @Test
    public void testOpen() throws Exception {
        assertFalse(QuickLookFactory.get().isOpen());
    }
}