ALTER TABLE support.attachments
ALTER COLUMN owner TYPE character varying(40);

ALTER TABLE support.comments
ALTER COLUMN owner TYPE character varying(40);

ALTER TABLE support.tickets
ALTER COLUMN owner TYPE character varying(40);

ALTER TABLE support.bug_tracker_issues
ALTER COLUMN owner TYPE character varying(40);

ALTER TABLE support.members
ALTER COLUMN user_id TYPE character varying(40);

ALTER TABLE support.users
ALTER COLUMN id TYPE character varying(40);

ALTER TABLE support.tickets
ADD COLUMN event_count SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE support.tickets
ADD COLUMN locale character varying(36) DEFAULT 'fr';
