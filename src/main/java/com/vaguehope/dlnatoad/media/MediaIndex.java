package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teleal.cling.support.model.DIDLObject;
import org.teleal.cling.support.model.Res;
import org.teleal.cling.support.model.WriteStatus;
import org.teleal.cling.support.model.container.Container;
import org.teleal.cling.support.model.item.ImageItem;
import org.teleal.cling.support.model.item.Item;
import org.teleal.cling.support.model.item.VideoItem;
import org.teleal.common.util.MimeType;

import com.vaguehope.dlnatoad.dlnaserver.ContentNode;
import com.vaguehope.dlnatoad.dlnaserver.ContentTree;
import com.vaguehope.dlnatoad.util.HashHelper;
import com.vaguehope.dlnatoad.util.TreeWalker;
import com.vaguehope.dlnatoad.util.TreeWalker.Hiker;

public class MediaIndex {

	private static final Logger LOG = LoggerFactory.getLogger(MediaIndex.class);

	private final List<File> baseDirs;
	private final ContentTree contentTree;
	private final String externalHttpContext;

	private final Container videoContainer;
	private final Container imageContainer;

	public MediaIndex (final List<File> dirs, final ContentTree contentTree, final String externalHttpContext) {
		this.baseDirs = dirs;
		this.contentTree = contentTree;
		this.externalHttpContext = externalHttpContext;

		this.videoContainer = makeContainerOnTree(contentTree.getRootNode(), ContentTree.VIDEO_ID, "Videos");
		this.imageContainer = makeContainerOnTree(contentTree.getRootNode(), ContentTree.IMAGE_ID, "Images");
	}

	public void refresh () throws IOException {
		this.contentTree.prune();
		new TreeWalker(this.baseDirs, MediaFormat.FILE_FILTER, new Hiker() {
			@Override
			public void onDirWithFiles (final File dir, final List<File> files) {
				putDirToContentTree(dir, files);
			}
		}).walk();
		LOG.info("refreshed: {} items.", String.valueOf(this.contentTree.itemCount()));
	}

	protected void putDirToContentTree (final File dir, final List<File> files) {
		Container vidContainer = null;
		Container imgContainer = null;
		for (final File file : files) {
			final MediaFormat format = MediaFormat.identify(file);
			switch (format.getType()) {
				case VIDEO:
					if (vidContainer == null) vidContainer = makeContainerOnTree(this.videoContainer, contentId(format.getType(), dir), dir.getName());
					makeVideoItemInContainer(vidContainer, file, file.getName(), format);
					break;
				case IMAGE:
					if (imgContainer == null) imgContainer = makeContainerOnTree(this.imageContainer, contentId(format.getType(), dir), dir.getName());
					makeImageItemInContainer(imgContainer, file, file.getName(), format);
					break;
				default:
					throw new IllegalStateException();
			}
		}
		LOG.info("shared: {} ({}v, {}i)", dir.getName(),
				vidContainer == null ? 0 : vidContainer.getChildCount(),
				imgContainer == null ? 0 : imgContainer.getChildCount());
	}

	private Container makeContainerOnTree (final Container parentContainer, final String id, final String title) {
		return makeContainerOnTree(this.contentTree.getNode(parentContainer.getId()), id, title);
	}

	/**
	 * If it already exists it will be reset.
	 */
	private Container makeContainerOnTree (final ContentNode parentNode, final String id, final String title) {
		ContentNode node = this.contentTree.getNode(id);
		if (node != null) {
			Container container = node.getContainer();
			container.setContainers(new ArrayList<Container>());
			container.setItems(new ArrayList<Item>());
			container.setChildCount(Integer.valueOf(0));
			return container;
		}

		final Container container = new Container();
		container.setClazz(new DIDLObject.Class("object.container"));
		container.setId(id);
		container.setParentID(parentNode.getId());
		container.setTitle(title);
		container.setRestricted(true);
		container.setWriteStatus(WriteStatus.NOT_WRITABLE);
		container.setChildCount(Integer.valueOf(0));

		final Container parentContainer = parentNode.getContainer();
		parentContainer.addContainer(container);
		parentContainer.setChildCount(Integer.valueOf(parentContainer.getChildCount().intValue() + 1));
		this.contentTree.addNode(new ContentNode(id, container));

		return container;

	}

	private void makeVideoItemInContainer (final Container parent, final File file, final String title, final MediaFormat format) {
		final String id = contentId(format.getType(), file);
		final String mime = format.getMime();
		final MimeType extMimeType = new MimeType(mime.substring(0, mime.indexOf('/')), mime.substring(mime.indexOf('/') + 1));
		final Res res = new Res(extMimeType, Long.valueOf(file.length()), this.externalHttpContext + "/" + id);
		//res.setDuration(formatDuration(durationMillis));
		//res.setResolution(resolutionXbyY);
		final VideoItem videoItem = new VideoItem(id, parent, title, "", res);

		parent.addItem(videoItem);
		parent.setChildCount(Integer.valueOf(parent.getChildCount().intValue() + 1));
		this.contentTree.addNode(new ContentNode(videoItem.getId(), videoItem, file));
	}

	private void makeImageItemInContainer (final Container parent, final File file, final String title, final MediaFormat format) {
		final String id = contentId(format.getType(), file);
		final String mime = format.getMime();
		final MimeType extMimeType = new MimeType(mime.substring(0, mime.indexOf('/')), mime.substring(mime.indexOf('/') + 1));
		final Res res = new Res(extMimeType, Long.valueOf(file.length()), this.externalHttpContext + "/" + id);
		//res.setResolution(resolutionXbyY);
		final ImageItem imageItem = new ImageItem(id, parent, title, "", res);

		parent.addItem(imageItem);
		parent.setChildCount(Integer.valueOf(parent.getChildCount().intValue() + 1));
		this.contentTree.addNode(new ContentNode(imageItem.getId(), imageItem, file));
	}

	private static String contentId (MediaType type, final File file) {
		switch (type) {
			case VIDEO:
				return ContentTree.VIDEO_PREFIX + HashHelper.sha1(file.getAbsolutePath()) + "-" + getSafeName(file);
			case IMAGE:
				return ContentTree.IMAGE_PREFIX + HashHelper.sha1(file.getAbsolutePath()) + "-" + getSafeName(file);
			default:
				throw new IllegalStateException();
		}
	}

	private static String getSafeName (final File file) {
		return file.getName().replaceAll("[^a-zA-Z0-9]", "_");
	}

}
