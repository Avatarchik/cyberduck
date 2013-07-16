package ch.cyberduck.core.formatter;

/**
 * @version $Id: BinarySizeFormatter.java 10119 2012-10-14 14:51:45Z yla $
 */
public class BinarySizeFormatter extends AbstractSizeFormatter {

    private static final long KILO = 1024; //2^10
    private static final long MEGA = 1048576; // 2^20
    private static final long GIGA = 1073741824; // 2^30

    public BinarySizeFormatter() {
        super(KILO, MEGA, GIGA);
    }
}
