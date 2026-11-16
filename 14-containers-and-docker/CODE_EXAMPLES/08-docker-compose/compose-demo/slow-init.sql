-- Stands in for a database that takes a while to become ready the first time:
-- a seed-data import, a big migration, or just a slow CI machine.
-- Scripts in /docker-entrypoint-initdb.d run while Postgres only listens on a Unix socket,
-- so for these 10 seconds nothing can connect over the network.
SELECT pg_sleep(10);
