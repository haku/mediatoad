package mediatoad.media;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mediatoad.auth.Users;
import mediatoad.db.MediaDb;
import mediatoad.db.Tag;
import mediatoad.util.ExceptionHelper;

public class AuthTagsUpdater implements Consumer<Collection<String>> {

	private static final Logger LOG = LoggerFactory.getLogger(AuthTagsUpdater.class);

	private final MediaDb mediaDb;
	private final ContentTree contentTree;
	private final ScheduledExecutorService dbEx;
	private final Set<String> allAuthTags;

	public AuthTagsUpdater(final Users users, final MediaDb mediaDb, final ContentTree contentTree, final ScheduledExecutorService dbEx) {
		this.mediaDb = mediaDb;
		this.contentTree = contentTree;
		this.dbEx = dbEx;
		this.allAuthTags = users.allAuthTags();

		mediaDb.setTagChangeListener(this);
	}

	@Override
	public void accept(final Collection<String> fileIds) {
		for (final String fileId : fileIds) {
			final ContentItem item = this.contentTree.getItem(fileId);
			if (item == null) {
				LOG.error("Got tag change event for item not in contentTree: {}", fileId);
				continue;
			}
			updateItemAsync(item);
		}
	}

	public void updateItemAsync(final ContentItem item) {
		if (this.mediaDb == null || this.allAuthTags.size() < 1) return;
		this.dbEx.execute(() -> {
			try {
				doUpdate(item);
			}
			catch (final Exception e) {
				LOG.error("Failed to update auth-tags for item {}: {}", item.getId(), ExceptionHelper.causeTrace(e));
			}
		});
	}

	private void doUpdate(final ContentItem item) throws SQLException {
		final Collection<Tag> tags = this.mediaDb.getTags(item.getId(), false, false);
		final Set<String> authTags = tags.stream()
				.map(AuthTagsUpdater::authTagMap)
				.filter(this::authTagFilter)
				.collect(Collectors.toUnmodifiableSet());
		item.setAuthTags(authTags);
	}

	private static String authTagMap(final Tag t) {
		return t.getTag();
	}

	private boolean authTagFilter(final String t) {
		return this.allAuthTags.contains(t);
	}

}
