-- The POLICY_KEY policies (kind POLICY, V57) are privacy policies: their names say so. Codes unchanged.
UPDATE nexhome_lookup_values lv
SET display_name = v.display_name, updated_date = now()
FROM nexhome_lookup_types lt,
     (VALUES ('CUSTOMER_LOGIN_POLICY', 'Customer Login Privacy Policy'),
             ('PARTNER_LOGIN_POLICY', 'Partner Login Privacy Policy'),
             ('CUSTOMER_REGISTRATION_POLICY', 'Customer Registration Privacy Policy'),
             ('PARTNER_REGISTRATION_POLICY', 'Partner Registration Privacy Policy'),
             ('CUSTOMER_APP_POLICY', 'Customer App Privacy Policy'),
             ('PARTNER_APP_POLICY', 'Partner App Privacy Policy')) AS v (value_code, display_name)
WHERE lv.lookup_type_id = lt.id AND lt.type_code = 'POLICY_KEY' AND lv.value_code = v.value_code;
