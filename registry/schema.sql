-- Run in a dedicated PostgreSQL database with a least-privilege service role.
CREATE TABLE IF NOT EXISTS mod_releases (
  id BIGSERIAL PRIMARY KEY,
  mod_id TEXT NOT NULL CHECK (mod_id ~ '^[a-z][a-z0-9_]{2,80}$'),
  display_name TEXT NOT NULL,
  version TEXT NOT NULL,
  release_tag TEXT NOT NULL,
  channel TEXT NOT NULL CHECK (channel IN ('nightly','stable')),
  minecraft TEXT NOT NULL,
  minimum_loader TEXT NOT NULL,
  required BOOLEAN NOT NULL DEFAULT FALSE,
  depends TEXT[] NOT NULL DEFAULT '{}',
  description TEXT NOT NULL DEFAULT '',
  sha256 CHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
  asset_name TEXT NOT NULL,
  size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
  source_url TEXT NOT NULL,
  approved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(mod_id, channel, release_tag)
);
CREATE INDEX IF NOT EXISTS mod_releases_channel_latest
  ON mod_releases(channel, mod_id, approved_at DESC);
CREATE INDEX IF NOT EXISTS mod_releases_hash ON mod_releases(sha256);
