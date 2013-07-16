package ch.cyberduck.core.transfer;

import ch.cyberduck.core.AbstractTestCase;
import ch.cyberduck.core.Preferences;
import ch.cyberduck.ui.cocoa.UserDefaultsPreferences;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @version $Id: TransferOptionsTest.java 10979 2013-05-02 08:40:53Z dkocher $
 */
public class TransferOptionsTest extends AbstractTestCase {

    @Test
    public void testQuarantine() {
        assertEquals(Preferences.instance().getBoolean("queue.download.quarantine"), new TransferOptions().quarantine);
    }
}
