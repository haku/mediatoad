package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.PersonWithRole;
import org.fourthline.cling.support.model.Protocol;
import org.fourthline.cling.support.model.ProtocolInfo;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.WriteStatus;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.dlna.DLNAAttribute;
import org.fourthline.cling.support.model.dlna.DLNAConversionIndicator;
import org.fourthline.cling.support.model.dlna.DLNAConversionIndicatorAttribute;
import org.fourthline.cling.support.model.dlna.DLNAFlags;
import org.fourthline.cling.support.model.dlna.DLNAFlagsAttribute;
import org.fourthline.cling.support.model.dlna.DLNAOperations;
import org.fourthline.cling.support.model.dlna.DLNAOperationsAttribute;
import org.fourthline.cling.support.model.dlna.DLNAProfileAttribute;
import org.fourthline.cling.support.model.dlna.DLNAProfiles;
import org.fourthline.cling.support.model.dlna.DLNAProtocolInfo;
import org.fourthline.cling.support.model.item.AudioItem;
import org.fourthline.cling.support.model.item.ImageItem;
import org.fourthline.cling.support.model.item.Item;
import org.fourthline.cling.support.model.item.VideoItem;
import org.seamless.util.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;
import com.vaguehope.dlnatoad.dlnaserver.ContentNode;
import com.vaguehope.dlnatoad.dlnaserver.ContentTree;
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
	private final String externalHttpContext;
	private final HierarchyMode hierarchyMode;
	private final MediaId mediaId;
	private final MediaInfo mediaInfo;

	private final ContentNode videoContainer;
	private final ContentNode imageContainer;
	private final ContentNode audioContainer;

	public MediaIndex(final ContentTree contentTree, final String externalHttpContext,
			final HierarchyMode hierarchyMode, final MediaId mediaId, final MediaInfo mediaInfo) {
		this.contentTree = contentTree;
		this.externalHttpContext = externalHttpContext;
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
			final String dirId = this.mediaId.contentIdSync(format.getContentGroup(), dir);
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

		findArtRes(dir, contentGroup, new AsyncCallback<Res, IOException>() {
			@Override
			public void onResult(Res artRes) throws IOException {
				if (artRes == null) return;
				dirContainer.addContainerProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(URI.create(artRes.getValue())));
			}

			@Override
			public void onError(IOException e) {
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
			final String ittrDirId = this.mediaId.contentIdSync(groupForContainerId, ittrDir);
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
				final String parentContainerId = this.mediaId.contentIdSync(groupForContainerId,
						dirToCreate.getParentFile());
				parentContainer = this.contentTree.getNode(parentContainerId);
			}

			final String dirToCreateId = this.mediaId.contentIdSync(groupForContainerId, dirToCreate);
			makeDirContainerOnTree(format.getContentGroup(), parentContainer, dirToCreateId, dirToCreate);
		}

		final String dirId = this.mediaId.contentIdSync(groupForContainerId, dir);
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
		final ContentNode node = this.contentTree.getNode(id);
		if (node != null) return node;

		final Container container = new Container();
		container.setClazz(new DIDLObject.Class("object.container"));
		container.setId(id);
		container.setParentID(parentNode.getId());
		container.setTitle(title);
		container.setRestricted(true);
		container.setWriteStatus(WriteStatus.NOT_WRITABLE);
		container.setChildCount(Integer.valueOf(0));

		final ContentNode contentNode = new ContentNode(id, container, file);
		if (sortName != null) contentNode.setSortName(sortName);

		// Sorting happens during add, so sort name must already be set.
		if (parentNode.addChildContainerIfAbsent(container)) {
			this.contentTree.addNode(contentNode);
			return contentNode;
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
		if (parent.hasChildItemWithId(id)) return false;  // Optimistic lock.

		final Res res = new Res(formatToMime(format), Long.valueOf(file.length()), contentServletPathForId(id));
		res.setSize(file.length());

		final Item item;
		switch (format.getContentGroup()) {
			case VIDEO:
				// res.setDuration(formatDuration(durationMillis));
				// res.setResolution(resolutionXbyY);
				item = new VideoItem(id, parent.getId(), title, "", res);
				findSubtitlesForItem(item, file);
				this.mediaInfo.readInfoAsync(file, res);
				break;
			case IMAGE:
				// res.setResolution(resolutionXbyY);
				item = new ImageItem(id, parent.getId(), title, "", res);
				break;
			case AUDIO:
				// res.setDuration(formatDuration(durationMillis));
				item = new AudioItem(id, parent.getId(), title, "", res);
				this.mediaInfo.readInfoAsync(file, res);
				break;
			default:
				throw new IllegalArgumentException();
		}

		findMetadata(file, item);

		if (parent.addChildItemIfAbsent(item)) {
			final ContentNode node = new ContentNode(item.getId(), item, file, format);
			this.contentTree.addNode(node);

			switch (format.getContentGroup()) {
				case VIDEO:
				case AUDIO:
					findArt(file, format, node);
					break;
				default:
			}

			return true;
		}
		return false;
	}

	/**
	 * Not synchronised because item should only by visible to caller.
	 */
	private static void findMetadata(final File file, final Item item) {
		final Metadata md = MetadataReader.read(file);
		if (md == null) return;
		if (md.getArtist() != null)
			item.addProperty(new DIDLObject.Property.UPNP.ARTIST(new PersonWithRole(md.getArtist())));
		if (md.getAlbum() != null) item.addProperty(new DIDLObject.Property.UPNP.ALBUM(md.getAlbum()));
	}

	private void findArt(final File mediaFile, final MediaFormat mediaFormat, final ContentNode node) throws IOException {
		findArtRes(mediaFile, mediaFormat.getContentGroup(), new AsyncCallback<Res, IOException>() {
			@Override
			public void onResult(Res artRes) throws IOException {
				if (artRes == null) return;
				node.withItem(i -> {
					i.addProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(URI.create(artRes.getValue())));
					i.addResource(artRes);
				});
			}

			@Override
			public void onError(IOException e) {
				LOG.warn("Error while attaching art to item: " + node, e);
			}
		});
	}

	private void findArtRes(final File mediaFile, final ContentGroup mediaContentGroup, AsyncCallback<Res, IOException> callback) throws IOException {
		final File artFile = CoverArtHelper.findCoverArt(mediaFile);
		if (artFile == null) return;

		final MediaFormat artFormat = MediaFormat.identify(artFile);
		if (artFormat == null) {
			LOG.warn("Ignoring art file of unsupported type: {}", artFile);
			return;
		}
		final MimeType artMimeType = formatToMime(artFormat);

		this.mediaId.contentIdAsync(mediaContentGroup, artFile, new MediaIdCallback() {
			@Override
			public void onResult(String artId) throws IOException {
				MediaIndex.this.contentTree.addNode(new ContentNode(artId, null, artFile, artFormat));
				final Res res = new Res(makeProtocolInfo(artMimeType), Long.valueOf(artFile.length()), contentServletPathForId(artId));
				callback.onResult(res);
			}

			@Override
			public void onError(IOException e) {
				callback.onError(e);
			}
		});

	}

	private static DLNAProtocolInfo makeProtocolInfo(final MimeType artMimeType) {
		@SuppressWarnings("rawtypes")
		final EnumMap<DLNAAttribute.Type, DLNAAttribute> attributes = new EnumMap<>(DLNAAttribute.Type.class);

		final DLNAProfiles dlnaThumbnailProfile = findDlnaThumbnailProfile(artMimeType);
		if (dlnaThumbnailProfile != null) {
			attributes.put(DLNAAttribute.Type.DLNA_ORG_PN, new DLNAProfileAttribute(dlnaThumbnailProfile));
		}
		attributes.put(DLNAAttribute.Type.DLNA_ORG_OP, new DLNAOperationsAttribute(DLNAOperations.RANGE));
		attributes.put(DLNAAttribute.Type.DLNA_ORG_CI,
				new DLNAConversionIndicatorAttribute(DLNAConversionIndicator.TRANSCODED));
		attributes.put(DLNAAttribute.Type.DLNA_ORG_FLAGS, new DLNAFlagsAttribute(
				DLNAFlags.INTERACTIVE_TRANSFERT_MODE, DLNAFlags.BACKGROUND_TRANSFERT_MODE, DLNAFlags.DLNA_V15));

		return new DLNAProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, artMimeType.toString(), attributes);
	}

	private static final Collection<DLNAProfiles> DLNA_THUMBNAIL_TYPES = Collections.unmodifiableList(Arrays.asList(
			DLNAProfiles.JPEG_TN, DLNAProfiles.PNG_TN));

	private static final Map<String, DLNAProfiles> MIME_TYPE_TO_DLNA_THUMBNAIL_TYPE;
	static {
		final Map<String, DLNAProfiles> m = new HashMap<>();
		for (final DLNAProfiles p : DLNA_THUMBNAIL_TYPES) {
			m.put(p.getContentFormat(), p);
		}
		MIME_TYPE_TO_DLNA_THUMBNAIL_TYPE = Collections.unmodifiableMap(m);
	}

	private static DLNAProfiles findDlnaThumbnailProfile(final MimeType mimeType) {
		return MIME_TYPE_TO_DLNA_THUMBNAIL_TYPE.get(mimeType.toString());
	}

	private void attachSubtitlesToItem(final File subtitlesFile, final MediaFormat subtitlesFormat,
			final Runnable onComplete) throws IOException {
		this.mediaId.contentIdAsync(ContentGroup.VIDEO, subtitlesFile.getParentFile(), new MediaIdCallback() {
			@Override
			public void onResult(final String dirNodeId) throws IOException {
				final ContentNode dirNode = MediaIndex.this.contentTree.getNode(dirNodeId);
				if (dirNode == null) return;

				dirNode.withEachChildItem(i -> {
					final File itemFile = MediaIndex.this.contentTree.getNode(i.getId()).getFile();
					if (new BasenameFilter(itemFile).accept(null, subtitlesFile.getName())) {
						addSubtitles(i, subtitlesFile, subtitlesFormat, () -> {
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

				parent.withEachChildItem(i -> {
					removeSubtitles(i, subtitlesFile, subtitlesFormat, () -> {
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

	private void findSubtitlesForItem(final Item item, final File itemFile) throws IOException {
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
				addSubtitles(item, new File(dir, fName), fFormat, null);
			}
		}
	}

	private void addSubtitles(final Item item, final File subtitlesFile, final MediaFormat subtitlesFormat,
			final Runnable onComplete) throws IOException {
		this.mediaId.contentIdAsync(subtitlesFormat.getContentGroup(), subtitlesFile, new MediaIdCallback() {
			@Override
			public void onResult(final String subtitlesId) throws IOException {
				final Res subtitlesRes = new Res(formatToMime(subtitlesFormat), Long.valueOf(subtitlesFile.length()),
						contentServletPathForId(subtitlesId));
				MediaIndex.this.contentTree.addNode(new ContentNode(subtitlesId, null, subtitlesFile, subtitlesFormat));
				if (addResourceToItemIfNotPresent(item, subtitlesRes)) {
					if (onComplete != null) onComplete.run();
				}
			}

			@Override
			public void onError(final IOException e) {
				LOG.warn("Failed to add subtitles, error getting subtitlesId.", e);
			}
		});
	}

	private void removeSubtitles(final Item item, final File subtitlesFile, final MediaFormat subtitlesFormat,
			final Runnable onComplete) throws IOException {
		this.mediaId.contentIdAsync(subtitlesFormat.getContentGroup(), subtitlesFile, new MediaIdCallback() {
			@Override
			public void onResult(String subtitlesId) throws IOException {
				final Res subtitlesRes = new Res(formatToMime(subtitlesFormat), Long.valueOf(subtitlesFile.length()),
						contentServletPathForId(subtitlesId));
				if (removeResourceFromItem(item, subtitlesRes)) {
					if (onComplete != null) onComplete.run();
				}
			}

			@Override
			public void onError(IOException e) {
				LOG.warn("Failed to remove subtitles, error getting subtitlesId.", e);
			}
		});
	}

	private String contentServletPathForId(final String id) {
		return this.externalHttpContext + "/" + C.CONTENT_PATH_PREFIX + id;
	}

	private static boolean addResourceToItemIfNotPresent(final Item item, final Res res) {
		if (res.getValue() == null) throw new IllegalArgumentException("Resource value must not be null.");
		synchronized (item) {
			for (final Res r : item.getResources()) {
				if (res.getValue().equals(r.getValue())) return false;
			}
			item.addResource(res);
			return true;
		}
	}

	private static boolean removeResourceFromItem(final Item item, final Res res) {
		if (res.getValue() == null) throw new IllegalArgumentException("Resource value must not be null.");
		synchronized (item) {
			final Iterator<Res> resIttr = item.getResources().iterator();
			while (resIttr.hasNext()) {
				if (res.getValue().equals(resIttr.next().getValue())) {
					resIttr.remove();
					return true;
				}
			}
			return false;
		}
	}

	private static MimeType formatToMime(final MediaFormat format) {
		final String mime = format.getMime();
		return new MimeType(mime.substring(0, mime.indexOf('/')), mime.substring(mime.indexOf('/') + 1));
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
