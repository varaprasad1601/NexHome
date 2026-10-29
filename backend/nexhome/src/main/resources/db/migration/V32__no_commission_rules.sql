-- Commission rules are not used for now (V31): the platform's commission is the service's own
-- (nexhome_services.platform_commission_type / _value). Their table and function go; payouts keep the commission they
-- were worked out with.
DELETE FROM nexhome_role_functions
WHERE function_id IN (SELECT id FROM nexhome_functions WHERE function_code = 'COMMISSION_MANAGE');

DELETE FROM nexhome_functions WHERE function_code = 'COMMISSION_MANAGE';

DROP TABLE nexhome_commission_rules;
