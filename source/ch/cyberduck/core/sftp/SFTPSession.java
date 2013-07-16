package ch.cyberduck.core.sftp;

/*
 *  Copyright (c) 2007 David Kocher. All rights reserved.
 *  http://cyberduck.ch/
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Bug fixes, suggestions and comments should be sent to:
 *  dkocher@cyberduck.ch
 */

import ch.cyberduck.core.*;
import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.core.local.Local;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.kohsuke.putty.PuTTYKey;

import java.io.BufferedReader;
import java.io.CharArrayWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.ConnectionMonitor;
import ch.ethz.ssh2.InteractiveCallback;
import ch.ethz.ssh2.PacketListener;
import ch.ethz.ssh2.SCPClient;
import ch.ethz.ssh2.SFTPv3Client;
import ch.ethz.ssh2.StreamGobbler;
import ch.ethz.ssh2.channel.ChannelClosedException;
import ch.ethz.ssh2.crypto.PEMDecoder;
import ch.ethz.ssh2.crypto.PEMDecryptException;

/**
 * @version $Id: SFTPSession.java 10823 2013-04-08 17:31:39Z dkocher $
 */
public class SFTPSession extends Session {
    private static final Logger log = Logger.getLogger(SFTPSession.class);

    private Connection connection;

    public SFTPSession(Host h) {
        super(h);
    }

    @Override
    protected Connection getClient() throws ConnectionCanceledException {
        if(null == connection) {
            throw new ConnectionCanceledException();
        }
        return connection;
    }

    @Override
    public boolean isSecure() {
        if(super.isSecure()) {
            try {
                return this.getClient().isAuthenticationComplete();
            }
            catch(ConnectionCanceledException e) {
                return false;
            }
        }
        return false;
    }

    private SFTPv3Client client;

    /**
     * If never called before opens a new SFTP subsystem. If called before, the cached
     * SFTP subsystem is returned. May not be used concurrently.
     *
     * @return Client instance
     * @throws IOException If opening SFTP channel fails
     */
    protected SFTPv3Client sftp() throws IOException {
        if(null == client) {
            if(!this.isConnected()) {
                throw new ConnectionCanceledException();
            }
            if(!this.getClient().isAuthenticationComplete()) {
                throw new LoginCanceledException();
            }
            this.message(Locale.localizedString("Starting SFTP subsystem", "Status"));
            client = new SFTPv3Client(this.getClient(), new PacketListener() {
                @Override
                public void read(String packet) {
                    SFTPSession.this.log(false, packet);
                }

                @Override
                public void write(String packet) {
                    SFTPSession.this.log(true, packet);
                }
            });
            this.message(Locale.localizedString("SFTP subsystem ready", "Status"));
            client.setCharset(this.getEncoding());
        }
        return client;
    }

    /**
     * Opens a new, dedicated SCP channel for this SSH session
     *
     * @return Client instance
     * @throws IOException If opening SCP channel fails
     */
    protected SCPClient openScp() throws IOException {
        if(!this.isConnected()) {
            throw new ConnectionCanceledException();
        }
        if(!this.getClient().isAuthenticationComplete()) {
            throw new LoginCanceledException();
        }
        final SCPClient client = new SCPClient(this.getClient());
        client.setCharset(this.getEncoding());
        return client;
    }

    @Override
    protected void connect() throws IOException {
        if(this.isConnected()) {
            return;
        }
        this.fireConnectionWillOpenEvent();

        connection = new Connection(HostnameConfiguratorFactory.get(host.getProtocol()).lookup(host.getHostname()), host.getPort(), this.getUserAgent());
        connection.setTCPNoDelay(true);
        connection.addConnectionMonitor(new ConnectionMonitor() {
            @Override
            public void connectionLost(Throwable reason) {
                log.warn(String.format("Connection lost:%s", (null == reason) ? "Unknown" : reason.getMessage()));
                interrupt();
            }
        });

        final int timeout = this.timeout();
        this.getClient().connect(HostKeyControllerFactory.get(this), timeout, timeout);
        if(!this.isConnected()) {
            throw new ConnectionCanceledException();
        }
        this.login();
        if(!this.getClient().isAuthenticationComplete()) {
            throw new LoginCanceledException();
        }
        // Make sure subsystem is available
        this.sftp();
        this.fireConnectionDidOpenEvent();
    }

    @Override
    protected void login(final LoginController controller, final Credentials credentials) throws IOException {
        if(this.getClient().isAuthenticationComplete()) {
            this.message(Locale.localizedString("Login successful", "Credentials"));
            // Already authenticated
            return;
        }
        if(credentials.isPublicKeyAuthentication()) {
            if(this.loginUsingPublicKeyAuthentication(controller, credentials)) {
                this.message(Locale.localizedString("Login successful", "Credentials"));
                return;
            }
        }
        else if(this.loginUsingKBIAuthentication(controller, credentials)) {
            this.message(Locale.localizedString("Login successful", "Credentials"));
            return;
        }
        else if(this.loginUsingPasswordAuthentication(controller, credentials)) {
            this.message(Locale.localizedString("Login successful", "Credentials"));
            return;
        }
        if(this.getClient().isAuthenticationPartialSuccess()) {
            final Credentials additional = new Credentials(credentials.getUsername(), null, false) {
                @Override
                public String getUsernamePlaceholder() {
                    return credentials.getUsernamePlaceholder();
                }

                @Override
                public String getPasswordPlaceholder() {
                    return getHost().getProtocol().getPasswordPlaceholder();
                }
            };
            controller.prompt(host.getProtocol(), additional,
                    Locale.localizedString("Partial authentication success", "Credentials"),
                    Locale.localizedString("Provide additional login credentials", "Credentials") + ".", false, false, false);
            if(this.loginUsingKBIAuthentication(controller, additional)) {
                this.message(Locale.localizedString("Login successful", "Credentials"));
                return;
            }
        }
        this.message(Locale.localizedString("Login failed", "Credentials"));
        controller.fail(host.getProtocol(), credentials);
        this.login();
    }

    /**
     * Authenticate with public key
     *
     * @param controller  Login prompt
     * @param credentials Username and password for private key
     * @return True if authentication succeeded
     * @throws IOException Error reading private key
     */
    private boolean loginUsingPublicKeyAuthentication(final LoginController controller, final Credentials credentials)
            throws IOException {

        log.debug("loginUsingPublicKeyAuthentication:" + credentials);
        if(this.getClient().isAuthMethodAvailable(credentials.getUsername(), "publickey")) {
            if(credentials.isPublicKeyAuthentication()) {
                final Local identity = credentials.getIdentity();
                final CharArrayWriter privatekey = new CharArrayWriter();
                if(PuTTYKey.isPuTTYKeyFile(identity.getInputStream())) {
                    final PuTTYKey putty = new PuTTYKey(identity.getInputStream());
                    if(putty.isEncrypted()) {
                        if(StringUtils.isEmpty(credentials.getPassword())) {
                            controller.prompt(host.getProtocol(), credentials,
                                    Locale.localizedString("Private key password protected", "Credentials"),
                                    Locale.localizedString("Enter the passphrase for the private key file", "Credentials")
                                            + " (" + identity + ")");
                        }
                    }
                    try {
                        IOUtils.copy(new StringReader(putty.toOpenSSH(credentials.getPassword())), privatekey);
                    }
                    catch(PEMDecryptException e) {
                        this.message(Locale.localizedString("Invalid passphrase", "Credentials"));
                        controller.prompt(host.getProtocol(), credentials,
                                Locale.localizedString("Invalid passphrase", "Credentials"),
                                Locale.localizedString("Enter the passphrase for the private key file", "Credentials")
                                        + " (" + identity + ")");
                        return this.loginUsingPublicKeyAuthentication(controller, credentials);
                    }
                }
                else {
                    IOUtils.copy(new FileReader(identity.getAbsolute()), privatekey);
                    if(PEMDecoder.isPEMEncrypted(privatekey.toCharArray())) {
                        if(StringUtils.isEmpty(credentials.getPassword())) {
                            controller.prompt(host.getProtocol(), credentials,
                                    Locale.localizedString("Private key password protected", "Credentials"),
                                    Locale.localizedString("Enter the passphrase for the private key file", "Credentials")
                                            + " (" + identity + ")");
                        }
                    }
                    try {
                        PEMDecoder.decode(privatekey.toCharArray(), credentials.getPassword());
                    }
                    catch(PEMDecryptException e) {
                        this.message(Locale.localizedString("Invalid passphrase", "Credentials"));
                        controller.prompt(host.getProtocol(), credentials,
                                Locale.localizedString("Invalid passphrase", "Credentials"),
                                Locale.localizedString("Enter the passphrase for the private key file", "Credentials")
                                        + " (" + identity + ")");

                        return this.loginUsingPublicKeyAuthentication(controller, credentials);
                    }
                }
                return this.getClient().authenticateWithPublicKey(credentials.getUsername(),
                        privatekey.toCharArray(), credentials.getPassword());
            }
        }
        return false;
    }

    /**
     * Authenticate with plain password.
     *
     * @param controller  Login prompt
     * @param credentials Username and password
     * @return True if authentication succeeded
     * @throws IOException Login failed or canceled
     */
    private boolean loginUsingPasswordAuthentication(final LoginController controller, final Credentials credentials) throws IOException {
        log.debug("loginUsingPasswordAuthentication:" + credentials);
        if(this.getClient().isAuthMethodAvailable(credentials.getUsername(), "password")) {
            return this.getClient().authenticateWithPassword(credentials.getUsername(), credentials.getPassword());
        }
        return false;
    }

    /**
     * Authenticate using challenge and response method.
     *
     * @param controller  Login prompt
     * @param credentials Username and password
     * @return True if authentication succeeded
     * @throws IOException Login failed or canceled
     */
    private boolean loginUsingKBIAuthentication(final LoginController controller, final Credentials credentials) throws IOException {
        log.debug("loginUsingKBIAuthentication:" + credentials);
        if(this.getClient().isAuthMethodAvailable(credentials.getUsername(), "keyboard-interactive")) {
            return this.getClient().authenticateWithKeyboardInteractive(credentials.getUsername(),
                    /**
                     * The logic that one has to implement if "keyboard-interactive" autentication shall be
                     * supported.
                     */
                    new InteractiveCallback() {
                        private int promptCount = 0;

                        /**
                         * The callback may be invoked several times, depending on how
                         * many questions-sets the server sends
                         */
                        @Override
                        public String[] replyToChallenge(String name, String instruction, int numPrompts, String[] prompt,
                                                         boolean[] echo) throws IOException {
                            log.debug("replyToChallenge:" + name);
                            // In its first callback the server prompts for the password
                            if(0 == promptCount) {
                                log.debug("First callback returning provided credentials");
                                promptCount++;
                                return new String[]{credentials.getPassword()};
                            }
                            String[] response = new String[numPrompts];
                            for(int i = 0; i < numPrompts; i++) {
                                controller.prompt(host.getProtocol(), credentials,
                                        Locale.localizedString("Provide additional login credentials", "Credentials"), prompt[i], false, false, false);
                                response[i] = credentials.getPassword();
                                promptCount++;
                            }
                            return response;
                        }
                    });
        }
        return false;
    }

    @Override
    public void close() {
        try {
            this.fireConnectionWillCloseEvent();
            if(client != null) {
                client.close();
            }
            this.getClient().close();
        }
        catch(ConnectionCanceledException e) {
            log.warn(e.getMessage());
        }
        finally {
            client = null;
            connection = null;
            this.fireConnectionDidCloseEvent();
        }
    }

    @Override
    public void interrupt() {
        log.debug("interrupt");
        try {
            this.fireConnectionWillCloseEvent();
            this.getClient().close(null, true);
        }
        catch(ConnectionCanceledException e) {
            log.warn(e.getMessage());
        }
        finally {
            client = null;
            connection = null;
            this.fireConnectionDidCloseEvent();
        }
    }

    @Override
    public void check() throws IOException {
        try {
            super.check();
        }
        catch(ChannelClosedException e) {
            log.warn("Connection already closed:" + e.getMessage());
            this.interrupt();
            this.connect();
        }
        SFTPv3Client subsystem = this.sftp();
        if(!subsystem.isConnected()) {
            log.warn("Connection to subsystem already closed");
            this.interrupt();
            this.connect();
        }
        else {
            try {
                subsystem.canonicalPath(".");
            }
            catch(IOException e) {
                log.warn("Connection already closed:" + e.getMessage());
                this.interrupt();
                this.connect();
            }
        }
    }

    @Override
    public Path workdir() throws IOException {
        // "." as referring to the current directory
        final String directory = this.sftp().canonicalPath(".");
        return new SFTPPath(this, directory,
                directory.equals(String.valueOf(Path.DELIMITER)) ? Path.VOLUME_TYPE | Path.DIRECTORY_TYPE : Path.DIRECTORY_TYPE);
    }

    @Override
    protected void noop() throws IOException {
        if(this.isConnected()) {
            try {
                this.getClient().sendIgnorePacket();
            }
            catch(IllegalStateException e) {
                throw new ConnectionCanceledException(e);
            }
        }
    }

    @Override
    public boolean isSendCommandSupported() {
        return true;
    }

    @Override
    public boolean isArchiveSupported() {
        return true;
    }

    @Override
    public boolean isUnarchiveSupported() {
        return true;
    }

    @Override
    public void sendCommand(final String command) throws IOException {
        final ch.ethz.ssh2.Session sess = this.getClient().openSession();
        try {
            this.message(command);

            sess.execCommand(command, host.getEncoding());

            BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(new StreamGobbler(sess.getStdout())));
            BufferedReader stderrReader = new BufferedReader(new InputStreamReader(new StreamGobbler(sess.getStderr())));

            try {
                // Here is the output from stdout
                while(true) {
                    String line = stdoutReader.readLine();
                    if(null == line) {
                        break;
                    }
                    this.log(false, line);
                }
                // Here is the output from stderr
                StringBuilder error = new StringBuilder();
                while(true) {
                    String line = stderrReader.readLine();
                    if(null == line) {
                        break;
                    }
                    this.log(false, line);
                    // Standard error output contains all status messages, not only errors.
                    if(StringUtils.isNotBlank(error.toString())) {
                        error.append(" ");
                    }
                    error.append(line).append(".");
                }
                if(StringUtils.isNotBlank(error.toString())) {
                    this.error(error.toString(), null);
                }
            }
            finally {
                IOUtils.closeQuietly(stdoutReader);
                IOUtils.closeQuietly(stderrReader);
            }
        }
        finally {
            sess.close();
        }
    }

    @Override
    public boolean isDownloadResumable() {
        return this.isTransferResumable();
    }

    @Override
    public boolean isUploadResumable() {
        return this.isTransferResumable();
    }

    @Override
    public boolean isCreateSymlinkSupported() {
        return true;
    }

    @Override
    public boolean isUnixPermissionsSupported() {
        return true;
    }

    /**
     * No resume supported for SCP transfers.
     *
     * @return True if SFTP is the selected transfer protocol for SSH sessions.
     */
    private boolean isTransferResumable() {
        return Preferences.instance().getProperty("ssh.transfer").equals(Protocol.SFTP.getIdentifier());
    }
}
