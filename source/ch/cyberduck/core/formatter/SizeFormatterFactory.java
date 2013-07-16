package ch.cyberduck.core.formatter;

import ch.cyberduck.core.Preferences;

/**
 * @version $Id: SizeFormatterFactory.java 10299 2012-10-16 11:43:30Z dkocher $
 */
public final class SizeFormatterFactory {

    private SizeFormatterFactory() {
        //
    }

    public static SizeFormatter get() {
        return SizeFormatterFactory.get(Preferences.instance().getBoolean("browser.filesize.decimal"));
    }

    public static SizeFormatter get(final boolean decimal) {
        if(decimal) {
            return new ch.cyberduck.core.formatter.DecimalSizeFormatter();
        }
        // Default is binary sizes
        return new ch.cyberduck.core.formatter.BinarySizeFormatter();
    }
}