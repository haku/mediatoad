package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.media.MetadataReader.Metadata;
import com.vaguehope.dlnatoad.util.AsyncCallback;
import com.vaguehope.dlnatoad.util.Watcher.EventResult;
import com.vaguehope.dlnatoad.util.Watcher.EventType;
import com.vaguehope.dlnatoad.util.Watcher.FileListener;

public class MediaIndex implements FileListener {

	public enum HierarchyMode {
		FLATTERN,
		PRESERVE
	}

	private static final Logger LOG = LoggerFactory.getLogger(MediaIndex.class);

	private final ContentTree contentTree;
	private final HierarchyMode hierarchyMode;
	private final MediaId mediaId;
	private final MediaInfo mediaInfo;

	private final ContentNode videoContainer;
	private final ContentNode imageContainer;
	private final ContentNode audioContainer;

	public MediaIndex(final ContentTree contentTree, final HierarchyMode hierarchyMode, final MediaId mediaId, final MediaInfo mediaInfo) {
		this.contentTree = contentTree;
		this.hierarchyMode = hierarchyMode;
		this.mediaId = mediaId;
		this.mediaInfo = mediaInfo;

		switch (hierarchyMode) {
			case PRESERVE:
				this.videoContainer = contentTree.getRootNode();
				this.imageContainer = contentTree.getRootNode();
				this.audioContainer = contentTree.getRootNode();
				break;
			case FLATTERN:
				this.videoContainer = makeFormatContainerOnTree(contentTree.getRootNode(), ContentGroup.VIDEO);
				this.imageContainer = makeFormatContainerOnTree(contentTree.getRootNode(), ContentGroup.IMAGE);
				this.audioContainer = makeFormatContainerOnTree(contentTree.getRootNode(), ContentGroup.AUDIO);
				break;
			default:
				throw new IllegalArgumentException("Unknown mode.");
		}
	}

	@Override
	public EventResult fileFound(final File rootDir, final File file, final EventType eventType, final Runnable onUsed)
			throws IOException {
		addFile(rootDir, file, new Runnable() {
			@Override
			public void run() {
				if (eventType == EventType.NOTIFY) {
					LOG.info("shared: {}", file.getAbsolutePath());
				}
				if (onUsed != null) onUsed.run();
			}
		});
		return EventResult.NOT_SURE_YET;
	}

	@Override
	public EventResult fileModified(final File rootDir, final File file, final Runnable onUsed) throws IOException {
		if (!file.isFile()) return EventResult.NOT_ADDED;

		final MediaFormat format = MediaFormat.identify(file);
		if (format == null) return EventResult.NOT_ADDED;

		this.mediaId.contentIdAsync(format.getContentGroup(), file, new MediaIdCallback() {
			@Override
			public void onResult(final String itemId) throws IOException {
				// If ID has changed, remove and re-add.
				final ContentNode itemInTree = MediaIndex.this.contentTree.getNode(itemId);
				if (itemInTree == null) {
					MediaIndex.this.contentTree.removeFile(file);
					addFile(rootDir, file, new Runnable() {
						@Override
						public void run() {
							LOG.info("modified: {}", file.getAbsolutePath());
							if (onUsed != null) onUsed.run();
						}
					});
				}
				else {
					itemInTree.reload();
				}
			}

			@Override
			public void onError(final IOException e) {
				LOG.warn(String.format("Error processing file modified event for \"%s\"", file.getAbsolutePath()), e);
			}
		});

		return EventResult.NOT_SURE_YET;
	}

	@Override
	public void fileGone(final File file) throws IOException {
		final MediaFormat format = MediaFormat.identify(file);
		if (format != null) {
			switch (format.getContentGroup()) {
				case SUBTITLES:
					deattachSubtitles(file, format);
					break;
				default:
			}
		}

		if (this.contentTree.removeFile(file) > 0) LOG.info("unshared: {}", file.getAbsolutePath());
	}

	private void addFile(final File rootDir, final File file, final Runnable onComplete) throws IOException {
		if (!rootDir.exists()) throw new IllegalArgumentException("Not found: " + rootDir);
		if (!file.isFile()) return;

		final MediaFormat format = MediaFormat.identify(file);
		if (format == null) return;

		switch (format.getContentGroup()) {
			case AUDIO:
			case IMAGE:
			case VIDEO:
				putFileToContentTree(rootDir, file, format, onComplete);
				break;
			case SUBTITLES:
				attachSubtitlesToItem(file, format, onComplete);
				break;
			default:
		}
	}

	private void putFileToContentTree(final File rootDir, final File file, final MediaFormat format,
			final Runnable onComplete) throws IOException {
		final File dir = file.getParentFile();
		final ContentNode formatContainer;
		switch (format.getContentGroup()) { // FIXME make hashmap.
			case VIDEO:
				formatContainer = this.videoContainer;
				break;
			case IMAGE:
				formatContainer = this.imageContainer;
				break;
			case AUDIO:
				formatContainer = this.audioContainer;
				break;
			default:
				throw new IllegalStateException();
		}

		final ContentNode dirContainer;
		switch (this.hierarchyMode) {
			case PRESERVE:
				dirContainer = makeDirAndParentDirsContianersOnTree(format, formatContainer, rootDir, dir);
				break;
			case FLATTERN:
			default:
			final String dirId = this.mediaId.contentIdForDirectory(format.getContentGroup(), dir);
			dirContainer = makeDirContainerOnTree(format.getContentGroup(), formatContainer, dirId, dir);
		}

		makeItemInContainer(format, dirContainer, file, file.getName(), onComplete);
	}

	private ContentNode makeFormatContainerOnTree(final ContentNode parentNode, final ContentGroup group) {
		return makeContainerOnTree(parentNode, group.getId(), group.getHumanName());
	}

	private ContentNode makeDirContainerOnTree(final ContentGroup contentGroup, final ContentNode parentContainer,
			final String id, final File dir) throws IOException {
		final ContentNode parentNode = this.contentTree.getNode(parentContainer.getId());
		final ContentNode dirContainer = makeContainerOnTree(parentNode, id, dir.getName(), dir.getAbsolutePath(), dir);

		findArtItem(dir, contentGroup, new AsyncCallback<ContentItem, IOException>() {
			@Override
			public void onResult(final ContentItem art) throws IOException {
				if (art == null) return;
				dirContainer.setArt(art);
			}

			@Override
			public void onError(final IOException e) {
				LOG.warn("Error while attaching art to container: " + dirContainer, e);
			}
		});

		return dirContainer;
	}

	private ContentNode makeDirAndParentDirsContianersOnTree(final MediaFormat format,
			final ContentNode formatContainer, final File rootDir, final File dir) throws IOException {
		final List<File> dirsToCreate = new ArrayList<>();

		// When in PRESERVE mode do not prefix dir IDs with the type.
		final ContentGroup groupForContainerId = this.hierarchyMode == HierarchyMode.PRESERVE
				? null
				: format.getContentGroup();

		File ittrDir = dir;
		while (ittrDir != null) {
			final String ittrDirId = this.mediaId.contentIdForDirectory(groupForContainerId, ittrDir);
			final ContentNode node = this.contentTree.getNode(ittrDirId);
			if (node != null) break;
			dirsToCreate.add(ittrDir);
			if (rootDir.equals(ittrDir)) break;
			ittrDir = ittrDir.getParentFile();
		}

		Collections.reverse(dirsToCreate);

		for (final File dirToCreate : dirsToCreate) {
			final ContentNode parentContainer;
			if (rootDir.equals(dirToCreate)) {
				parentContainer = formatContainer;
			}
			else {
				final String parentContainerId = this.mediaId.contentIdForDirectory(groupForContainerId,
						dirToCreate.getParentFile());
				parentContainer = this.contentTree.getNode(parentContainerId);
			}

			final String dirToCreateId = this.mediaId.contentIdForDirectory(groupForContainerId, dirToCreate);
			makeDirContainerOnTree(format.getContentGroup(), parentContainer, dirToCreateId, dirToCreate);
		}

		final String dirId = this.mediaId.contentIdForDirectory(groupForContainerId, dir);
		return this.contentTree.getNode(dirId);
	}

	private ContentNode makeContainerOnTree(final ContentNode parentNode, final String id, final String title) {
		return makeContainerOnTree(parentNode, id, title, null);
	}

	private ContentNode makeContainerOnTree(final ContentNode parentNode, final String id, final String title,
			final String sortName) {
		return makeContainerOnTree(parentNode, id, title, sortName, null);
	}

	/**
	 * If it already exists it will return the existing instance.
	 */
	private ContentNode makeContainerOnTree(final ContentNode parentNode, final String id, final String title,
			final String sortName, final File file) {
		final ContentNode existingNode = this.contentTree.getNode(id);
		if (existingNode != null) return existingNode;

		final ContentNode newNode = new ContentNode(id, parentNode.getId(), title, file, sortName);
		if (parentNode.addNodeIfAbsent(newNode)) {
			this.contentTree.addNode(newNode);
			return newNode;
		}

		final ContentNode preExisting = this.contentTree.getNode(id);
		if (preExisting == null) throw new IllegalStateException("parentNode already had item with ID but its not in tree: " + id);
		return preExisting;
	}

	private void makeItemInContainer(final MediaFormat format, final ContentNode parent, final File file,
			final String title, final Runnable onComplete) throws IOException {
		this.mediaId.contentIdAsync(format.getContentGroup(), file, new MediaIdCallback() {
			@Override
			public void onResult(final String itemId) throws IOException {
				final boolean added = makeItemInContainer(format, parent, file, title, itemId);
				if (added) onComplete.run();
			}

			@Override
			public void onError(final IOException e) {
				if (e instanceof FileNotFoundException) {
					LOG.warn("File disappeared: {}", file.getAbsolutePath());
				}
				else {
					LOG.warn("Error adding item to media index.", e);
				}
			}
		});
	}

	private boolean makeItemInContainer(final MediaFormat format, final ContentNode parent, final File file,
			final String title, final String id) throws IOException {
		if (parent.hasItemWithId(id)) return false;  // Optimistic lock.

		final ContentItem item = new ContentItem(id, parent.getId(), title, file, format);
		if (parent.addItemIfAbsent(item)) {
			this.contentTree.addItem(item);
			findMetadata(file, item);
			findArt(file, format, item);

			final ContentGroup contentGroup = format.getContentGroup();
			if (contentGroup == ContentGroup.VIDEO) {
				findSubtitlesForItem(item, file);
			}
			// TODO mediaInfo on reads duration ATM, but could also ready width/height for all types.
			// TODO remove this condition once width/height are read.
			if (contentGroup == ContentGroup.VIDEO || contentGroup == ContentGroup.AUDIO) {
				this.mediaInfo.readInfoAsync(file, item);
			}

			return true;
		}
		return false;
	}

	private static void findMetadata(final File file, final ContentItem item) {
		final Metadata md = MetadataReader.read(file);
		if (md == null) return;
		item.setMetadata(md);
	}

	private void findArt(final File mediaFile, final MediaFormat mediaFormat, final ContentItem item) throws IOException {
		findArtItem(mediaFile, mediaFormat.getContentGroup(), new AsyncCallback<ContentItem, IOException>() {
			@Override
			public void onResult(final ContentItem artItem) throws IOException {
				item.setArt(artItem);
			}

			@Override
			public void onError(final IOException e) {
				LOG.warn("Error while attaching art to item: " + item, e);
			}
		});
	}

	private void findArtItem(final File mediaFile, final ContentGroup mediaContentGroup, final AsyncCallback<ContentItem, IOException> callback) throws IOException {
		final File artFile = CoverArtHelper.findCoverArt(mediaFile);
		if (artFile == null) return;

		final MediaFormat artFormat = MediaFormat.identify(artFile);
		if (artFormat == null) {
			LOG.warn("Ignoring art file of unsupported type: {}", artFile);
			return;
		}

		this.mediaId.contentIdAsync(mediaContentGroup, artFile, new MediaIdCallback() {
			@Override
			public void onResult(final String artId) throws IOException {
				final ContentItem artItem = new ContentItem(artId, null, null, artFile, artFormat);
				MediaIndex.this.contentTree.addItem(artItem);
				callback.onResult(artItem);
			}

			@Override
			public void onError(final IOException e) {
				callback.onError(e);
			}
		});
	}

	private void attachSubtitlesToItem(final File subtitlesFile, final MediaFormat subtitlesFormat,
			final Runnable onComplete) throws IOException {
		this.mediaId.contentIdAsync(ContentGroup.VIDEO, subtitlesFile.getParentFile(), new MediaIdCallback() {
			@Override
			public void onResult(final String dirNodeId) throws IOException {
				final ContentNode dirNode = MediaIndex.this.contentTree.getNode(dirNodeId);
				if (dirNode == null) return;

				dirNode.withEachItem(i -> {
					final File itemFile = i.getFile();
					if (new BasenameFilter(itemFile).accept(null, subtitlesFile.getName())) {
						addSubtitlesToItem(i, subtitlesFile, subtitlesFormat, () -> {
							LOG.info("subtitles for {}: {}", itemFile.getAbsolutePath(),
									subtitlesFile.getAbsolutePath());
							if (onComplete != null) onComplete.run();
						});
					}
				});
			}

			@Override
			public void onError(final IOException e) {
				LOG.warn("Failed to attach subtitles, error getting dirNodeId.", e);
			}
		});
	}

	private void deattachSubtitles(final File subtitlesFile, final MediaFormat subtitlesFormat) throws IOException {
		this.mediaId.contentIdAsync(ContentGroup.VIDEO, subtitlesFile.getParentFile(), new MediaIdCallback() {
			@Override
			public void onResult(final String parentId) throws IOException {
				final ContentNode parent = MediaIndex.this.contentTree.getNode(parentId);
				if (parent == null) return;

				parent.withEachItem(i -> {
					removeSubtitlesFromItem(i, subtitlesFile, subtitlesFormat, () -> {
						LOG.info("subtitles removed: {}", subtitlesFile.getAbsolutePath());
					});
				});
			}

			@Override
			public void onError(final IOException e) {
				LOG.warn("Failed to deattach subtitles, error getting parentId.", e);
			}
		});
	}

	private void findSubtitlesForItem(final ContentItem item, final File itemFile) throws IOException {
		final File dir = itemFile.getParentFile();
		if (dir == null) throw new NullPointerException("itemFile has null parent: " + itemFile);

		final String[] fNames = dir.list(new BasenameFilter(itemFile));
		if (fNames == null) {
			LOG.warn("Directory containing item no longer contains itself: {}", itemFile);
			return;
		}

		for (final String fName : fNames) {
			final MediaFormat fFormat = MediaFormat.identify(fName);
			if (fFormat != null && fFormat.getContentGroup() == ContentGroup.SUBTITLES) {
				addSubtitlesToItem(item, new File(dir, fName), fFormat, null);
			}
		}
	}

	private void addSubtitlesToItem(final ContentItem item, final File subtitlesFile, final MediaFormat subtitlesFormat,
			final Runnable onComplete) throws IOException {
		this.mediaId.contentIdAsync(subtitlesFormat.getContentGroup(), subtitlesFile, new MediaIdCallback() {
			@Override
			public void onResult(final String subtitlesId) throws IOException {
				final ContentItem subtitlesItem = new ContentItem(subtitlesId, null, null, subtitlesFile, subtitlesFormat);
				MediaIndex.this.contentTree.addItem(subtitlesItem);
				if (item.addAttachmentIfNotPresent(subtitlesItem)) {
					if (onComplete != null) onComplete.run();
				}
			}

			@Override
			public void onError(final IOException e) {
				LOG.warn("Failed to add subtitles, error getting subtitlesId.", e);
			}
		});
	}

	private void removeSubtitlesFromItem(final ContentItem item, final File subtitlesFile, final MediaFormat subtitlesFormat,
			final Runnable onComplete) throws IOException {
		this.mediaId.contentIdAsync(subtitlesFormat.getContentGroup(), subtitlesFile, new MediaIdCallback() {
			@Override
			public void onResult(final String subtitlesId) throws IOException {
				if (item.removeAttachmetById(subtitlesId)) {
					if (onComplete != null) onComplete.run();
				}
			}

			@Override
			public void onError(final IOException e) {
				LOG.warn("Failed to remove subtitles, error getting subtitlesId.", e);
			}
		});
	}

	private final class BasenameFilter implements FilenameFilter {

		private final String baseName;

		public BasenameFilter(final File file) {
			this.baseName = FilenameUtils.getBaseName(file.getName());
		}

		@Override
		public boolean accept(final File dir, final String name) {
			return name.startsWith(this.baseName);
		}
	}

}
