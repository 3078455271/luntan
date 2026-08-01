#!/bin/bash
set -e

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    CREATE USER IF NOT EXISTS 'identity_app'@'%' IDENTIFIED BY '${IDENTITY_DB_PASSWORD}';
    ALTER USER 'identity_app'@'%' IDENTIFIED BY '${IDENTITY_DB_PASSWORD}';
    CREATE USER IF NOT EXISTS 'forum_app'@'%' IDENTIFIED BY '${FORUM_DB_PASSWORD}';
    ALTER USER 'forum_app'@'%' IDENTIFIED BY '${FORUM_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON identity_db.* TO 'identity_app'@'%';
    GRANT ALL PRIVILEGES ON forum_db.* TO 'forum_app'@'%';
    FLUSH PRIVILEGES;
EOSQL