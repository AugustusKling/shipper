package shipper;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class FileMonitorTest {

	private enum Events {
		NO_SUCH_FILE, LINE_ADDED, FILE_ROTATED, COMPLETELY_READ
	}

	private class TestDone extends RuntimeException {
		private static final long serialVersionUID = 5446394894332138488L;
	}

	@Test
	public void test() throws IOException {
		final List<Events> events = new ArrayList<>();

		final Path tempFile = Files.createTempFile(null, null);
		try {
			new FileMonitor(tempFile, Charset.defaultCharset(),
					new FileModificationListener() {
						private int step = 0;

						@Override
						public void noSuchFile(Path path) {
							org.junit.Assert.assertNotEquals(0, events.size());
							org.junit.Assert.assertEquals(step, 1);

							events.add(Events.NO_SUCH_FILE);

							try (FileWriter w = new FileWriter(tempFile
									.toFile())) {
								w.append("new");
							} catch (IOException e) {
								throw new RuntimeException(e);
							}
						}

						@Override
						public void lineAdded(Path file, String lineContent) {
							org.junit.Assert.assertNotEquals(0, events.size());

							events.add(Events.LINE_ADDED);
							if (step == 0) {
								org.junit.Assert.assertEquals("initial",
										lineContent);
								try {
									Files.delete(tempFile);
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
								step = 1;
							} else if (step == 1) {
								org.junit.Assert.assertEquals("new",
										lineContent);
								step = 2;
							}
						}

						@Override
						public void fileRotated(Path file) {
							org.junit.Assert.assertNotEquals(0, events.size());

							events.add(Events.FILE_ROTATED);
						}

						@Override
						public void completelyRead(Path file) {
							if (step == 0) {
								org.junit.Assert.assertEquals(0, events.size());
								try (FileWriter w = new FileWriter(tempFile
										.toFile())) {
									w.append("initial");
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
							}

							events.add(Events.COMPLETELY_READ);
							if (step == 2) {
								throw new TestDone();
							}
						}
					});
		} catch (TestDone e) {
			// passed.
			events.contains(Events.COMPLETELY_READ);
			events.contains(Events.LINE_ADDED);
			events.contains(Events.NO_SUCH_FILE);
		}
	}

}
