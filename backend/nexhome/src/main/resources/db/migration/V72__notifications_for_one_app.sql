-- Staff notes sent to one user were stored for ALL apps, so a number that is a customer, a partner and staff saw them in
-- every app. From now on a note to one user names its app (NexHomeNotificationSendRequest.app). Existing ALL rows go to
-- the recipient's only app where the account has just one (active role of an active role); accounts with several keep
-- ALL, since which one was meant is not known.
WITH apps AS (
	SELECT ur.user_id,
		CASE
			WHEN r.code = 'CUSTOMER' THEN 'CUSTOMER'
			WHEN r.code = 'PARTNER' THEN 'PARTNER'
			WHEN r.code IN ('SUPER_USER', 'ADMIN', 'SUB_ADMIN') THEN 'STAFF'
		END AS app
	FROM nexhome_user_roles ur
	JOIN nexhome_roles r ON r.id = ur.role_id
	WHERE ur.is_active AND r.active_flag
),
single AS (
	SELECT user_id, MIN(app) AS app
	FROM apps
	WHERE app IS NOT NULL
	GROUP BY user_id
	HAVING COUNT(DISTINCT app) = 1
)
UPDATE nexhome_notifications n
SET audience = single.app
FROM single
WHERE n.user_id = single.user_id
	AND n.audience = 'ALL';
