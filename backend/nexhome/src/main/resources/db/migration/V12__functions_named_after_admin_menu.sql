-- The seeded functions take the admin console's menu names: the module is the sidebar group and the name its
-- "Manage ..." item(s), so the Functions and role permission screens read like the menu. The codes stay as they are:
-- the backend checks them (@PreAuthorize). Functions created through the API are not touched.
UPDATE nexhome_functions f
SET function_module = v.function_module,
    function_name   = v.function_name,
    updated_by      = 1,
    updated_date    = now()
FROM (VALUES ('STAFF_MANAGE', 'USER_MANAGEMENT', 'Manage Admins & Sub Admins'),
             ('USER_MANAGE', 'USER_MANAGEMENT', 'Manage Customers & Partners'),
             ('PROFILE_MANAGE', 'USER_MANAGEMENT', 'Manage Customer Profiles'),
             ('PARTNER_ONBOARDING_MANAGE', 'USER_MANAGEMENT', 'Manage Partner Onboarding'),
             ('CATALOG_MANAGE', 'SERVICE_MANAGEMENT', 'Manage Categories & Services'),
             ('ROLE_MANAGE', 'SYSTEM_CONFIGURATION', 'Manage Roles'),
             ('FUNCTION_MANAGE', 'SYSTEM_CONFIGURATION', 'Manage Functions'),
             ('LOOKUP_MANAGE', 'SYSTEM_CONFIGURATION', 'Manage Lookups'),
             ('POLICY_MANAGE', 'SETTINGS', 'Manage Policies'),
             ('FAQ_MANAGE', 'SETTINGS', 'Manage FAQs')) AS v (function_code, function_module, function_name)
WHERE f.function_code = v.function_code
  AND f.created_by = 1;
