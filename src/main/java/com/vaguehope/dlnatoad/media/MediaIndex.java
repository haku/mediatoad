package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.auth.AuthList;
import com.vaguehope.dlnatoad.media.MetadataReader.Metadata;
import com.vaguehope.dlnatoad.util.AsyncCallback;
import com.vaguehope.dlnatoad.util.FileHelper;
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
	private final ContentNode docContainer;

	public MediaIndex(final ContentTree contentTree, final HierarchyMode hierarchyMode, final MediaId mediaId, final MediaInfo mediaInfo) throws IOException {
		this.contentTree = contentTree;
		this.hierarchyMode = hierarchyMode;
		this.mediaId = mediaId;
		this.mediaInfo = mediaInfo;

		switch (hierarchyMode) {
			case PRESERVE:
				this.videoContainer = contentTree.getRootNode();
				this.imageContainer = contentTree.getRootNode();
				this.audioContainer = contentTree.getRootNode();
				this.docContainer = contentTree.getRootNode();
				break;
			case FLATTERN:
				this.videoContainer = makeFormatContainerOnTree(contentTree.getRootNode(), ContentGroup.VIDEO);
				this.imageContainer = makeFormatContainerOnTree(contentTree.getRootNode(), ContentGroup.IMAGE);
				this.audioContainer = makeFormatContainerOnTree(contentTree.getRootNode(), ContentGroup.AUDIO);
				this.docContainer = makeFormatContainerOnTree(contentTree.getRootNode(), ContentGroup.DOCUMENT);
				break;
			default:
				throw new IllegalArgumentException("Unknown mode.");
		}
	}

	@Override
	public EventResult fileFound(final File rawRootDir, final File rawFile, final EventType eventType, final Runnable onUsed) throws IOException {
		final MediaFile rootDir = MediaFile.forFile(rawRootDir);
		final MediaFile file = MediaFile.forFile(rawFile);

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
	public EventResult fileModified(final File rawRootDir, final File rawFile, final Runnable onUsed) throws IOException {
		if (!rawFile.isFile()) return EventResult.NOT_ADDED;

		final MediaFormat format = MediaFormat.identify(rawFile);
		if (format == null) return EventResult.NOT_ADDED;


		final MediaFile rootDir = MediaFile.forFile(rawRootDir);
		final MediaFile file = MediaFile.forFile(rawFile);

		final BigInteger authId;
		if (format.getContentGroup() == ContentGroup.SUBTITLES) {
			authId = BigInteger.ZERO;
		} else {
			final ContentNode dirContainer = dirToContentNode(rootDir, file.getParentFile(), format);
			authId = dirContainer.getAuthId();
		}

		this.mediaId.contentIdAsync(format.getContentGroup(), file, authId, new MediaIdCallback() {
			@Override
			public void onResult(final String itemId) throws IOException {
				// If ID has changed, remove and re-add.
				final ContentItem itemInTree = MediaIndex.this.contentTree.getItem(itemId);
				if (itemInTree == null) {
					MediaIndex.this.contentTree.removeFile(file);
					addFile(rootDir, file, new Runnable() {
						@Override
						public void run() {
							LOG.info("File ID changed {}: {}", file.getAbsolutePath(), itemId);
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
	public void fileGone(final File rawFile, final boolean isDir) throws IOException {
		final MediaFile file = MediaFile.forFile(rawFile);

		final MediaFormat format = MediaFormat.identify(rawFile);
		if (format != null) {
			switch (format.getContentGroup()) {
				case SUBTITLES:
					deattachSubtitles(file, format);
					break;
				default:
			}
		}

		if (this.contentTree.removeFile(file) > 0) {
			LOG.info("unshared: {}", file.getAbsolutePath());
		}
		this.mediaId.fileGoneAsync(file);
	}

	private void addFile(final MediaFile rootDir, final MediaFile file, final Runnable onComplete) throws IOException {
		if (!rootDir.exists()) throw new IllegalArgumentException("Not found: " + rootDir);
		if (!file.isFile()) return;

		final MediaFormat format = MediaFormat.identify(file);
		if (format == null) return;

		switch (format.getContentGroup()) {
			case AUDIO:
			case IMAGE:
			case VIDEO:
			case DOCUMENT:
				putFileToContentTree(rootDir, file, format, onComplete);
				break;
			case SUBTITLES:
				attachSubtitlesToItem(file, format, onComplete);
				break;
			case ARCHIVE:
				expandArchive(rootDir, file, format, onComplete);
				break;
			default:
		}
	}

	private void expandArchive(final MediaFile rootDir, final MediaFile file, final MediaFormat format, final Runnable onComplete) throws IOException {
		// TODO check for feature flag here in args --expand-archives or something like that.

		switch (format) {
		case ZIP:
			expandZip(rootDir, file, onComplete);
			break;
		default:
		}
	}

	private void expandZip(final MediaFile rootDir, final MediaFile file, final Runnable onComplete) throws IOException {
		for (final MediaFile mf : MediaFile.expandZip(file)) {
			final MediaFormat format = MediaFormat.identify(mf.getName());
			// TODO extend to other groups once this is working.
			if (format == null || format.getContentGroup() != ContentGroup.IMAGE) continue;

			putFileToContentTree(rootDir, mf, format, onComplete);
		}
	}

	private void putFileToContentTree(final MediaFile rootDir, final MediaFile file, final MediaFormat format,
			final Runnable onComplete) throws IOException {
		final MediaFile dir = file.getParentFile();
		final ContentNode dirContainer = dirToContentNode(rootDir, dir, format);
		makeItemInContainer(format, dirContainer, file, file.getName(), onComplete);
	}

	private ContentNode dirToContentNode(final MediaFile rootDir, final MediaFile dir, final MediaFormat format) throws IOException {
		final ContentNode formatContainer;
		switch (format.getContentGroup()) { // FIXME make hashmap and look this up?
			case VIDEO:
				formatContainer = this.videoContainer;
				break;
			case IMAGE:
				formatContainer = this.imageContainer;
				break;
			case AUDIO:
				formatContainer = this.audioContainer;
				break;
			case DOCUMENT:
				formatContainer = this.docContainer;
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
			final String dirPath = FileHelper.rootAndPath(rootDir, dir);
			dirContainer = makeDirContainerOnTree(format.getContentGroup(), formatContainer, dirId, dir, dirPath);
		}
		return dirContainer;
	}

	private ContentNode makeFormatContainerOnTree(final ContentNode parentNode, final ContentGroup group) throws IOException {
		return makeContainerOnTree(parentNode, group.getId(), group.getHumanName(), null, null, null);
	}

	private ContentNode makeDirContainerOnTree(final ContentGroup contentGroup, final ContentNode parentContainer,
			final String id, final MediaFile dir, final String path) throws IOException {
		final ContentNode parentNode = this.contentTree.getNode(parentContainer.getId());
		final String sortName = dir.getAbsolutePath().toLowerCase();
		final ContentNode dirContainer = makeContainerOnTree(parentNode, id, dir.getName(), sortName, dir, path);

		findArtItem(dir, contentGroup, dirContainer, new AsyncCallback<ContentItem, IOException>() {
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
			final ContentNode formatContainer, final MediaFile rootDir, final MediaFile dir) throws IOException {
		final List<MediaFile> dirsToCreate = new ArrayList<>();

		// When in PRESERVE mode do not prefix dir IDs with the type.
		final ContentGroup groupForContainerId = this.hierarchyMode == HierarchyMode.PRESERVE
				? null
				: format.getContentGroup();

		MediaFile ittrDir = dir;
		while (ittrDir != null) {
			final String ittrDirId = this.mediaId.contentIdForDirectory(groupForContainerId, ittrDir);
			final ContentNode node = this.contentTree.getNode(ittrDirId);
			if (node != null) break;
			dirsToCreate.add(ittrDir);
			if (rootDir.equals(ittrDir)) break;
			ittrDir = ittrDir.getParentFile();
		}

		Collections.reverse(dirsToCreate);

		for (final MediaFile dirToCreate : dirsToCreate) {
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
			final String dirPath = FileHelper.rootAndPath(rootDir, dirToCreate);
			makeDirContainerOnTree(format.getContentGroup(), parentContainer, dirToCreateId, dirToCreate, dirPath);
		}

		final String dirId = this.mediaId.contentIdForDirectory(groupForContainerId, dir);
		return this.contentTree.getNode(dirId);
	}

	/**
	 * If it already exists it will return the existing instance.
	 */
	private ContentNode makeContainerOnTree(final ContentNode parentNode, final String id, final String title,
			final String sortName, final MediaFile file, final String path) throws IOException {
		final ContentNode existingNode = this.contentTree.getNode(id);
		if (existingNode != null) return existingNode;

		final AuthList authList = file != null ? AuthList.forDir(file) : null;
		final String modTitle = authList != null && !authList.equals(parentNode.getAuthList())
				? title + " (restricted)"
				: title;

		final ContentNode newNode = new ContentNode(id, parentNode.getId(), modTitle, file, path, authList, sortName);
		if (parentNode.addNodeIfAbsent(newNode)) {
			this.contentTree.addNode(newNode);
			return newNode;
		}

		final ContentNode preExisting = this.contentTree.getNode(id);
		if (preExisting == null) throw new IllegalStateException("parentNode already had item with ID but it is not in the content tree: " + id);
		return preExisting;
	}

	private void makeItemInContainer(final MediaFormat format, final ContentNode parent, final MediaFile file,
			final String title, final Runnable onComplete) throws IOException {
		this.mediaId.contentIdAsync(format.getContentGroup(), file, parent.getAuthId(), new MediaIdCallback() {
			@Override
			public void onResult(final String itemId) throws IOException {
				final boolean added = makeItemInContainer(format, parent, file, title, itemId);
				if (added) onComplete.run();
			}

			@Override
			public void onError(final IOException e) {
				if (e instanceof FileNotFoundException) {
					// Includes permission denied, e.g. java.io.FileNotFoundException: /foo/bar/file.png (Permission denied)
					LOG.warn("Error adding item to media index: {}", e.toString());
				}
				else {
					LOG.warn("Error adding item to media index.", e);
				}
			}
		});
	}

	private boolean makeItemInContainer(final MediaFormat format, final ContentNode parent, final MediaFile file,
			final String title, final String id) throws IOException {
		if (parent.hasItemWithId(id)) return false;  // Optimistic lock.

		final ContentItem item = new ContentItem(id, parent.getId(), title, file, format);
		if (parent.addItemIfAbsent(item)) {
			this.contentTree.addItem(item);
			findMetadata(file, item);
			findArt(file, format, item, parent);

			final ContentGroup contentGroup = format.getContentGroup();
			if (contentGroup == ContentGroup.VIDEO) {
				findSubtitlesForItem(item, file);
			}
			if (contentGroup == ContentGroup.VIDEO || contentGroup == ContentGroup.AUDIO || contentGroup == ContentGroup.IMAGE) {
				this.mediaInfo.readInfoAsync(file, item);
			}

			return true;
		}
		return false;
	}

	private static void findMetadata(final MediaFile file, final ContentItem item) {
		final Metadata md = MetadataReader.read(file);
		if (md == null) return;
		item.setMetadata(md);
	}

	private void findArt(final MediaFile mediaFile, final MediaFormat mediaFormat, final ContentItem item, final ContentNode node) throws IOException {
		// Images are their own art so no need to search for anything.
		if (mediaFormat.getContentGroup() == ContentGroup.IMAGE) return;

		findArtItem(mediaFile, mediaFormat.getContentGroup(), node, new AsyncCallback<ContentItem, IOException>() {
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

	private void findArtItem(final MediaFile mediaFile, final ContentGroup mediaContentGroup, final ContentNode node, final AsyncCallback<ContentItem, IOException> callback) throws IOException {
		final MediaFile artFile = CoverArtHelper.findCoverArt(mediaFile);
		if (artFile == null) return;

		final MediaFormat artFormat = MediaFormat.identify(artFile);
		if (artFormat == null) {
			LOG.warn("Ignoring art file of unsupported type: {}", artFile);
			return;
		}

		this.mediaId.contentIdAsync(mediaContentGroup, artFile, node.getAuthId(), new MediaIdCallback() {
			@Override
			public void onResult(final String artId) throws IOException {
				final ContentItem artItem = new ContentItem(artId, node.getId(), artFile.getName(), artFile, artFormat);
				MediaIndex.this.contentTree.addItem(artItem);
				callback.onResult(artItem);
			}

			@Override
			public void onError(final IOException e) {
				callback.onError(e);
			}
		});
	}

	private void attachSubtitlesToItem(final MediaFile subtitlesFile, final MediaFormat subtitlesFormat,
			final Runnable onComplete) throws IOException {
		// Note that contentIdAsync() is used for a dir here to preserve ordering of events.
		this.mediaId.contentIdAsync(ContentGroup.VIDEO, subtitlesFile.getParentFile(), null, new MediaIdCallback() {
			@Override
			public void onResult(final String dirNodeId) throws IOException {
				final ContentNode dirNode = MediaIndex.this.contentTree.getNode(dirNodeId);
				if (dirNode == null) return;

				dirNode.withEachItem(i -> {
					final MediaFile itemFile = i.getFile();
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

	private void deattachSubtitles(final MediaFile subtitlesFile, final MediaFormat subtitlesFormat) throws IOException {
		final String dirNodeId = this.mediaId.contentIdForDirectory(ContentGroup.VIDEO, subtitlesFile.getParentFile());
		final ContentNode dirNode = MediaIndex.this.contentTree.getNode(dirNodeId);
		if (dirNode == null) return;

		dirNode.withEachItem(i -> {
			removeSubtitlesFromItem(i, subtitlesFile, subtitlesFormat, () -> {
				LOG.info("subtitles removed: {}", subtitlesFile.getAbsolutePath());
			});
		});
	}

	private void findSubtitlesForItem(final ContentItem item, final MediaFile itemFile) throws IOException {
		final MediaFile dir = itemFile.getParentFile();
		if (dir == null) throw new NullPointerException("itemFile has null parent: " + itemFile);

		final String[] fNames = dir.list(new BasenameFilter(itemFile));
		if (fNames == null) {
			LOG.warn("Directory containing item no longer contains itself: {}", itemFile);
			return;
		}

		for (final String fName : fNames) {
			final MediaFormat fFormat = MediaFormat.identify(fName);
			if (fFormat != null && fFormat.getContentGroup() == ContentGroup.SUBTITLES) {
				addSubtitlesToItem(item, dir.containedFile(fName), fFormat, null);
			}
		}
	}

	private void addSubtitlesToItem(final ContentItem item, final MediaFile subtitlesFile, final MediaFormat subtitlesFormat,
			final Runnable onComplete) throws IOException {
		// TODO actually look up the ContentNode and get the real auth value.
		// For now exposing subtitles to people who guess their ID is not a big deal.
		this.mediaId.contentIdAsync(subtitlesFormat.getContentGroup(), subtitlesFile, BigInteger.ZERO, new MediaIdCallback() {
			@Override
			public void onResult(final String subtitlesId) throws IOException {
				final ContentItem subtitlesItem = new ContentItem(subtitlesId, item.getParentId(), subtitlesFile.getName(), subtitlesFile, subtitlesFormat);
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

	private void removeSubtitlesFromItem(final ContentItem item, final MediaFile subtitlesFile, final MediaFormat subtitlesFormat,
			final Runnable onComplete) throws IOException {
		// OK to use fake auth value here cos the file is being removed anyway.
		this.mediaId.contentIdAsync(subtitlesFormat.getContentGroup(), subtitlesFile, BigInteger.ZERO, new MediaIdCallback() {
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

		public BasenameFilter(final MediaFile file) {
			this.baseName = FilenameUtils.getBaseName(file.getName());
		}

		@Override
		public boolean accept(final File dir, final String name) {
			return name.startsWith(this.baseName);
		}
	}

}
