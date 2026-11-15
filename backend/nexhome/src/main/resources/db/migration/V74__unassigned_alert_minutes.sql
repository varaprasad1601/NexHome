-- The no-partner warning (general setting) is set in minutes instead of hours: UNASSIGNED_ALERT_HOURS becomes
-- UNASSIGNED_ALERT_MINUTES (default 120, 5 to 2880; NexHomeSettingsServiceImpl.DEFINITIONS). A value changed from the
-- default is carried over in minutes; one that is not a whole number is dropped (the default applies).
UPDATE nexhome_app_settings
SET setting_key = 'UNASSIGNED_ALERT_MINUTES',
	setting_value = (setting_value::INT * 60)::TEXT
WHERE setting_key = 'UNASSIGNED_ALERT_HOURS'
	AND setting_value ~ '^\s*[0-9]{1,4}\s*$';

DELETE FROM nexhome_app_settings WHERE setting_key = 'UNASSIGNED_ALERT_HOURS';
