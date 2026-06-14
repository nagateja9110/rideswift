-- Give the seeded demo accounts realistic Indian (+91) numbers so the in-app
-- "Call" (tel:) link shows a sensible Chennai-style number. Matches DataSeeder
-- so fresh installs and migrated installs agree. OTP-created users are untouched.
UPDATE users SET phone = '+919900000001' WHERE email = 'admin@rideswift.io';
UPDATE users SET phone = '+919900000010' WHERE email = 'alice@rideswift.io';
UPDATE users SET phone = '+919900000011' WHERE email = 'bob@rideswift.io';
UPDATE users SET phone = '+919900000012' WHERE email = 'carol@rideswift.io';
UPDATE users SET phone = '+919900000013' WHERE email = 'dave@rideswift.io';
UPDATE users SET phone = '+919900000014' WHERE email = 'eve@rideswift.io';
UPDATE users SET phone = '+919900001000' WHERE email = 'mike@rideswift.io';
UPDATE users SET phone = '+919900001001' WHERE email = 'driver2@rideswift.io';
UPDATE users SET phone = '+919900001002' WHERE email = 'driver3@rideswift.io';
UPDATE users SET phone = '+919900001003' WHERE email = 'driver4@rideswift.io';
UPDATE users SET phone = '+919900001004' WHERE email = 'driver5@rideswift.io';
UPDATE users SET phone = '+919900001005' WHERE email = 'driver6@rideswift.io';
UPDATE users SET phone = '+919900001006' WHERE email = 'driver7@rideswift.io';
UPDATE users SET phone = '+919900001007' WHERE email = 'driver8@rideswift.io';
UPDATE users SET phone = '+919900001008' WHERE email = 'driver9@rideswift.io';
UPDATE users SET phone = '+919900001009' WHERE email = 'driver10@rideswift.io';
UPDATE users SET phone = '+919900009999' WHERE email = 'pending@rideswift.io';
