CREATE TABLE platform_bootstrap (
	id SMALLINT PRIMARY KEY,
	initialized_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO platform_bootstrap (id) VALUES (1);
