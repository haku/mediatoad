package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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

import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;
import com.vaguehope.dlnatoad.dlnaserver.ContentNode;
import com.vaguehope.dlnatoad.dlnaserver.ContentTree;
import com.vaguehope.dlnatoad.media.MetadataReader.Metadata;
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

	private final Container videoContainer;
	private final Container imageContainer;
	private final Container audioContainer;

	public MediaIndex (final ContentTree contentTree, final String externalHttpContext, final HierarchyMode hierarchyMode, final MediaId mediaId, final MediaInfo mediaInfo) {
		this.contentTree = contentTree;
		this.externalHttpContext = externalHttpContext;
		this.hierarchyMode = hierarchyMode;
		this.mediaId = mediaId;
		this.mediaInfo = mediaInfo;

		switch (hierarchyMode) {
			case PRESERVE:
				this.videoContainer = contentTree.getRootNode().getContainer();
				this.imageContainer = contentTree.getRootNode().getContainer();
				this.audioContainer = contentTree.getRootNode().getContainer();
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
	public void fileFound (final File rootDir, final File file, final EventType eventType) throws IOException {
		if (addFile(rootDir, file) && eventType == EventType.NOTIFY) LOG.info("shared: {}", file.getAbsolutePath());
	}

	@Override
	public void fileModified (final File rootDir, final File file) throws IOException {
		if (!file.isFile()) return;

		final MediaFormat format = MediaFormat.identify(file);
		if (format == null) return;

		final String newId = this.mediaId.contentId(format.getContentGroup(), file);
		if (this.contentTree.getNode(newId) == null) {
			this.contentTree.removeFile(file);
			if (addFile(rootDir, file)) LOG.info("modified: {}", file.getAbsolutePath());
		}
	}

	@Override
	public void fileGone (final File file) throws IOException {
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

	private boolean addFile (final File rootDir, final File file) throws IOException {
		if (!rootDir.exists()) throw new IllegalArgumentException("Not found: " + rootDir);
		if (!file.isFile()) return false;

		final MediaFormat format = MediaFormat.identify(file);
		if (format == null) return false;
		switch (format.getContentGroup()) {
			case AUDIO:
			case IMAGE:
			case VIDEO:
				putFileToContentTree(rootDir, file, format);
				return true;
			case SUBTITLES:
				return attachSubtitlesToItem(file, format);
			default:
				return false;
		}
	}

	private void putFileToContentTree (final File rootDir, final File file, final MediaFormat format) throws IOException {
		final File dir = file.getParentFile();
		final Container formatContainer;
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

		final Container dirContainer;
		switch (this.hierarchyMode) {
			case PRESERVE:
				dirContainer = makeDirAndParentDirsContianersOnTree(format, formatContainer, rootDir, dir);
				break;
			case FLATTERN:
			default:
				dirContainer = makeDirContainerOnTree(format.getContentGroup(), formatContainer, this.mediaId.contentId(format.getContentGroup(), dir), dir);
		}

		makeItemInContainer(format, dirContainer, file, file.getName());
		synchronized (dirContainer) {
			Collections.sort(dirContainer.getItems(), DIDLObjectOrder.TITLE);
		}
	}

	private Container makeFormatContainerOnTree (final ContentNode parentNode, final ContentGroup group) {
		return makeContainerOnTree(parentNode, group.getId(), group.getHumanName());
	}

	private Container makeDirContainerOnTree (final ContentGroup contentGroup, final Container parentContainer, final String id, final File dir) throws IOException {
		final ContentNode parentNode = this.contentTree.getNode(parentContainer.getId());
		final Container dirContainer = makeContainerOnTree(parentNode, id, dir.getName());
		dirContainer.setCreator(dir.getAbsolutePath());
		synchronized (parentContainer) {
			Collections.sort(parentNode.getContainer().getContainers(), DIDLObjectOrder.CREATOR);
		}

		final Res artRes = findArtRes(dir, contentGroup);
		if (artRes != null) {
			synchronized (dirContainer) {
				dirContainer.addProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(URI.create(artRes.getValue())));
			}
		}

		return dirContainer;
	}

	private Container makeDirAndParentDirsContianersOnTree (final MediaFormat format, final Container formatContainer, final File rootDir, final File dir) throws IOException {
		final List<File> dirsToCreate = new ArrayList<>();

		File ittrDir = dir;
		while (ittrDir != null) {
			final ContentNode node = this.contentTree.getNode(this.mediaId.contentId(format.getContentGroup(), ittrDir));
			if (node != null) break;
			dirsToCreate.add(ittrDir);
			if (rootDir.equals(ittrDir)) break;
			ittrDir = ittrDir.getParentFile();
		}

		Collections.reverse(dirsToCreate);

		for (final File dirToCreate : dirsToCreate) {
			final Container parentContainer;
			if (rootDir.equals(dirToCreate)) {
				parentContainer = formatContainer;
			}
			else {
				final ContentNode parentNode = this.contentTree.getNode(this.mediaId.contentId(format.getContentGroup(), dirToCreate.getParentFile()));
				parentContainer = parentNode.getContainer();
			}
			makeDirContainerOnTree(format.getContentGroup(), parentContainer, this.mediaId.contentId(format.getContentGroup(), dirToCreate), dirToCreate);
		}

		return this.contentTree.getNode(this.mediaId.contentId(format.getContentGroup(), dir)).getContainer();
	}

	/**
	 * If it already exists it will return the existing instance.
	 */
	private Container makeContainerOnTree (final ContentNode parentNode, final String id, final String title) {
		final ContentNode node = this.contentTree.getNode(id);
		if (node != null) return node.getContainer();

		final Container container = new Container();
		container.setClazz(new DIDLObject.Class("object.container"));
		container.setId(id);
		container.setParentID(parentNode.getId());
		container.setTitle(title);
		container.setRestricted(true);
		container.setWriteStatus(WriteStatus.NOT_WRITABLE);
		container.setChildCount(Integer.valueOf(0));

		final Container parentContainer = parentNode.getContainer();
		synchronized (parentContainer) {
			parentContainer.addContainer(container);
			parentContainer.setChildCount(Integer.valueOf(parentContainer.getChildCount().intValue() + 1));
		}
		this.contentTree.addNode(new ContentNode(id, container));

		return container;
	}

	private void makeItemInContainer (final MediaFormat format, final Container parent, final File file, final String title) throws IOException {
		final String id = this.mediaId.contentId(format.getContentGroup(), file);
		if (hasItemWithId(parent, id)) return;

		final Res res = new Res(formatToMime(format), Long.valueOf(file.length()), this.externalHttpContext + "/" + id);
		res.setSize(file.length());

		final Item item;
		switch (format.getContentGroup()) {
			case VIDEO:
				//res.setDuration(formatDuration(durationMillis));
				//res.setResolution(resolutionXbyY);
				item = new VideoItem(id, parent, title, "", res);
				findSubtitlesForItem(item, file);
				this.mediaInfo.readInfoAsync(file, res);
				break;
			case IMAGE:
				//res.setResolution(resolutionXbyY);
				item = new ImageItem(id, parent, title, "", res);
				break;
			case AUDIO:
				//res.setDuration(formatDuration(durationMillis));
				item = new AudioItem(id, parent, title, "", res);
				this.mediaInfo.readInfoAsync(file, res);
				break;
			default:
				throw new IllegalArgumentException();
		}

		findMetadata(file, item);
		findArt(file, format, item);

		synchronized (parent) {
			parent.addItem(item);
			parent.setChildCount(Integer.valueOf(parent.getChildCount().intValue() + 1));
		}
		this.contentTree.addNode(new ContentNode(item.getId(), item, file));
	}

	private static void findMetadata (final File file, final Item item) {
		final Metadata md = MetadataReader.read(file);
		if (md == null) return;
		if (md.getArtist() != null) item.addProperty(new DIDLObject.Property.UPNP.ARTIST(new PersonWithRole(md.getArtist())));
		if (md.getAlbum() != null) item.addProperty(new DIDLObject.Property.UPNP.ALBUM(md.getAlbum()));
	}

	private void findArt (final File mediaFile, final MediaFormat mediaFormat, final Item item) throws IOException {
		final Res artRes = findArtRes(mediaFile, mediaFormat.getContentGroup());
		if (artRes == null) return;

		synchronized (item) {
			item.addProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(URI.create(artRes.getValue())));
			item.addResource(artRes);
		}
	}

	private Res findArtRes (final File mediaFile, final ContentGroup mediaContentGroup) throws IOException {
		final File artFile = CoverArtHelper.findCoverArt(mediaFile);
		if (artFile == null) return null;

		final MediaFormat artFormat = MediaFormat.identify(artFile);
		if (artFormat == null) {
			LOG.warn("Ignoring art file of unsupported type: {}", artFile);
			return null;
		}
		final MimeType artMimeType = formatToMime(artFormat);

		final String artId = this.mediaId.contentId(mediaContentGroup, artFile);
		this.contentTree.addNode(new ContentNode(artId, null, artFile));
		return new Res(makeProtocolInfo(artMimeType), Long.valueOf(artFile.length()), this.externalHttpContext + "/" + artId);
	}

	private DLNAProtocolInfo makeProtocolInfo(final MimeType artMimeType) {
		final EnumMap<DLNAAttribute.Type, DLNAAttribute> attributes = new EnumMap<>(DLNAAttribute.Type.class);

		final DLNAProfiles dlnaThumbnailProfile = findDlnaThumbnailProfile(artMimeType);
		if (dlnaThumbnailProfile != null) {
			attributes.put(DLNAAttribute.Type.DLNA_ORG_PN, new DLNAProfileAttribute(dlnaThumbnailProfile));
		}
		attributes.put(DLNAAttribute.Type.DLNA_ORG_OP, new DLNAOperationsAttribute(DLNAOperations.RANGE));
		attributes.put(DLNAAttribute.Type.DLNA_ORG_CI, new DLNAConversionIndicatorAttribute(DLNAConversionIndicator.TRANSCODED));
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

	private DLNAProfiles findDlnaThumbnailProfile (final MimeType mimeType) {
		return MIME_TYPE_TO_DLNA_THUMBNAIL_TYPE.get(mimeType.toString());
	}

	private boolean attachSubtitlesToItem (final File subtitlesFile, final MediaFormat subtitlesFormat) throws IOException {
		final ContentNode dirNode = this.contentTree.getNode(this.mediaId.contentId(ContentGroup.VIDEO, subtitlesFile.getParentFile()));
		if (dirNode == null) return false;

		boolean attached = false;
		synchronized (dirNode.getContainer()) {
			for (final Item item : dirNode.getContainer().getItems()) {
				final File itemFile = this.contentTree.getNode(item.getId()).getFile();
				if (new BasenameFilter(itemFile).accept(null, subtitlesFile.getName())) {
					if (addSubtitles(item, subtitlesFile, subtitlesFormat)) {
						LOG.info("subtitles for {}: {}", itemFile.getAbsolutePath(), subtitlesFile.getAbsolutePath());
						attached = true;
					}
				}
			}
		}
		return attached;
	}

	private void deattachSubtitles (final File subtitlesFile, final MediaFormat subtitlesFormat) throws IOException {
		final Container dirContainer = this.contentTree.getNode(this.mediaId.contentId(ContentGroup.VIDEO, subtitlesFile.getParentFile())).getContainer();
		if (dirContainer == null) return;

		synchronized (dirContainer) {
			for (final Item item : dirContainer.getItems()) {
				if (removeSubtitles(item, subtitlesFile, subtitlesFormat)) {
					LOG.info("subtitles removed: {}", subtitlesFile.getAbsolutePath());
				}
			}
		}
	}

	private void findSubtitlesForItem (final Item item, final File itemFile) throws IOException {
		final File parentFile = itemFile.getParentFile();
		if (parentFile == null) throw new NullPointerException("itemFile has null parent: " + itemFile);

		final String[] fNames = parentFile.list(new BasenameFilter(itemFile));
		if (fNames == null) {
			LOG.warn("Failed to read directory: {}", parentFile.getAbsolutePath());
			return;
		}

		for (final String fName : fNames) {
			final MediaFormat fFormat = MediaFormat.identify(fName);
			if (fFormat != null && fFormat.getContentGroup() == ContentGroup.SUBTITLES) {
				addSubtitles(item, new File(parentFile, fName), fFormat);
			}
		}
	}

	private boolean addSubtitles (final Item item, final File subtitlesFile, final MediaFormat subtitlesFormat) throws IOException {
		final String subtitlesId = this.mediaId.contentId(subtitlesFormat.getContentGroup(), subtitlesFile);
		final Res subtitlesRes = new Res(formatToMime(subtitlesFormat), Long.valueOf(subtitlesFile.length()), this.externalHttpContext + "/" + subtitlesId);
		this.contentTree.addNode(new ContentNode(subtitlesId, null, subtitlesFile));
		return addResourceToItemIfNotPresent(item, subtitlesRes);
	}

	private boolean removeSubtitles (final Item item, final File subtitlesFile, final MediaFormat subtitlesFormat) throws IOException {
		final String subtitlesId = this.mediaId.contentId(subtitlesFormat.getContentGroup(), subtitlesFile);
		final Res subtitlesRes = new Res(formatToMime(subtitlesFormat), Long.valueOf(subtitlesFile.length()), this.externalHttpContext + "/" + subtitlesId);
		return removeResourceFromItem(item, subtitlesRes);
	}

	private static boolean addResourceToItemIfNotPresent (final Item item, final Res res) {
		if (res.getValue() == null) throw new IllegalArgumentException("Resource value must not be null.");
		synchronized (item) {
			for (final Res r : item.getResources()) {
				if (res.getValue().equals(r.getValue())) return false;
			}
			item.addResource(res);
			return true;
		}
	}

	private static boolean removeResourceFromItem (final Item item, final Res res) {
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

	private static MimeType formatToMime (final MediaFormat format) {
		final String mime = format.getMime();
		return new MimeType(mime.substring(0, mime.indexOf('/')), mime.substring(mime.indexOf('/') + 1));
	}

	private static boolean hasItemWithId (final Container parent, final String id) {
		synchronized (parent) {
			for (final Item item : parent.getItems()) {
				if (id.equals(item.getId())) return true;
			}
		}
		return false;
	}

	private enum DIDLObjectOrder implements Comparator<DIDLObject> {
		TITLE {
			@Override
			public int compare (final DIDLObject a, final DIDLObject b) {
				return a.getTitle().compareToIgnoreCase(b.getTitle());
			}
		},
		ID {
			@Override
			public int compare (final DIDLObject a, final DIDLObject b) {
				return a.getId().compareTo(b.getId());
			}
		},
		CREATOR {
			@Override
			public int compare (final DIDLObject a, final DIDLObject b) {
				final String l = a.getCreator() != null ? a.getCreator() : "";
				final String r = b.getCreator() != null ? b.getCreator() : "";
				return l.compareToIgnoreCase(r);
			}
		};

		@Override
		public abstract int compare (DIDLObject a, DIDLObject b);
	}

	private final class BasenameFilter implements FilenameFilter {

		private final String baseName;

		public BasenameFilter (final File file) {
			this.baseName = FilenameUtils.getBaseName(file.getName());
		}

		@Override
		public boolean accept (final File dir, final String name) {
			return name.startsWith(this.baseName);
		}
	}

}
