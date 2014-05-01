package shipper;

import static shipper.ShipperLogger.error;
import static shipper.ShipperLogger.info;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

import org.apache.log4j.Logger;

/**
 * Monitor on a file that forwards file contents to a {@link Logger}.
 */
public class FileListenerThread extends Thread {
	/**
	 * Categories of messages to user.
	 */
	private static enum MessageCategory {
		SENDING, NO_SUCH_FILE, FILE_ROTATED
	}

	/**
	 * File to monitor for changes.
	 */
	private Path file;

	/**
	 * Target for content forwarding.
	 */
	private Logger logger;

	/**
	 * File encoding.
	 */
	private Charset encoding;

	/**
	 * If {@code true} encountered messages are ignored.
	 */
	private boolean skip;

	/**
	 * @param file
	 *            File to monitor for changes.
	 * @param logger
	 *            Target for content forwarding.
	 * @param encoding
	 *            File encoding.
	 * @param skip
	 *            When {@code true}, ignore the current file content. Additions
	 *            still are forwarded.
	 */
	public FileListenerThread(Path file, Logger logger, Charset encoding,
			boolean skip) {
		this.file = file;
		this.logger = logger;
		this.encoding = encoding;
		this.skip = skip;

		setName("Monitor on " + file);
	}

	/**
	 * Monitors a single file for changes. Should never return.
	 */
	@Override
	public void run() {
		try {
			new FileMonitor().watch(file, encoding,
					new FileModificationListener() {
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
							// Disable skipping as a newly created to be found
							// file will
							// only contain new data.
							skip = false;
						}

						@Override
						public void lineAdded(Path path, String lineContent) {
							if (!skip) {
								println(MessageCategory.SENDING,
										"Sending lines of "
												+ path.toAbsolutePath()
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
							// sending
							// newly added messages.
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
		} catch (IOException e) {
			error("Failed to monitor " + file
					+ ". Please file an issue including the dumped stack.", e);
		}
	}
}