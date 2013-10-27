package shipper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

/**
 * Monitors a path to a file for changes.
 */
public class FileMonitor {
	/**
	 * Position of read content. Used to detect file rotations.
	 */
	long fileEndPosition = 0;

	/**
	 * @param path
	 *            Path to monitor.
	 * @param fileEncoding
	 *            Encoding for reading the file
	 * @param listener
	 *            Handler for detected events.
	 * @throws IOException
	 *             Errors other than {@link NoSuchFileException}.
	 */
	public FileMonitor(Path path, Charset fileEncoding,
			FileModificationListener listener) throws IOException {
		WatchService ws;

		if (!Files.exists(path)) {
			listener.noSuchFile(path);
		}

		try {
			ws = path.getFileSystem().newWatchService();
		} catch (UnsupportedOperationException e) {
			// File system does not support watching.
			polling(path, fileEncoding, listener);
			// Polling is infinite.
			return;
		}

		// Watching is likely supported.
		Kind<?>[] kinds = { StandardWatchEventKinds.ENTRY_CREATE,
				StandardWatchEventKinds.ENTRY_DELETE,
				StandardWatchEventKinds.ENTRY_MODIFY };

		while (true) {
			// Acquire a watch as close to the monitored file as possible.
			Path closestExisting = getClosestWatchable(path);
			Path watchToDesiredWatch = closestExisting.relativize(path
					.getParent());
			boolean watchingDesired = closestExisting.equals(path.getParent());
			WatchKey key = null;
			try {
				// Start watching closestExisting.
				key = closestExisting.register(ws, kinds);
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (key == null) {
				throw new NotWatchableException();
			}
			if (Files.exists(path)) {
				// Process new files (either newly created or available due to
				// parent folder moves).
				examineFile(path, fileEncoding, listener);
			}
			awaitKeys: while (true) {
				WatchKey res = null;
				try {
					// Await the presence of new events on the watched folder.
					res = ws.take();

					for (WatchEvent<?> candidate : res.pollEvents()) {
						// Skip over unknown events.
						if (candidate.kind() == StandardWatchEventKinds.OVERFLOW) {
							// Java lost events. Make sure to process existing
							// file to avoid missing additions.
							if (Files.exists(path)) {
								examineFile(path, fileEncoding, listener);
							}
							continue;
						}

						@SuppressWarnings("unchecked")
						WatchEvent<Path> event = (WatchEvent<Path>) candidate;

						if (watchingDesired) {
							// Already watching desired folder, see if monitored
							// files is affected.
							if (event.context().getName(0)
									.equals(path.getFileName())) {
								// Something happened to the monitored file.
								if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE
										|| event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
									// Read the file's content and notify
									// listener.
									examineFile(path, fileEncoding, listener);
								} else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
									listener.noSuchFile(path);
								}
							}
						} else {
							// Not yet watching desired folder.
							if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
								if (event.context().getName(0)
										.equals(watchToDesiredWatch.getName(0))) {
									// Path towards monitored file created,
									// dispose current watch and try to get a
									// closer watch.
									res.cancel();
									res = null;
									break awaitKeys;
								}
							}
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
					break;
				} finally {
					if (res != null && res.reset() == false) {
						// Aborting watch on closestExisting as closetExisting
						// is no longer watchable.
						break;
					}
				}
			}
		}
	}

	/**
	 * Polling based file watching. Used in case {@link WatchService} is not
	 * available.
	 * 
	 * @param path
	 *            Path to monitor.
	 * @param fileEncoding
	 *            Encoding for reading the file
	 * @param listener
	 *            Handler for detected events.
	 * @throws IOException
	 *             Errors other than {@link NoSuchFileException}.
	 */
	private void polling(Path path, Charset fileEncoding,
			FileModificationListener listener) throws IOException {

		while (true) {
			examineFile(path, fileEncoding, listener);

			// Wait some time to prevent overly high load.
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// Idle delay passed.
			}
		}
	}

	/**
	 * Try to read file's content. Sends new lines to {@code listener}.
	 * 
	 * @param path
	 *            Path to monitor.
	 * @param fileEncoding
	 *            Encoding for reading the file
	 * @param listener
	 *            Handler for detected events.
	 * @throws IOException
	 *             Errors other than {@link NoSuchFileException}.
	 */
	private void examineFile(Path path, Charset fileEncoding,
			FileModificationListener listener) throws IOException {
		if (Files.isRegularFile(path)) {
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

				InputStreamReader reader = new InputStreamReader(is,
						fileEncoding);
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
			} catch (NoSuchFileException e) {
				listener.noSuchFile(path);
			}
		} else {
			// File is not readable as text file.
			listener.noSuchFile(path);
		}
	}

	/**
	 * @param path
	 *            Path to file or folder. Its existence is not required on disk.
	 * @return Most specific, but existing parent.
	 */
	private Path getClosestWatchable(Path path) {
		Path parent = path.getParent();
		if (parent == null) {
			// No existent parent found in hierarchy. Expected for file systems
			// with multiple roots where the given path does not fall below any
			// of the roots.
			throw new NotWatchableException();
		} else if (Files.exists(parent)) {
			return parent;
		} else {
			// Parent does not exist but path is not yet fully consumed. Climb
			// up towards root.
			return getClosestWatchable(parent);
		}
	}
}
