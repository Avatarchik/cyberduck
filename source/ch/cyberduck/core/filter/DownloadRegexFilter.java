package ch.cyberduck.core.filter;

import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathFilter;
import ch.cyberduck.core.Preferences;

import org.apache.log4j.Logger;

import java.util.regex.Pattern;

/**
 * @version $Id: DownloadRegexFilter.java 10250 2012-10-15 18:32:38Z dkocher $
 */
public class DownloadRegexFilter implements PathFilter<Path> {
    private static final Logger log = Logger.getLogger(DownloadRegexFilter.class);

    private final Pattern pattern
            = Pattern.compile(Preferences.instance().getProperty("queue.download.skip.regex"));

    @Override
    public boolean accept(final Path file) {
        if(file.attributes().isDuplicate()) {
            return false;
        }
        if(Preferences.instance().getBoolean("queue.download.skip.enable")) {
            if(pattern.matcher(file.getName()).matches()) {
                if(log.isDebugEnabled()) {
                    log.debug(String.format("Skip %s excluded with regex", file.getAbsolute()));
                }
                return false;
            }
        }
        return true;
    }
}
