package ch.cyberduck.core;

import ch.cyberduck.core.local.Local;
import ch.cyberduck.core.local.LocalFactory;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @version $Id: CredentialsTest.java 10661 2012-12-17 14:04:25Z dkocher $
 */
public class CredentialsTest extends AbstractTestCase {

    @Test
    public void testEquals() {
        assertEquals(new DefaultCredentials("a", "b"), new DefaultCredentials("a", "b"));
        assertNotSame(new DefaultCredentials("a", "b"), new DefaultCredentials("a", "c"));
    }

    @Test
    public void testSetIdentity() throws Exception {
        Credentials c = new DefaultCredentials();
        c.setIdentity(LocalFactory.createLocal("~/.ssh/unknown.rsa"));
        assertFalse(c.isPublicKeyAuthentication());
        final Local t = LocalFactory.createLocal(Preferences.instance().getProperty("tmp.dir"), "id_rsa");
        assertTrue(t.touch());
        c.setIdentity(t);
        assertTrue(c.isPublicKeyAuthentication());
        t.delete();
    }

    @Test
    public void testAnonymous() throws Exception {
        Credentials c = new DefaultCredentials("anonymous", "");
        assertEquals("cyberduck@example.net", c.getPassword());
    }

    @Test
    public void testDefault() throws Exception {
        Credentials c = new DefaultCredentials();
        assertEquals(null, c.getUsername());
        assertEquals(null, c.getPassword());
    }

    @Test
    public void testValidateEmpty() throws Exception {
        Credentials c = new DefaultCredentials("user", "");
        assertTrue(c.validate(Protocol.FTP));
        assertFalse(c.validate(Protocol.WEBDAV));
        assertFalse(c.validate(Protocol.SFTP));
    }

    @Test
    public void testValidateBlank() throws Exception {
        Credentials c = new DefaultCredentials("user", " ");
        assertTrue(c.validate(Protocol.FTP));
        assertTrue(c.validate(Protocol.WEBDAV));
        assertTrue(c.validate(Protocol.SFTP));
    }

    @Test
    public void testLoginReasonable() {
        Credentials credentials = new Credentials("guest", "changeme");
        assertTrue(credentials.validate(Protocol.FTP));
    }

    @Test
    public void testLoginWithoutUsername() {
        Credentials credentials = new Credentials(null,
                Preferences.instance().getProperty("connection.login.anon.pass"));
        assertFalse(credentials.validate(Protocol.FTP));
    }

    @Test
    public void testLoginWithoutPass() {
        Credentials credentials = new Credentials("guest", null);
        assertFalse(credentials.validate(Protocol.FTP));
    }

    @Test
    public void testLoginWithoutEmptyPass() {
        Credentials credentials = new Credentials("guest", "");
        assertTrue(credentials.validate(Protocol.FTP));
    }

    @Test
    public void testLoginAnonymous1() {
        Credentials credentials = new Credentials(Preferences.instance().getProperty("connection.login.anon.name"),
                Preferences.instance().getProperty("connection.login.anon.pass"));
        assertTrue(credentials.validate(Protocol.FTP));
    }

    @Test
    public void testLoginAnonymous2() {
        Credentials credentials = new Credentials(Preferences.instance().getProperty("connection.login.anon.name"),
                null);
        assertTrue(credentials.validate(Protocol.FTP));
    }

    /**
     * http://trac.cyberduck.ch/ticket/1204
     */
    @Test
    public void testLogin1204() {
        Credentials credentials = new Credentials("cyberduck.login",
                "1seCret");
        assertTrue(credentials.validate(Protocol.FTP));
        assertEquals("cyberduck.login", credentials.getUsername());
        assertEquals("1seCret", credentials.getPassword());
    }
}