ALTER TABLE users ADD COLUMN role_id INT;

UPDATE users
SET role_id = (
    SELECT MIN(user_roles.role_id)
    FROM user_roles
    WHERE user_roles.user_id = users.id
);

ALTER TABLE users ALTER COLUMN role_id SET NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT fk_users_role
    FOREIGN KEY (role_id) REFERENCES roles(id);

DROP TABLE user_roles;
