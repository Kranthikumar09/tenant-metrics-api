CREATE TABLE tenants (
    tenant_id VARCHAR(128) PRIMARY KEY,
    display_name VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT tenants_id_not_blank CHECK (BTRIM(tenant_id) <> '')
);

CREATE TABLE platform_users (
    user_id UUID PRIMARY KEY,
    oidc_issuer VARCHAR(512) NOT NULL,
    oidc_subject VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT platform_users_identity_unique UNIQUE (oidc_issuer, oidc_subject),
    CONSTRAINT platform_users_issuer_not_blank CHECK (BTRIM(oidc_issuer) <> ''),
    CONSTRAINT platform_users_subject_not_blank CHECK (BTRIM(oidc_subject) <> '')
);

CREATE TABLE tenant_memberships (
    user_id UUID NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    role VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, tenant_id),
    CONSTRAINT tenant_memberships_user_fk FOREIGN KEY (user_id)
        REFERENCES platform_users (user_id) ON DELETE CASCADE,
    CONSTRAINT tenant_memberships_tenant_fk FOREIGN KEY (tenant_id)
        REFERENCES tenants (tenant_id) ON DELETE RESTRICT,
    CONSTRAINT tenant_memberships_role_not_blank CHECK (BTRIM(role) <> '')
);
