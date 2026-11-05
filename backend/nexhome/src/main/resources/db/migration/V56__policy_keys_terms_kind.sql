-- POLICY_KEY values say what kind of policy they are: TERMS (terms & conditions) for the six seeded in V55, so the
-- apps show terms on their own and list every other published policy under Policies.
UPDATE nexhome_lookup_values lv
SET metadata = lv.metadata || '{"kind": "TERMS"}'::jsonb, updated_date = now()
FROM nexhome_lookup_types lt
WHERE lv.lookup_type_id = lt.id AND lt.type_code = 'POLICY_KEY'
  AND lv.value_code IN ('CUSTOMER_LOGIN_TERMS', 'PARTNER_LOGIN_TERMS', 'CUSTOMER_REGISTRATION_TERMS',
                        'PARTNER_REGISTRATION_TERMS', 'CUSTOMER_APP_TERMS', 'PARTNER_APP_TERMS');
