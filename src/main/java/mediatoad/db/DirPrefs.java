package mediatoad.db;

import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;

public class DirPrefs {

	public static final String PREF_KEY_FAVOURITE = "favourite";
	public static final String PREF_KEY_SORT_MODIFIED = "sort_by_modified";
	public static final String PREF_KEY_VIDEO_THUMBS = "video_thumbs";

	public static final DirPrefs DEFAULT = new DirPrefs(false, false, false);

	public static DirPrefs read(final MediaDb db, final String nodeId) throws SQLException {
		if (db == null || nodeId == null) return DEFAULT;
		final Map<String, String> raw = db.getNodePrefs(nodeId);
		if (raw.size() < 1) return DEFAULT;
		return new DirPrefs(raw);
	}

	public final boolean favourite;
	public final boolean sortModified;
	public final boolean videoThumbs;

	private DirPrefs(final Map<String, String> dirPrefs) {
		this.favourite = Boolean.parseBoolean(dirPrefs.get(PREF_KEY_FAVOURITE));
		this.sortModified = Boolean.parseBoolean(dirPrefs.get(PREF_KEY_SORT_MODIFIED));
		this.videoThumbs = Boolean.parseBoolean(dirPrefs.get(PREF_KEY_VIDEO_THUMBS));
	}

	protected DirPrefs(final boolean favourite, final boolean sortModified, final boolean videoThumbs) {
		this.favourite = favourite;
		this.sortModified = sortModified;
		this.videoThumbs = videoThumbs;
	}

	@Override
	public String toString() {
		return String.format("DirPrefs{%s, %s, %s}",
				this.favourite, this.sortModified, this.videoThumbs);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.favourite, this.sortModified, this.videoThumbs);
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == null) return false;
		if (this == obj) return true;
		if (!(obj instanceof DirPrefs)) return false;
		final DirPrefs that = (DirPrefs) obj;
		return this.favourite == that.favourite
				&& this.sortModified == that.sortModified
				&& this.videoThumbs == that.videoThumbs;
	}

}
