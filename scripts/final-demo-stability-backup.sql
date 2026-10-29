-- Run manually in SSMS as an authorized administrator, tcp:127.0.0.1,1433.
-- No database restore/reset, migration, seed or application stop is performed.
USE [UTEExpress_FinalDemo];
SET NOCOUNT ON;
IF DB_NAME() <> N'UTEExpress_FinalDemo' OR @@TRANCOUNT <> 0
    THROW 51000, 'Wrong database or active transaction. Stop.', 1;
IF CONVERT(nvarchar(128),SERVERPROPERTY('MachineName')) <> N'DESKTOP-31GJQ3T'
    OR COALESCE(CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')),N'') <> N'SQLEXPRESS'
    OR COALESCE(CONVERT(int,CONNECTIONPROPERTY('local_tcp_port')),0) <> 1433
    THROW 51000, 'Wrong machine, instance or TCP port. Stop.', 1;
DECLARE @backupDirectory nvarchar(4000)=CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultBackupPath'));
IF @backupDirectory IS NULL OR LEN(@backupDirectory)=0
    THROW 51000, 'Default server backup directory unavailable. Stop.', 1;
IF RIGHT(@backupDirectory,1) <> NCHAR(92) SET @backupDirectory += NCHAR(92);
DECLARE @backupFile nvarchar(4000)=@backupDirectory+N'UTEExpress_FinalDemo_stability_'+CONVERT(nvarchar(36),NEWID())+N'.bak';
BACKUP DATABASE [UTEExpress_FinalDemo] TO DISK=@backupFile
    WITH COPY_ONLY, CHECKSUM, NOFORMAT, NOINIT, STOP_ON_ERROR,
         NAME=N'UTEExpress FinalDemo before stability browser writes';
RESTORE VERIFYONLY FROM DISK=@backupFile WITH CHECKSUM;
SELECT N'PASS COPY_ONLY backup and VERIFYONLY; no restore executed' AS result,
       @backupFile AS backup_file;
