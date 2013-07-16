package ch.cyberduck.core;

import ch.cyberduck.ui.cocoa.UserDefaultsPreferences;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @version $Id: PreferencesTest.java 10130 2012-10-14 16:09:30Z dkocher $
 */
public class PreferencesTest extends AbstractTestCase {

    @BeforeClass
    public static void register() {
        UserDefaultsPreferences.register();
    }

    @Test
    public void testGetListEscapedWhitespace() throws Exception {
        Preferences p = Preferences.instance();
        p.setProperty("metadata",
                "Cache-Control=public,max-age=31536000 Expires=Fri,\\ 01\\ Feb\\ 2013\\ 00:00:00\\ GMT");

        final List<String> properties = p.getList("metadata");
        assertTrue(properties.contains("Cache-Control=public,max-age=31536000"));
        assertTrue(properties.contains("Expires=Fri,\\ 01\\ Feb\\ 2013\\ 00:00:00\\ GMT"));
    }

    @Test
    public void testGetList() throws Exception {
        Preferences p = Preferences.instance();
        p.setProperty("metadata",
                "a b");

        final List<String> properties = p.getList("metadata");
        assertTrue(properties.contains("a"));
        assertTrue(properties.contains("b"));
    }

    @Test
    public void testLong() {
        Preferences p = Preferences.instance();
        p.setProperty("test.p", 2L);
        assertEquals("2", p.getProperty("test.p"));
    }

    @Test
    public void testDouble() {
        Preferences p = Preferences.instance();
        p.setProperty("test.p", 0.983652);
        assertEquals("0.983652", p.getProperty("test.p"));
    }
}