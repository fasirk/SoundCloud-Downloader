DROP TABLE IF EXISTS playlists;
CREATE TABLE playlists(
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	title VARCHAR(50) NOT NULL,
	soundcloud_url TEXT NOT NULL,
	artwork_url TEXT DEFAULT NULL,
	created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DROP TABLE IF EXISTS tracks;
CREATE TABLE tracks(
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	title TEXT NOT NULL,
	soundcloud_url TEXT NOT NULL,
	artwork_url TEXT DEFAULT NULL,
	playlist_id INTEGER DEFAULT NULL,
	download_id INTEGER DEFAULT NULL,
	created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON UPDATE CASCADE ON DELETE CASCADE
);