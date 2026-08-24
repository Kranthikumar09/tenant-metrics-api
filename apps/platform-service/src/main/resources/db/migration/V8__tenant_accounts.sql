CREATE TABLE accounts (
	tenant_id VARCHAR(128) NOT NULL,
	account_external_id VARCHAR(128) NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (tenant_id, account_external_id),
	CONSTRAINT accounts_tenant_not_blank CHECK (BTRIM(tenant_id) <> ''),
	CONSTRAINT accounts_external_id_not_blank CHECK (BTRIM(account_external_id) <> '')
);
