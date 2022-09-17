BEGIN TRANSACTION;

CREATE TABLE new_tags (
  file_id STRING NOT NULL,
  tag STRING NOT NULL COLLATE NOCASE,
  cls STRING NOT NULL COLLATE NOCASE DEFAULT '',
  modified INT NOT NULL,
  deleted INT(1) NOT NULL DEFAULT 0,
  UNIQUE(file_id, tag, cls));

INSERT INTO new_tags (file_id, tag, cls, modified, deleted)
  SELECT file_id, tag, cls, modified, deleted from tags;

DROP TABLE tags;
ALTER TABLE new_tags RENAME TO tags;

COMMIT;
