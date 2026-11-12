-- Notification priorities become HIGH, MEDIUM or LOW (V45 had NORMAL or HIGH): NORMAL becomes MEDIUM (the default);
-- staff choose one when sending. The staff alert settings (OFF / NORMAL / HIGH, V49) are a separate thing and stay.
ALTER TABLE nexhome_notifications
	DROP CONSTRAINT ck_nexhome_notifications_priority;

UPDATE nexhome_notifications
SET priority = 'MEDIUM'
WHERE priority = 'NORMAL';

ALTER TABLE nexhome_notifications
	ALTER COLUMN priority SET DEFAULT 'MEDIUM',
	ADD CONSTRAINT ck_nexhome_notifications_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW'));
