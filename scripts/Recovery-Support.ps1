$ErrorActionPreference='Stop'

function Get-RecoveryContext {
 $root=Split-Path -Parent $PSScriptRoot
 $config=Get-Content -LiteralPath (Join-Path $root '.runtime/local.json') -Raw|ConvertFrom-Json
 if($config.database -notmatch '^[a-z][a-z0-9_]{0,62}$' -or $config.user -notmatch '^[a-z][a-z0-9_]{0,62}$'){throw 'Invalid local database configuration.'}
 if([int]$config.port -lt 1024 -or [int]$config.port -gt 65535){throw 'Invalid local database port.'}
 $bin=Join-Path $root '.tools/postgresql/pgsql/bin'
 foreach($tool in @('psql','pg_dump','pg_restore','createdb','dropdb')){
  if(!(Test-Path -LiteralPath (Join-Path $bin "$tool.exe"))){throw 'Portable PostgreSQL tools missing; follow README.'}
 }
 @{root=$root;config=$config;bin=$bin}
}

function Get-VerifiedBackup([string]$Manifest,[hashtable]$Context){
 $path=[IO.Path]::GetFullPath($Manifest)
 $allowed=[IO.Path]::GetFullPath((Join-Path $Context.root '.runtime/backups'))+[IO.Path]::DirectorySeparatorChar
 if(!$path.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Only project-local backups are accepted by this local restore tool.'}
 if((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Backup links are not accepted.'}
 $manifestData=Get-Content -LiteralPath $path -Raw|ConvertFrom-Json
 if($manifestData.formatVersion -ne 1 -or $manifestData.schema -ne 'public' -or $manifestData.archive -ne 'application.dump' -or $manifestData.sha256 -notmatch '^[A-Fa-f0-9]{64}$'){throw 'Invalid backup manifest.'}
 $archive=Join-Path (Split-Path -Parent $path) 'application.dump'
 if((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $manifestData.sha256){throw 'Backup checksum mismatch; restore refused.'}
 @{path=$path;archive=$archive;metadata=$manifestData}
}

function Get-DatabaseFingerprint([string]$Database,[hashtable]$Context){
 $args=@('-X','-w','-h','127.0.0.1','-p',[string]$Context.config.port,'-U',$Context.config.user,'-d',$Database,'-v','ON_ERROR_STOP=1','-tA')
 $tables=@(& (Join-Path $Context.bin 'psql.exe') @args -c "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename")
 if($LASTEXITCODE -ne 0 -or $tables.Count -lt 8 -or $tables -notcontains 'flyway_schema_history'){throw 'Application schema is unavailable or incomplete.'}
 $fingerprint=[ordered]@{tables=[ordered]@{}}
 foreach($table in $tables){
  if($table -notmatch '^[a-z][a-z0-9_]*$'){throw 'Unexpected application table identifier.'}
  $sql="SELECT json_build_object('rows',count(*),'digest',md5(coalesce(string_agg(h,'' ORDER BY h COLLATE `"C`"),'')))::text FROM (SELECT md5(row_to_json(t)::text) h FROM public.`"$table`" t) x"
  $value=& (Join-Path $Context.bin 'psql.exe') @args -c $sql
  if($LASTEXITCODE -ne 0){throw 'Cannot verify table data.'}
  $fingerprint.tables[$table]=$value|ConvertFrom-Json
 }
 $sequences=& (Join-Path $Context.bin 'psql.exe') @args -c "SELECT coalesce(jsonb_agg(jsonb_build_object('name',sequencename,'value',last_value,'increment',increment_by,'start',start_value) ORDER BY sequencename),'[]')::text FROM pg_sequences WHERE schemaname='public'"
 if($LASTEXITCODE -ne 0){throw 'Cannot verify sequences.'}
 # CHECK expressions can be deparsed into equivalent cast syntax on restore.
 # Compare their structural metadata, then exercise status/role CHECKs separately.
 $constraints=& (Join-Path $Context.bin 'psql.exe') @args -c "SELECT coalesce(jsonb_agg(jsonb_build_object('table',c.conrelid::regclass::text,'name',c.conname,'valid',c.convalidated,'type',c.contype,'columns',c.conkey,'references',c.confrelid::regclass::text,'referenceColumns',c.confkey,'definition',CASE WHEN c.contype='c' THEN NULL ELSE pg_get_constraintdef(c.oid) END) ORDER BY c.conrelid::regclass::text,c.conname),'[]')::text FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='public'"
 if($LASTEXITCODE -ne 0){throw 'Cannot verify constraints.'}
 $fingerprint.sequences=$sequences|ConvertFrom-Json
 $fingerprint.constraints=$constraints|ConvertFrom-Json
 $fingerprint|ConvertTo-Json -Depth 12 -Compress
}
