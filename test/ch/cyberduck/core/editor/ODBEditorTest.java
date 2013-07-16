package ch.cyberduck.core.editor;

import ch.cyberduck.core.AbstractTestCase;
import ch.cyberduck.core.local.LaunchServicesApplicationFinder;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @version $Id: ODBEditorTest.java 10419 2012-10-18 14:16:42Z dkocher $
 */
public class ODBEditorTest extends AbstractTestCase {

    @BeforeClass
    public static void register() {
        LaunchServicesApplicationFinder.register();
        ODBEditorFactory.register();
    }

    @Test
    @Ignore
    public void testEdit() throws Exception {

    }
}
