package shipper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Monitors a path to a file for changes.
 */
public class FileMonitor {
	/**
	 * @param path
	 *            Path to monitor.
	 * @param listener
	 *            Handler for detected events.
	 * @throws IOException
	 *             Errors other than {@link NoSuchFileException}.
	 */
	public FileMonitor(Path path, FileModificationListener listener)
			throws IOException {
		// Position of read content. Used to detect file rotations.
		long fileEndPosition = 0;

		while (true) {
			// Open file with read option only to allow for file deletion and
			// modifications from other programs.
			try (InputStream is = Files.newInputStream(path,
					StandardOpenOption.READ)) {
				File file = path.toFile();

				if (file.length() >= fileEndPosition) {
					// Skip over already processed lines.
					is.skip(fileEndPosition);
				} else {
					// After rotation, so not skip but process whole file.
					listener.fileRotated(path);
				}

				InputStreamReader reader = new InputStreamReader(is);
				BufferedReader lineReader = new BufferedReader(reader);

				// Process all lines.
				String line;
				while ((line = lineReader.readLine()) != null) {
					listener.lineAdded(path, line);
				}
				fileEndPosition = file.length();

				// Avoid keeping the file open whilst the delay passes.
				is.close();

				listener.completelyRead(path);

				// Wait some time to prevent overly high load.
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// Idle delay passed.
				}
			} catch (NoSuchFileException e) {
				listener.noSuchFile(path);
			}
		}
	}
}
