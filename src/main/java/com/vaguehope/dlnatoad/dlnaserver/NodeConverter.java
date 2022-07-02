package com.vaguehope.dlnatoad.dlnaserver;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.fourthline.cling.model.ModelUtil;
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

import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ExternalUrls;
import com.vaguehope.dlnatoad.media.MetadataReader.Metadata;

/**
 * TODO add caching like Morrigan ContentAdaptor has?
 */
public class NodeConverter {

	private final ExternalUrls externalUrls;

	public NodeConverter(final ExternalUrls externalUrls) {
		this.externalUrls = externalUrls;
	}

	public List<Container> makeSubContainersWithoutTheirSubContainers(final ContentNode n) {
		final List<Container> ret = new ArrayList<>();
		n.withEachNode(i -> ret.add(makeContainerWithoutSubContainers(i)));
		return ret;
	}

	public Container makeContainerWithoutSubContainers(final ContentNode n) {
		final Container c = new Container();
		c.setClazz(new DIDLObject.Class("object.container"));
		c.setId(n.getId());
		c.setParentID(n.getParentId());
		c.setTitle(n.getTitle());
		c.setChildCount(Integer.valueOf(n.getNodeAndItemCount()));
		c.setRestricted(true);
		c.setWriteStatus(WriteStatus.NOT_WRITABLE);
		c.setSearchable(true);

		final ContentItem art = n.getArt();
		if (art != null) {
			final String artUri = this.externalUrls.contentUrl(art.getId());
			c.addProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(URI.create(artUri)));
		}

		return c;
	}

	public List<Item> makeItems(final ContentNode n) {
		final List<Item> ret = new ArrayList<>();
		n.withEachItem(i -> ret.add(makeItem(i)));
		return ret;
	}

	public List<Item> makeItems(final List<ContentItem> items) {
		final List<Item> ret = new ArrayList<>();
		for (final ContentItem i : items) {
			ret.add(makeItem(i));
		}
		return ret;
	}

	public Item makeItem(final ContentItem c) {
		final Res res = new Res(c.getFormat().asMimetype(), Long.valueOf(c.getFileLength()), this.externalUrls.contentUrl(c.getId()));

		final long durationSeconds = TimeUnit.MILLISECONDS.toSeconds(c.getDurationMillis());
		if (durationSeconds > 0) {
			res.setDuration(ModelUtil.toTimeString(durationSeconds));
		}

		final Item i;
		switch (c.getFormat().getContentGroup()) {
		case VIDEO:
			i = new VideoItem(c.getId(), c.getParentId(), c.getTitle(), "", res);
			break;
		case IMAGE:
			i = new ImageItem(c.getId(), c.getParentId(), c.getTitle(), "", res);
			break;
		case AUDIO:
			i = new AudioItem(c.getId(), c.getParentId(), c.getTitle(), "", res);
			break;
		default:
			throw new IllegalArgumentException();
		}

		final ContentItem art = c.getArt();
		if (art != null) {
			final String artUri = this.externalUrls.contentUrl(art.getId());
			i.addProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(URI.create(artUri)));
			i.addResource(makeArtRes(art, artUri));
		}

		if (c.hasAttachments()) {  // TODO is this premature optimisation?
			c.withEachAttachment(a -> {
				i.addResource(new Res(a.getFormat().asMimetype(), Long.valueOf(a.getFileLength()), this.externalUrls.contentUrl(a.getId())));
			});
		}

		final Metadata md = c.getMetadata();
		if (md != null) {
			if (md.getArtist() != null) i.addProperty(new DIDLObject.Property.UPNP.ARTIST(new PersonWithRole(md.getArtist())));
			if (md.getAlbum() != null) i.addProperty(new DIDLObject.Property.UPNP.ALBUM(md.getAlbum()));
		}

		return i;
	}

	private static Res makeArtRes(final ContentItem art, final String artUri) {
		return new Res(makeProtocolInfo(art.getFormat().asMimetype()), Long.valueOf(art.getFileLength()), artUri);
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

}
