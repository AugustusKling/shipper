package shipper;

import static shipper.ShipperLogger.info;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.net.SocketAppender;

/**
 * Monitors a file path for changes and forwards changed lines via log4j
 * {@link SocketAppender}.
 */
public class Main {
	/**
	 * Command line arguments.
	 */
	private enum arg {
		/**
		 * File path.
		 */
		FILE("Path to monitored file"),
		/**
		 * Recipient name.
		 */
		HOST("Target hostname"),
		/**
		 * Recipient port.
		 */
		PORT("Target port", "4560"),
		/**
		 * Whether to sent all lines or just added lines.
		 */
		SKIP("Skip over existing data", "true"),
		/**
		 * Input file encoding.
		 */
		FILE_ENCODING("Encoding of the input file", "UTF-8"),
		/**
		 * Configuration files to control details of log output.
		 */
		LOGGING_CONFIGURATION("Path to log4j configuration.", "");

		/**
		 * Hint, displayed in usage message.
		 */
		private String hint;

		/**
		 * Default value for parameter.
		 */
		private String defaultValue;

		arg(String hint) {
			this(hint, null);
		}

		arg(String hint, String defaultValue) {
			this.hint = hint;
			this.defaultValue = defaultValue;
		}

		/**
		 * @return Command line option in long-option style.
		 */
		private String getCommandLineName() {
			return "--" + this.name().toLowerCase().replace("_", "-");
		}
	}

	/**
	 * Categories of messages to user.
	 */
	private enum MessageCategory {
		SENDING, NO_SUCH_FILE, FILE_ROTATED
	}

	static Logger logger = Logger.getLogger(Main.class);

	/**
	 * Command line arguments (unparsed).
	 */
	private static List<String> arguments;

	public static void main(String[] args) throws IOException {
		arguments = Arrays.asList(args);

		// Configure logging system.
		Properties logConfig = new Properties();
		InputStream logConfigStream = null;
		try {
			if (get(arg.LOGGING_CONFIGURATION).isEmpty()) {
				// Use defaults because user did not ask for specific
				// configuration.
				logConfigStream = Main.class
						.getResourceAsStream("logging.properties");
			} else if (Files.exists(Paths.get(get(arg.LOGGING_CONFIGURATION)))) {
				// Use users configuration.
				logConfigStream = new FileInputStream(
						get(arg.LOGGING_CONFIGURATION));
			} else {
				System.err
						.println("Specified logging configration file does not exist. Using default configuration.");
				logConfigStream = Main.class
						.getResourceAsStream("logging.properties");
			}
			logConfig.load(new InputStreamReader(logConfigStream, Charset
					.forName("UTF-8")));
		} finally {
			if (logConfigStream != null) {
				logConfigStream.close();
			}
		}

		// Configure target or log messages according to command line.
		logConfig.put("log4j.appender.shipperSocket.remoteHost", get(arg.HOST));
		logConfig.put("log4j.appender.shipperSocket.port", get(arg.PORT));
		PropertyConfigurator.configure(logConfig);

		// Monitor a single file.
		new FileMonitor(Paths.get(get(arg.FILE)),
				Charset.forName(get(arg.FILE_ENCODING)),
				new FileModificationListener() {
					/**
					 * If {@code true} encountered messages are ignored.
					 */
					boolean skip = Boolean.valueOf(get(arg.SKIP));

					/**
					 * Category of last emitted message.
					 */
					private MessageCategory lastCategory = MessageCategory.SENDING;

					@Override
					public void noSuchFile(Path path) {
						println(MessageCategory.NO_SUCH_FILE,
								"File at "
										+ path.toAbsolutePath()
										+ " is not existent. Path will be monitored for newly added files.");
						// Disable skipping as a newly created to be found file
						// will only contain new data.
						skip = false;
					}

					@Override
					public void lineAdded(Path path, String lineContent) {
						if (!skip) {
							println(MessageCategory.SENDING,
									"Sending lines of " + path.toAbsolutePath()
											+ " (after non-normal state).");

							// Send encountered message to target host.
							logger.info(lineContent);
						}
					}

					@Override
					public void fileRotated(Path path) {
						println(MessageCategory.FILE_ROTATED,
								"File at "
										+ path.toAbsolutePath()
										+ " was rotated. Will send all lines of new file.");
					}

					@Override
					public void completelyRead(Path file) {
						// File end was reached, disable skipping to begin
						// sending newly added messages.
						skip = false;
					}

					/**
					 * Shows message to user if category changes.
					 * 
					 * @param newCategory
					 *            Message category.
					 * @param message
					 *            Text to display.
					 */
					private void println(MessageCategory newCategory,
							String message) {
						if (!lastCategory.equals(newCategory)) {
							info(message);
							lastCategory = newCategory;
						}
					}
				});
	}

	/**
	 * Parses command line. Prints usage info and exits if argument without
	 * default was not specified.
	 * 
	 * @param argument
	 *            Command line argument.
	 * @return Current value or default value.
	 */
	private static String get(arg argument) {
		int nameIndex = arguments.indexOf(argument.getCommandLineName());
		int valueIndex = nameIndex + 1;
		if (nameIndex == -1 || valueIndex >= arguments.size()) {
			// Fall back to default value if argument not on command line and
			// default value exists.
			if (argument.defaultValue != null && nameIndex == -1) {
				return argument.defaultValue;
			} else {
				printUsageAndExit(argument);
				return null;
			}
		} else {
			// Take value from command line.
			return arguments.get(valueIndex);
		}
	}

	/**
	 * Prints usage instruction.
	 * 
	 * @param argument
	 *            The argument that could not be read whilst trying to parse the
	 *            command line.
	 */
	private static void printUsageAndExit(arg argument) {
		System.err.println("Missing value for " + argument.getCommandLineName()
				+ ": " + argument.hint);
		System.err.print("Usage: java -jar shipper.jar");
		for (arg option : arg.values()) {
			String defaultValue;
			if (option.defaultValue == null) {
				defaultValue = "…";
			} else if (option.defaultValue.isEmpty()) {
				defaultValue = "\"\"";
			} else {
				defaultValue = option.defaultValue;
			}
			System.err.print(" " + option.getCommandLineName() + " "
					+ defaultValue);

		}
		System.err.println();

		// Abort as there is no sensible way to continue with incomplete setup.
		System.exit(1);
	}

}
