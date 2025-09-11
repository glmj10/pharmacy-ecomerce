INSERT INTO roles (name, code, description)
SELECT * FROM (
                  SELECT 'Admin' AS name, 'ADMIN' AS code, 'Administrator with full system access' AS description
              ) AS tmp
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE code = 'ADMIN'
);

INSERT INTO roles (name, code, description)
SELECT * FROM (
                  SELECT 'User' AS name, 'USER' AS code, 'Regular user with basic permissions' AS description
              ) AS tmp
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE code = 'USER'
);

INSERT INTO roles (name, code, description)
SELECT * FROM (
                  SELECT 'Staff' AS name, 'STAFF' AS code, 'Staff member with elevated permissions' AS description
              ) AS tmp
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE code = 'STAFF'
);
