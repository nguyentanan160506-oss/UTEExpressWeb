-- Run manually in SSMS as an administrator, connected to tcp:127.0.0.1,1433.
-- This script creates only a NEW UTEExpress_FinalDemo. It does not create logins,
-- alter existing databases, migrate, seed, clean, repair or reset anything.
USE [master];
SET NOCOUNT ON;
IF @@TRANCOUNT <> 0 THROW 51000, 'Run outside a transaction.', 1;
IF CONVERT(nvarchar(128), SERVERPROPERTY('MachineName')) <> N'DESKTOP-31GJQ3T'
    THROW 51000, 'Unexpected SQL Server machine. Stop.', 1;
IF COALESCE(CONVERT(nvarchar(128), SERVERPROPERTY('InstanceName')), N'') <> N'SQLEXPRESS'
    THROW 51000, 'Unexpected SQL Server instance. Stop.', 1;
IF COALESCE(CONVERT(int, CONNECTIONPROPERTY('local_tcp_port')), 0) <> 1433
    THROW 51000, 'Connect explicitly over TCP port 1433. Stop.', 1;
IF DB_ID(N'UTEExpress_FinalDemo') IS NOT NULL
    THROW 51000, 'UTEExpress_FinalDemo already exists. Inspect; do not recreate or reuse automatically.', 1;
IF HAS_PERMS_BY_NAME(NULL, NULL, 'CREATE ANY DATABASE') <> 1
    THROW 51000, 'Database creation permission required.', 1;
EXEC(N'CREATE DATABASE [UTEExpress_FinalDemo]');
SELECT name, state_desc, create_date FROM sys.databases WHERE name = N'UTEExpress_FinalDemo';
