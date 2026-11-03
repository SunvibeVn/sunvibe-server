package vn.sunvibe.survey.media;

import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;

import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.infrastructure.media.store.LocalMediaObjectStore;

/** The store contract on a local directory (survey-media spec A-1: dev, test and lan only). */
class LocalMediaObjectStoreTest extends MediaObjectStoreContract {

	@TempDir
	Path root;

	@Override
	protected MediaObjectStore store() {
		return new LocalMediaObjectStore(root);
	}

	@Override
	protected MediaObjectStore brokenStore() {
		// A root that is a file: no directory can be created under it
		Path file = root.resolve("not-a-directory");
		try {
			java.nio.file.Files.writeString(file, "x");
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		return new LocalMediaObjectStore(file);
	}
}
