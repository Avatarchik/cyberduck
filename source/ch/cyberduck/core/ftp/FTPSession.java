package ch.cyberduck.core.ftp;

/*
 *  Copyright (c) 2005 David Kocher. All rights reserved.
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
import ch.cyberduck.core.ftp.parser.CompositeFileEntryParser;
import ch.cyberduck.core.ftp.parser.LaxUnixFTPEntryParser;
import ch.cyberduck.core.ftp.parser.RumpusFTPEntryParser;
import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.core.ssl.CustomTrustSSLProtocolSocketFactory;
import ch.cyberduck.core.ssl.SSLSession;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.net.ProtocolCommandListener;
import org.apache.commons.net.ftp.Configurable;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPFileEntryParser;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.parser.NetwareFTPEntryParser;
import org.apache.commons.net.ftp.parser.ParserInitializationException;
import org.apache.commons.net.ftp.parser.UnixFTPEntryParser;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Opens a connection to the remote server via ftp protocol
 *
 * @version $Id: FTPSession.java 10852 2013-04-15 09:40:06Z dkocher $
 */
public class FTPSession extends SSLSession {
    private static final Logger log = Logger.getLogger(FTPSession.class);

    private FTPClient client;

    private TimeZone tz;

    /**
     * Listing parser
     */
    private FTPFileEntryParser parser;

    public FTPSession(Host h) {
        super(h);
    }

    @Override
    protected FTPClient getClient() throws ConnectionCanceledException {
        if(null == client) {
            throw new ConnectionCanceledException();
        }
        return client;
    }

    @Override
    public Path mount() {
        final Path workdir = super.mount();
        if(Preferences.instance().getBoolean("ftp.timezone.auto")) {
            if(null == host.getTimezone()) {
                // No custom timezone set
                final List<TimeZone> matches = this.calculateTimezone(workdir);
                for(TimeZone tz : matches) {
                    // Save in bookmark. User should have the option to choose from determined zones.
                    host.setTimezone(tz);
                    break;
                }
                if(!matches.isEmpty()) {
                    // Reset parser to use newly determined timezone
                    parser = null;
                }
            }
        }
        return workdir;
    }

    protected TimeZone getTimezone() throws IOException {
        if(null == host.getTimezone()) {
            return TimeZone.getTimeZone(
                    Preferences.instance().getProperty("ftp.timezone.default"));
        }
        return host.getTimezone();
    }

    /**
     * @return Directory listing parser depending on response for SYST command
     * @throws IOException Failure initializing parser
     */
    protected FTPFileEntryParser getFileParser() throws IOException {
        try {
            if(!this.getTimezone().equals(tz)) {
                tz = this.getTimezone();
                if(log.isInfoEnabled()) {
                    log.info(String.format("Reset parser to timezone %s", tz));
                }
                parser = null;
            }
            if(null == parser) {
                String system = null; //Unknown
                try {
                    system = this.getClient().getSystemType();
                }
                catch(IOException e) {
                    log.warn("SYST command failed:" + e.getMessage());
                }
                if(log.isInfoEnabled()) {
                    log.info(String.format("Using timezone %s", tz));
                }
                parser = new FTPParserFactory().createFileEntryParser(system, tz);
                if(parser instanceof Configurable) {
                    // Configure with default configuration
                    ((Configurable) parser).configure(null);
                }
                if(StringUtils.isNotBlank(system)) {
                    String ukey = system.toUpperCase(java.util.Locale.ENGLISH);
                    if(ukey.contains(FTPClientConfig.SYST_NT)) {
                        // Workaround for #5572.
                        this.setStatListSupportedEnabled(false);
                    }
                }
            }
            return parser;
        }
        catch(ParserInitializationException e) {
            IOException failure = new IOException(e.getMessage());
            failure.initCause(e);
            throw failure;
        }
    }

    /**
     * Best guess of available timezones given the offset of the modification
     * date in the directory listing from the UTC timestamp returned from <code>MDTM</code>
     * if available. Result is error prone because of additional daylight saving offsets.
     *
     * @param workdir Directory listing
     * @return Matching timezones
     */
    private List<TimeZone> calculateTimezone(final Path workdir) {
        // Determine the server offset from UTC
        final AttributedList<Path> list = workdir.children();
        if(list.isEmpty()) {
            log.warn("Cannot determine timezone with empty directory listing");
            return Collections.emptyList();
        }
        for(Path test : list) {
            if(test.attributes().isFile()) {
                long local = test.attributes().getModificationDate();
                if(-1 == local) {
                    log.warn("No modification date in directory listing to calculate timezone");
                    continue;
                }
                // Subtract seconds
                local -= local % 60000;
                // Read the modify fact which must be UTC
                test.readTimestamp();
                long utc = test.attributes().getModificationDate();
                if(-1 == utc) {
                    log.warn("No UTC support on server");
                    continue;
                }
                // Subtract seconds
                utc -= utc % 60000;
                long offset = local - utc;
                log.info(String.format("Calculated UTC offset is %dms", offset));
                final List<TimeZone> zones = new ArrayList<TimeZone>();
                if(TimeZone.getTimeZone(Preferences.instance().getProperty("ftp.timezone.default")).getOffset(utc) == offset) {
                    log.info("Offset equals local timezone offset.");
                    zones.add(TimeZone.getTimeZone(Preferences.instance().getProperty("ftp.timezone.default")));
                    return zones;
                }
                // The offset should be the raw GMT offset without the daylight saving offset.
                // However the determied offset *does* include daylight saving time and therefore
                // the call to TimeZone#getAvailableIDs leads to errorneous results.
                final String[] timezones = TimeZone.getAvailableIDs((int) offset);
                for(String timezone : timezones) {
                    log.info(String.format("Matching timezone identifier %s", timezone));
                    final TimeZone match = TimeZone.getTimeZone(timezone);
                    log.info(String.format("Determined timezone %s", match));
                    zones.add(match);
                }
                if(zones.isEmpty()) {
                    log.warn("Failed to calculate timezone for offset:" + offset);
                    continue;
                }
                return zones;
            }
        }
        log.warn("No file in directory listing to calculate timezone");
        return Collections.emptyList();
    }

    private Map<FTPFileEntryParser, Boolean> parsers = new HashMap<FTPFileEntryParser, Boolean>(1);

    /**
     * @param p Parser
     * @return True if the parser will read the file permissions
     */
    protected boolean isPermissionSupported(final FTPFileEntryParser p) {
        FTPFileEntryParser delegate;
        if(p instanceof CompositeFileEntryParser) {
            // Get the actual parser
            delegate = ((CompositeFileEntryParser) p).getCachedFtpFileEntryParser();
            if(null == delegate) {
                log.warn("Composite FTP parser has no cached delegate yet");
                return false;
            }
        }
        else {
            // Not a composite parser
            delegate = p;
        }
        if(null == parsers.get(delegate)) {
            // Cache the value as it might get queried frequently
            parsers.put(delegate, delegate instanceof UnixFTPEntryParser
                    || delegate instanceof LaxUnixFTPEntryParser
                    || delegate instanceof NetwareFTPEntryParser
                    || delegate instanceof RumpusFTPEntryParser
            );
        }
        return parsers.get(delegate);
    }

    @Override
    public void close() {
        try {
            if(this.isConnected()) {
                this.fireConnectionWillCloseEvent();
                this.getClient().logout();
            }
        }
        catch(IOException e) {
            log.error(String.format("Error closing connection: %s", e.getMessage()));
        }
        finally {
            if(null != client) {
                client.removeProtocolCommandListener(listener);
            }
            client = null;
            this.fireConnectionDidCloseEvent();
        }
    }

    @Override
    public void interrupt() {
        try {
            this.fireConnectionWillCloseEvent();
            this.getClient().disconnect();
        }
        catch(IOException e) {
            log.error(String.format("Error closing connection: %s", e.getMessage()));
        }
        finally {
            if(null != client) {
                client.removeProtocolCommandListener(listener);
            }
            client = null;
            this.fireConnectionDidCloseEvent();
        }
    }

    @Override
    public void check() throws IOException {
        try {
            super.check();
        }
        catch(FTPConnectionClosedException e) {
            log.warn("Connection already closed:" + e.getMessage());
            this.interrupt();
            this.connect();
        }
    }

    private final ProtocolCommandListener listener = new LoggingProtocolCommandListener() {
        @Override
        public void log(boolean request, String event) {
            FTPSession.this.log(request, event);
        }
    };

    protected void configure(final FTPClient client) throws IOException {
        client.setControlEncoding(this.getEncoding());
        client.removeProtocolCommandListener(listener);
        client.addProtocolCommandListener(listener);
        client.setConnectTimeout(this.timeout());
        client.setDefaultTimeout(this.timeout());
        client.setDataTimeout(this.timeout());
        client.setDefaultPort(Protocol.FTP.getDefaultPort());
        client.setParserFactory(new FTPParserFactory());
        client.setRemoteVerificationEnabled(Preferences.instance().getBoolean("ftp.datachannel.verify"));
        if(this.getHost().getProtocol().isSecure()) {
            List<String> protocols = new ArrayList<String>();
            for(String protocol : Preferences.instance().getProperty("connection.ssl.protocols").split(",")) {
                protocols.add(protocol.trim());
            }
            client.setEnabledProtocols(protocols.toArray(new String[protocols.size()]));
        }
        final int buffer = Preferences.instance().getInteger("ftp.socket.buffer");
        client.setBufferSize(buffer);
        client.setReceiveBufferSize(buffer);
        client.setSendBufferSize(buffer);
        client.setReceieveDataSocketBufferSize(buffer);
        client.setSendDataSocketBufferSize(buffer);
    }

    /**
     * @return True if the feaatures AUTH TLS, PBSZ and PROT are supported.
     * @throws IOException Error reading FEAT response
     */
    private boolean isTLSSupported() throws IOException {
        return this.getClient().isFeatureSupported("AUTH TLS")
                && this.getClient().isFeatureSupported("PBSZ")
                && this.getClient().isFeatureSupported("PROT");
    }

    @Override
    protected void connect() throws IOException {
        if(this.isConnected()) {
            return;
        }
        this.fireConnectionWillOpenEvent();

        final CustomTrustSSLProtocolSocketFactory f
                = new CustomTrustSSLProtocolSocketFactory(this.getTrustManager());

        this.client = new FTPClient(f, f.getSSLContext());

        final FTPClient client = this.getClient();
        this.configure(client);
        client.connect(host.getHostname(true), host.getPort());
        if(!this.isConnected()) {
            throw new ConnectionCanceledException();
        }
        client.setTcpNoDelay(false);
        this.login();

        if(this.getHost().getProtocol().isSecure()) {
            client.execPBSZ(0);
            // Negotiate data connection security
            client.execPROT(Preferences.instance().getProperty("ftp.tls.datachannel"));
        }

        this.fireConnectionDidOpenEvent();
        if("UTF-8".equals(this.getEncoding())) {
            if(client.isFeatureSupported("UTF8")) {
                if(!FTPReply.isPositiveCompletion(client.sendCommand("OPTS UTF8 ON"))) {
                    log.warn("Failed to negogiate UTF-8 charset:" + client.getReplyString());
                }
            }
        }
    }

    protected FTPConnectMode getConnectMode() {
        if(null == this.host.getFTPConnectMode()) {
            if(ProxyFactory.get().usePassiveFTP()) {
                return FTPConnectMode.PASV;
            }
            return FTPConnectMode.PORT;
        }
        return this.host.getFTPConnectMode();

    }

    private boolean unsecureswitch =
            Preferences.instance().getBoolean("connection.unsecure.switch");

    public boolean isUnsecureswitch() {
        return unsecureswitch;
    }

    public void setUnsecureswitch(boolean unsecureswitch) {
        this.unsecureswitch = unsecureswitch;
    }

    /**
     * Propose protocol change if AUTH TLS is available.
     *
     * @param login       Prompt
     * @param credentials Login credentials
     * @throws IOException I/O failure
     */
    @Override
    protected void warn(final LoginController login, final Credentials credentials) throws IOException {
        Host host = this.getHost();
        if(this.isUnsecureswitch()
                && !credentials.isAnonymousLogin()
                && !host.getProtocol().isSecure()
                && !Preferences.instance().getBoolean("connection.unsecure." + host.getHostname())
                && this.isTLSSupported()) {
            try {
                login.warn(MessageFormat.format(Locale.localizedString("Unsecured {0} connection", "Credentials"), host.getProtocol().getName()),
                        MessageFormat.format(Locale.localizedString("The server supports encrypted connections. Do you want to switch to {0}?", "Credentials"), Protocol.FTP_TLS.getName()),
                        Locale.localizedString("Continue", "Credentials"),
                        Locale.localizedString("Change", "Credentials"),
                        "connection.unsecure." + host.getHostname());
                // Continue choosen. Login using plain FTP.
            }
            catch(LoginCanceledException e) {
                // Protocol switch
                host.setProtocol(Protocol.FTP_TLS);
                // Reconfigure client for TLS
                this.configure(this.getClient());
                this.getClient().execAUTH();
                this.getClient().sslNegotiation();
            }
            finally {
                // Do not warn again upon subsequent login
                this.setUnsecureswitch(false);
            }
        }
    }

    @Override
    protected void login(LoginController controller, Credentials credentials) throws IOException {
        final FTPClient client = this.getClient();
        if(client.login(credentials.getUsername(), credentials.getPassword())) {
            this.message(Locale.localizedString("Login successful", "Credentials"));
        }
        else {
            this.message(Locale.localizedString("Login failed", "Credentials"));
            controller.fail(host.getProtocol(), credentials, client.getReplyString());
            this.login();
        }
    }

    @Override
    public Path workdir() throws IOException {
        final String directory = this.getClient().printWorkingDirectory();
        return new FTPPath(this, directory,
                directory.equals(String.valueOf(Path.DELIMITER)) ? Path.VOLUME_TYPE | Path.DIRECTORY_TYPE : Path.DIRECTORY_TYPE);
    }

    @Override
    protected void noop() throws IOException {
        if(this.isConnected()) {
            this.getClient().sendNoOp();
        }
    }

    @Override
    public boolean isSendCommandSupported() {
        return true;
    }

    @Override
    public void sendCommand(final String command) throws IOException {
        if(this.isConnected()) {
            this.message(command);
            this.getClient().sendSiteCommand(command);
        }
    }

    @Override
    public boolean isDownloadResumable() {
        return true;
    }

    @Override
    public boolean isUploadResumable() {
        return true;
    }

    /**
     * The sever supports STAT file listings
     */
    private boolean statListSupportedEnabled = Preferences.instance().getBoolean("ftp.command.stat");

    public void setStatListSupportedEnabled(boolean e) {
        this.statListSupportedEnabled = e;
    }

    public boolean isStatListSupportedEnabled() {
        return statListSupportedEnabled;
    }

    /**
     * The server supports MLSD
     */
    private boolean mlsdListSupportedEnabled = Preferences.instance().getBoolean("ftp.command.mlsd");

    public void setMlsdListSupportedEnabled(boolean e) {
        this.mlsdListSupportedEnabled = e;
    }

    public boolean isMlsdListSupportedEnabled() {
        return mlsdListSupportedEnabled;
    }

    /**
     * The server supports LIST -a
     */
    private boolean extendedListEnabled = Preferences.instance().getBoolean("ftp.command.lista");

    public void setExtendedListEnabled(boolean e) {
        this.extendedListEnabled = e;
    }

    public boolean isExtendedListEnabled() {
        return extendedListEnabled;
    }

    private boolean utimeSupported = Preferences.instance().getBoolean("ftp.command.utime");

    public boolean isUtimeSupported() {
        return utimeSupported;
    }

    public void setUtimeSupported(boolean utimeSupported) {
        this.utimeSupported = utimeSupported;
    }

    @Override
    public boolean isUnixPermissionsSupported() {
        return true;
    }

}