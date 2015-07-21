package com.oxfoo.examples.nio;

import com.sun.nio.file.SensitivityWatchEventModifier;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.*;
import static java.nio.file.StandardCopyOption.*;

/**
 * This class demonstrates basic usage of the WatchService API introduced in Java 7.
 *
 * Be aware that if you run this on OSX, it will be slow. This is because there has been no native integration
 * with Apple's File System Events API in Java 7 or Java 8. If you need something similar and need to support
 * OSX, please see the BarbaryWatchService project hosted at https://github.com/gjoseph/barbarywatchservice. If
 * you wan to track the bug in OpenJDK, you can see it at https://bugs.openjdk.java.net/browse/JDK-7133447
 *
 * @author Erik R. Jensen
 */
public class WatchServiceExample {

	public static void main(String[] args) throws IOException {

		// Setup directory and delete any previously existing files

		Path path = Paths.get(System.getProperty("java.io.tmpdir"), "WatchServiceTest");
		if (!Files.exists(path)) {
			Files.createDirectory(path);
		}

		Path filePath1 = path.resolve("TestFile1.txt");
		if (Files.exists(filePath1)) {
			Files.delete(filePath1);
		}

		Path filePath2 = path.resolve("TestFile2.txt");
		if (Files.exists(filePath2)) {
			Files.delete(filePath2);
		}

		// Start the watcher service
		WatchServiceRunner runner = new WatchServiceRunner(path);
		Thread thread = new Thread(runner, "WatchServiceRunner");
		thread.setDaemon(true);
		thread.start();

		while (!runner.isReady()) {
			Thread.yield();
		}

		// Execute tests

		System.out.println("Creating new file [" + filePath1 + "]");
		try (PrintWriter writer = new PrintWriter(new FileWriter(filePath1.toFile()))) {
			writer.write("Test is a test 1\n");
		}
		runner.waitForEvent();

		System.out.println("Updating file [" + filePath1 + "]");
		try (PrintWriter writer = new PrintWriter(new FileWriter(filePath1.toFile()))) {
			writer.append("This is another test\n");
		}
		runner.waitForEvent();

		System.out.println("Creating new file [" + filePath2 + "]");
		try (PrintWriter writer = new PrintWriter(new FileWriter(filePath2.toFile()))) {
			writer.write("Test is a test 2\n");
		}
		runner.waitForEvent();

		System.out.println("Copying [" + filePath1 + "] to [" + filePath2 + "]");
		Files.copy(filePath1, filePath2, REPLACE_EXISTING);
		runner.waitForEvent();

		System.out.println("Moving [" + filePath1 + " ] to [" + filePath2 + "]");
		Files.move(filePath1, filePath2, REPLACE_EXISTING);
		runner.waitForEvent();

		System.out.println("Deleting file [" + filePath2 + "]");
		Files.delete(filePath2);
		runner.waitForEvent();

		// Interrupt the thread and exit
		thread.interrupt();

	}

	public static class WatchServiceRunner implements Runnable {

		private Path watchDir;
		private boolean ready = false;
		private AtomicBoolean eventReceived = new AtomicBoolean(false);

		public WatchServiceRunner(Path watchDir) {
			this.watchDir = watchDir;
		}

		public boolean isReady() {
			return ready;
		}

		public void waitForEvent() {
			while (!eventReceived.get()) {
				Thread.yield();
			}
			eventReceived.set(false);
		}

		@Override
		public void run() {
			try {
				WatchService watchService = FileSystems.getDefault().newWatchService();
				watchDir.register(watchService,
						new WatchEvent.Kind[]{ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY},
						SensitivityWatchEventModifier.HIGH);
				System.out.println("Watching directory [" + watchDir + "] for changes");
				ready = true;

				while (!Thread.currentThread().isInterrupted()) {
					WatchKey key;
					try {
						key = watchService.take();
					} catch (InterruptedException x) {
						System.out.println("Received interrupt, exiting...");
						return;
					}

					for (WatchEvent<?> event : key.pollEvents()) {
						System.out.println("Received " + event.kind() + " event on " + event.context().toString());
						eventReceived.set(true);
					}

					if (!key.reset()) {
						break;
					}
				}
			} catch (IOException x) {
				System.err.println("An I/O error occurred: " + x.getMessage());
				x.printStackTrace();
			}
		}
	}

}
