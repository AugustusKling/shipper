package shipper;

import org.apache.log4j.Category;
import org.apache.log4j.Logger;

/**
 * Logging methods for use by shipper.
 */
public class ShipperLogger {
	private static Logger shipperLogger = Logger.getLogger(ShipperLogger.class);

	/**
	 * @see Category#debug(Object)
	 */
	public static void debug(Object message) {
		shipperLogger.debug(message);
	}

	/**
	 * @see Category#info(Object)
	 */
	public static void info(Object message) {
		shipperLogger.info(message);
	}

	/**
	 * @see Category#error(Object)
	 */
	public static void error(Object message) {
		shipperLogger.error(message);
	}

	/**
	 * @see Category#error(Object, Throwable)
	 */
	public static void error(Object message, Throwable t) {
		shipperLogger.error(message, t);
	}
}
