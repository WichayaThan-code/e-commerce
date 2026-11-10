-- V58: Social login identities (Google / Facebook; APPLE kept in CHECK for forward compatibility) linked to users.

CREATE TABLE oauth_identities (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider character varying(20) NOT NULL,
    provider_subject character varying(255) NOT NULL,
    email_at_link character varying(255),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pk_oauth_identities PRIMARY KEY (id),
    CONSTRAINT ck_oauth_identities_provider CHECK (provider IN ('GOOGLE', 'FACEBOOK', 'APPLE')),
    CONSTRAINT uq_oauth_identities_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uq_oauth_identities_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX ix_oauth_identities_user_id ON oauth_identities (user_id);

COMMENT ON TABLE oauth_identities IS '[Auth] Linked social providers per user; one row per (user, provider)';
COMMENT ON COLUMN oauth_identities.provider_subject IS 'Stable subject (sub / Facebook user id) from the IdP';
COMMENT ON COLUMN oauth_identities.email_at_link IS 'Email claimed by the provider when the link was created (audit only)';
