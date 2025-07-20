package com.vaguehope.dlnatoad.media;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import com.vaguehope.dlnatoad.auth.AuthList;

public class RecentContentNode extends ContentNode {

	private static final int MAX_RECENT_ITEMS = 200;

	private final ConcurrentSkipListSet<ContentItem> recent;
	private volatile long oldestRecentItem = 0L;

	private final Map<String, AuthList> itemIdToAuth = new ConcurrentHashMap<>();

	public RecentContentNode() {
		super(
				ContentGroup.RECENT.getId(),
				ContentGroup.ROOT.getId(),
				ContentGroup.RECENT.getHumanName(),
				null, null, null, null,
				new ConcurrentSkipListSet<>(ContentItem.Order.MODIFIED_DESC));
		this.recent = (ConcurrentSkipListSet<ContentItem>) this.items;
	}

	@Override
	public List<ContentItem> itemsUserHasAuth(final String username) {
		synchronized (this.recent) {
			final List<ContentItem> ret = new ArrayList<>(this.recent.size());
			for (final ContentItem item : this.recent) {
				final AuthList auth = this.itemIdToAuth.get(item.getId());
				if (auth == null || auth.hasUser(username)) ret.add(item);
			}
			return ret;
		}
	}

	public void maybeAddToRecent(final ContentItem item, ContentNode node) {
		if (item.getParentId() == null) return;  // Things like subtitles and thumbnails.
		if (item.getLastModified() < this.oldestRecentItem) return;

		final AuthList auth = node.getAuthList();
		if (auth != null) this.itemIdToAuth.put(item.getId(), auth);

		synchronized (this.recent) {
			this.recent.add(item);
			if (this.recent.size() > MAX_RECENT_ITEMS) {
				final ContentItem removed = this.recent.pollLast();
				this.itemIdToAuth.remove(removed.getId());

				this.oldestRecentItem = this.recent.last().getLastModified();
			}
			else {
				this.oldestRecentItem = 0L;
			}
		}
	}

	public void removeFromRecent(final ContentItem item) {
		synchronized (this.recent) {
			this.recent.remove(item);
			this.itemIdToAuth.remove(item.getId());
		}
	}

}
