package ch.cyberduck.core.ftp;

/*
 * Copyright (c) 2002-2010 David Kocher. All rights reserved.
 *
 * http://cyberduck.ch/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Bug fixes, suggestions and comments should be sent to:
 * dkocher@cyberduck.ch
 */

import ch.cyberduck.core.Preferences;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.net.ftp.FTPCommand;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.log4j.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @version $Id: FTPClient.java 10852 2013-04-15 09:40:06Z dkocher $
 */
public class FTPClient extends FTPSClient {
    private static final Logger log = Logger.getLogger(FTPClient.class);

    private SSLSocketFactory sslSocketFactory;

    public FTPClient(final SSLSocketFactory f, final SSLContext c) {
        super(false, c);
        this.sslSocketFactory = f;
    }

    /**
     * Cached
     */
    private List<String> features = Collections.emptyList();

    /**
     * Query feature set.
     */
    private boolean featSupported = Preferences.instance().getBoolean("ftp.command.feat");

    /**
     * Get the server supplied features
     *
     * @return string containing server features, or null if no features or not
     *         supported
     * @throws IOException I/O failure
     */
    public List<String> listFeatures() throws IOException {
        if(features.isEmpty()) {
            if(featSupported) {
                if(FTPReply.isPositiveCompletion(this.feat())) {
                    features = Arrays.asList(this.getReplyStrings());
                }
                else {
                    featSupported = false;
                }
            }
        }
        return features;
    }

    public boolean isFeatureSupported(final int command) throws IOException {
        return this.isFeatureSupported(this.getCommand(command));
    }

    public boolean isFeatureSupported(final String feature) throws IOException {
        for(String item : this.listFeatures()) {
            if(item.trim().startsWith(feature)) {
                return true;
            }
        }
        log.warn(String.format("No %s support", feature));
        return false;
    }

    /**
     * Additional commands supported
     */
    private static final Map<Integer, String> commands
            = new HashMap<Integer, String>();

    public static final int SIZE = 52;
    public static final int PRET = 53;

    static {
        commands.put(SIZE, "SIZE");
        commands.put(PRET, "PRET");
    }

    @Override
    public int sendCommand(final int command, final String args) throws IOException {
        return super.sendCommand(this.getCommand(command), args);
    }

    protected String getCommand(final int command) {
        final String value = commands.get(command);
        if(null == value) {
            return FTPCommand.getCommand(command);
        }
        return value;
    }

    @Override
    protected Socket _openDataConnection_(final String command, final String arg) throws IOException {
        final Socket socket = super._openDataConnection_(command, arg);
        if(null == socket) {
            throw new FTPException(this.getReplyString());
        }
        return socket;
    }

    @Override
    protected void _prepareDataSocket_(final Socket socket) throws IOException {
        if(Preferences.instance().getBoolean("ftp.tls.session.requirereuse")) {
            if(socket instanceof SSLSocket) {
                // Control socket is SSL
                final SSLSession session = ((SSLSocket) _socket_).getSession();
                final SSLSessionContext context = session.getSessionContext();
                context.setSessionCacheSize(Preferences.instance().getInteger("ftp.ssl.session.cache.size"));
                try {
                    final Field sessionHostPortCache = context.getClass().getDeclaredField("sessionHostPortCache");
                    sessionHostPortCache.setAccessible(true);
                    final Object cache = sessionHostPortCache.get(context);
                    final Method method = cache.getClass().getDeclaredMethod("put", Object.class, Object.class);
                    method.setAccessible(true);
                    final String key = String.format("%s:%s", socket.getInetAddress().getHostName(),
                            String.valueOf(socket.getPort())).toLowerCase(Locale.ENGLISH);
                    method.invoke(cache, key, session);
                }
                catch(NoSuchFieldException e) {
                    // Not running in expected JRE
                    log.warn("No field sessionHostPortCache in SSLSessionContext", e);
                }
                catch(Exception e) {
                    // Not running in expected JRE
                    log.warn(e.getMessage());
                }
            }
        }
    }

    /**
     * SSL versions enabled.
     */
    private List<String> versions = Collections.emptyList();

    @Override
    public void setEnabledProtocols(final String[] protocols) {
        versions = Arrays.asList(protocols);
        super.setEnabledProtocols(protocols);
    }

    @Override
    protected void execAUTH() throws IOException {
        if(versions.isEmpty()) {
            log.debug("No trust manager configured");
            return;
        }
        super.execAUTH();
    }

    @Override
    public void execPROT(String prot) throws IOException {
        try {
            super.execPROT(prot);
            if("P".equals(prot)) {
                this.setSocketFactory(sslSocketFactory);
            }
        }
        catch(SSLException e) {
            if("P".equals(prot)) {
                // Compatibility mode if server does only accept clear data connections.
                log.warn("No data channel security: " + e.getMessage());
                this.setSocketFactory(null);
                this.setServerSocketFactory(null);
            }
        }
    }

    @Override
    protected void sslNegotiation() throws java.io.IOException {
        if(versions.isEmpty()) {
            log.debug("No trust manager configured");
            return;
        }
        super.sslNegotiation();
    }

    public List<String> list(final int command) throws IOException {
        return this.list(command, null);
    }

    public List<String> list(final int command, final String pathname) throws IOException {
        this.pret(this.getCommand(command), pathname);

        Socket socket = _openDataConnection_(command, pathname);

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), getControlEncoding()));
        ArrayList<String> results = new ArrayList<String>();
        String line;
        while((line = reader.readLine()) != null) {
            _commandSupport_.fireReplyReceived(-1, line);
            results.add(line);
        }

        reader.close();
        socket.close();

        if(completePendingCommand()) {
            return results;
        }
        return Collections.emptyList();
    }

    @Override
    public boolean retrieveFile(String remote, OutputStream local) throws IOException {
        this.pret(this.getCommand(FTPCommand.RETR), remote);
        return super.retrieveFile(remote, local);
    }

    @Override
    public InputStream retrieveFileStream(String remote) throws IOException {
        this.pret(this.getCommand(FTPCommand.RETR), remote);
        return super.retrieveFileStream(remote);
    }

    @Override
    public boolean storeFile(String remote, InputStream local) throws IOException {
        this.pret(this.getCommand(FTPCommand.STOR), remote);
        return super.storeFile(remote, local);
    }

    @Override
    public OutputStream storeFileStream(String remote) throws IOException {
        this.pret(this.getCommand(FTPCommand.STOR), remote);
        return super.storeFileStream(remote);
    }

    @Override
    public boolean appendFile(String remote, InputStream local) throws IOException {
        this.pret(this.getCommand(FTPCommand.APPE), remote);
        return super.appendFile(remote, local);
    }

    @Override
    public OutputStream appendFileStream(String remote) throws IOException {
        this.pret(this.getCommand(FTPCommand.APPE), remote);
        return super.appendFileStream(remote);
    }

    /**
     * http://drftpd.org/index.php/PRET_Specifications
     *
     * @param command Command to execute
     * @param file    Remote file
     * @throws IOException I/O failure
     */
    protected void pret(final String command, final String file) throws IOException {
        if(this.isFeatureSupported(PRET)) {
            // PRET support
            if(!FTPReply.isPositiveCompletion(this.sendCommand(PRET, command + " " + file))) {
                log.warn("PRET command failed:" + this.getReplyString());
            }
        }
    }

    public long size(final String pathname) throws IOException {
        if(this.isFeatureSupported(SIZE)) {
            if(!this.setFileType(FTPClient.BINARY_FILE_TYPE)) {
                throw new FTPException(this.getReplyString());
            }
            if(FTPReply.isPositiveCompletion(this.sendCommand(SIZE, pathname))) {
                String status = StringUtils.chomp(this.getReplyString().substring(3).trim());
                // Trim off any trailing characters after a space, e.g. webstar
                // responds to SIZE with 213 55564 bytes
                Matcher matcher = Pattern.compile("\\d+").matcher(status);
                if(matcher.matches()) {
                    try {
                        return Long.parseLong(matcher.group());
                    }
                    catch(NumberFormatException ex) {
                        log.warn(String.format("Failed to parse reply:%s", status));
                    }
                }
            }
        }
        return -1;
    }

    @Override
    public String getModificationTime(final String file) throws IOException {
        final String status = super.getModificationTime(file);
        if(null == status) {
            return null;
        }
        return StringUtils.chomp(status.substring(3).trim());
    }
}