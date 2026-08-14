using System.Text.Json;
using Microsoft.Data.Sqlite;
using FlowerWhisp.Core;

namespace FlowerWhisp.Infrastructure;

/// <summary>Transactional, user-local persistence seam. The file format is versioned and can be migrated to SQLite without changing contracts.</summary>
public sealed class JsonDictationRepository : IDictationRepository
{
    private readonly string _path; private readonly SemaphoreSlim _gate = new(1, 1);
    public JsonDictationRepository(string path) => _path = path;
    public async Task SaveAsync(DictationRecord record, CancellationToken cancellationToken = default)
    {
        await _gate.WaitAsync(cancellationToken); try { var rows = await ReadAsync(cancellationToken); rows.RemoveAll(x => x.Id == record.Id); rows.Add(record); await WriteAsync(rows, cancellationToken); } finally { _gate.Release(); }
    }
    public async Task<IReadOnlyList<DictationRecord>> ListAsync(CancellationToken cancellationToken = default) { await _gate.WaitAsync(cancellationToken); try { return await ReadAsync(cancellationToken); } finally { _gate.Release(); } }
    public async Task DeleteAsync(Guid id, CancellationToken cancellationToken = default) { await _gate.WaitAsync(cancellationToken); try { var rows = await ReadAsync(cancellationToken); rows.RemoveAll(x => x.Id == id); await WriteAsync(rows, cancellationToken); } finally { _gate.Release(); } }
    private async Task<List<DictationRecord>> ReadAsync(CancellationToken token) { if (!File.Exists(_path)) return []; await using var stream = File.OpenRead(_path); return await JsonSerializer.DeserializeAsync<List<DictationRecord>>(stream, cancellationToken: token) ?? []; }
    private async Task WriteAsync(List<DictationRecord> rows, CancellationToken token) { Directory.CreateDirectory(Path.GetDirectoryName(_path) ?? "."); var tmp = _path + ".tmp"; await using (var stream = File.Create(tmp)) await JsonSerializer.SerializeAsync(stream, rows, cancellationToken: token); File.Move(tmp, _path, true); }
}

public sealed class JsonUsageAggregateRepository : IUsageAggregateRepository
{
    private readonly string _path; private readonly SemaphoreSlim _gate = new(1, 1);
    public JsonUsageAggregateRepository(string path) => _path = path;
    public async Task RecordAsync(UsageAggregate aggregate, CancellationToken cancellationToken = default) { await _gate.WaitAsync(cancellationToken); try { var rows = await ReadAsync(cancellationToken); var existing = rows.FirstOrDefault(x => x.Day == aggregate.Day); if (existing is null) rows.Add(aggregate); else { rows[rows.IndexOf(existing)] = existing with { DictationCount = existing.DictationCount + aggregate.DictationCount, AudioSeconds = existing.AudioSeconds + aggregate.AudioSeconds, CharacterCount = existing.CharacterCount + aggregate.CharacterCount }; } await WriteAsync(rows, cancellationToken); } finally { _gate.Release(); } }
    public async Task<IReadOnlyList<UsageAggregate>> ListAsync(CancellationToken cancellationToken = default) { await _gate.WaitAsync(cancellationToken); try { return await ReadAsync(cancellationToken); } finally { _gate.Release(); } }
    private async Task<List<UsageAggregate>> ReadAsync(CancellationToken token) { if (!File.Exists(_path)) return []; await using var stream = File.OpenRead(_path); return await JsonSerializer.DeserializeAsync<List<UsageAggregate>>(stream, cancellationToken: token) ?? []; }
    private async Task WriteAsync(List<UsageAggregate> rows, CancellationToken token) { Directory.CreateDirectory(Path.GetDirectoryName(_path) ?? "."); var tmp = _path + ".tmp"; await using (var stream = File.Create(tmp)) await JsonSerializer.SerializeAsync(stream, rows, cancellationToken: token); File.Move(tmp, _path, true); }
}

public sealed class RetentionService : IRetentionService
{
    private readonly IDictationRepository _repository;
    public RetentionService(IDictationRepository repository) => _repository = repository;
    public async Task<int> ApplyAsync(RetentionPolicy policy, CancellationToken cancellationToken = default)
    {
        if (policy == RetentionPolicy.KeepForever) return 0;
        var rows = await _repository.ListAsync(cancellationToken); var removed = 0; var cutoff = DateTimeOffset.UtcNow - TimeSpan.FromHours(24);
        foreach (var row in rows.Where(x => policy == RetentionPolicy.NeverStore || (x.Retention == RetentionPolicy.DeleteAfter24Hours && x.CreatedAt < cutoff)).ToArray()) { await _repository.DeleteAsync(row.Id, cancellationToken); removed++; }
        return removed;
    }
}

/// <summary>
/// Creates and migrates the user-local SQLite store.  Every repository opens its
/// own short-lived connection, so a process crash cannot leave a shared connection
/// in a partially usable state.  The schema is intentionally boring and mirrors the
/// Core contracts; migrations are monotonic and protected by SQLite's user_version.
/// </summary>
public static class SqlitePersistence
{
    public const int CurrentSchemaVersion = 1;

    public static string DefaultPath => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "FlowerWhisp", "flowerwhisp.db");

    public static async Task<SqliteConnection> OpenAsync(string path, CancellationToken cancellationToken = default)
    {
        SQLitePCL.Batteries_V2.Init();
        if (string.IsNullOrWhiteSpace(path)) throw new ArgumentException("A database path is required.", nameof(path));
        var fullPath = Path.GetFullPath(path);
        Directory.CreateDirectory(Path.GetDirectoryName(fullPath) ?? ".");
        var connection = new SqliteConnection(new SqliteConnectionStringBuilder
        {
            DataSource = fullPath,
            Mode = SqliteOpenMode.ReadWriteCreate,
            Cache = SqliteCacheMode.Shared,
            ForeignKeys = true,
            Pooling = true
        }.ToString());
        await connection.OpenAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            await MigrateAsync(connection, cancellationToken).ConfigureAwait(false);
            return connection;
        }
        catch
        {
            await connection.DisposeAsync().ConfigureAwait(false);
            throw;
        }
    }

    public static async Task MigrateAsync(SqliteConnection connection, CancellationToken cancellationToken = default)
    {
        await using (var pragma = connection.CreateCommand())
        {
            pragma.CommandText = "PRAGMA journal_mode = WAL; PRAGMA foreign_keys = ON;";
            await pragma.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        }

        var version = 0;
        await using (var command = connection.CreateCommand())
        {
            command.CommandText = "PRAGMA user_version;";
            version = Convert.ToInt32(await command.ExecuteScalarAsync(cancellationToken).ConfigureAwait(false));
        }

        if (version >= CurrentSchemaVersion) return;
        await using var transaction = (SqliteTransaction)await connection.BeginTransactionAsync(cancellationToken).ConfigureAwait(false);
        if (version < 1)
        {
            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                CREATE TABLE IF NOT EXISTS dictations (
                    id TEXT NOT NULL PRIMARY KEY,
                    created_at TEXT NOT NULL,
                    raw_text TEXT NOT NULL,
                    final_text TEXT NOT NULL,
                    backend INTEGER NOT NULL,
                    polish_mode INTEGER NOT NULL,
                    retention INTEGER NOT NULL,
                    duration_seconds REAL NOT NULL,
                    language TEXT NULL
                );
                CREATE INDEX IF NOT EXISTS ix_dictations_created_at ON dictations(created_at);
                CREATE TABLE IF NOT EXISTS usage_aggregates (
                    day TEXT NOT NULL PRIMARY KEY,
                    dictation_count INTEGER NOT NULL,
                    audio_seconds REAL NOT NULL,
                    character_count INTEGER NOT NULL
                );
                PRAGMA user_version = 1;
                """;
            await command.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        }
        await transaction.CommitAsync(cancellationToken).ConfigureAwait(false);
    }
}

public sealed class SqliteDictationRepository : IDictationRepository
{
    private readonly string _path;
    public SqliteDictationRepository(string? path = null) => _path = path ?? SqlitePersistence.DefaultPath;

    public async Task SaveAsync(DictationRecord record, CancellationToken cancellationToken = default)
    {
        await using var connection = await SqlitePersistence.OpenAsync(_path, cancellationToken).ConfigureAwait(false);
        await using var command = connection.CreateCommand();
        command.CommandText = """
            INSERT INTO dictations (id, created_at, raw_text, final_text, backend, polish_mode, retention, duration_seconds, language)
            VALUES ($id, $created, $raw, $final, $backend, $polish, $retention, $duration, $language)
            ON CONFLICT(id) DO UPDATE SET
              created_at = excluded.created_at,
              raw_text = excluded.raw_text,
              final_text = excluded.final_text,
              backend = excluded.backend,
              polish_mode = excluded.polish_mode,
              retention = excluded.retention,
              duration_seconds = excluded.duration_seconds,
              language = excluded.language;
            """;
        AddRecordParameters(command, record);
        await command.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
    }

    public async Task<IReadOnlyList<DictationRecord>> ListAsync(CancellationToken cancellationToken = default)
    {
        await using var connection = await SqlitePersistence.OpenAsync(_path, cancellationToken).ConfigureAwait(false);
        await using var command = connection.CreateCommand();
        command.CommandText = "SELECT id, created_at, raw_text, final_text, backend, polish_mode, retention, duration_seconds, language FROM dictations ORDER BY created_at DESC;";
        await using var reader = await command.ExecuteReaderAsync(cancellationToken).ConfigureAwait(false);
        var rows = new List<DictationRecord>();
        while (await reader.ReadAsync(cancellationToken).ConfigureAwait(false))
        {
            rows.Add(new DictationRecord(
                Guid.Parse(reader.GetString(0)),
                DateTimeOffset.Parse(reader.GetString(1), null, System.Globalization.DateTimeStyles.RoundtripKind),
                reader.GetString(2), reader.GetString(3),
                (TranscriptionBackend)reader.GetInt32(4), (PolishMode)reader.GetInt32(5),
                (RetentionPolicy)reader.GetInt32(6), TimeSpan.FromSeconds(reader.GetDouble(7)),
                reader.IsDBNull(8) ? null : reader.GetString(8)));
        }
        return rows;
    }

    public async Task DeleteAsync(Guid id, CancellationToken cancellationToken = default)
    {
        await using var connection = await SqlitePersistence.OpenAsync(_path, cancellationToken).ConfigureAwait(false);
        await using var command = connection.CreateCommand();
        command.CommandText = "DELETE FROM dictations WHERE id = $id;";
        command.Parameters.AddWithValue("$id", id.ToString("D"));
        await command.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
    }

    private static void AddRecordParameters(SqliteCommand command, DictationRecord record)
    {
        command.Parameters.AddWithValue("$id", record.Id.ToString("D"));
        command.Parameters.AddWithValue("$created", record.CreatedAt.ToUniversalTime().ToString("O"));
        command.Parameters.AddWithValue("$raw", record.RawText);
        command.Parameters.AddWithValue("$final", record.FinalText);
        command.Parameters.AddWithValue("$backend", (int)record.Backend);
        command.Parameters.AddWithValue("$polish", (int)record.PolishMode);
        command.Parameters.AddWithValue("$retention", (int)record.Retention);
        command.Parameters.AddWithValue("$duration", record.Duration.TotalSeconds);
        command.Parameters.AddWithValue("$language", (object?)record.Language ?? DBNull.Value);
    }
}

public sealed class SqliteUsageAggregateRepository : IUsageAggregateRepository
{
    private readonly string _path;
    public SqliteUsageAggregateRepository(string? path = null) => _path = path ?? SqlitePersistence.DefaultPath;

    public async Task RecordAsync(UsageAggregate aggregate, CancellationToken cancellationToken = default)
    {
        await using var connection = await SqlitePersistence.OpenAsync(_path, cancellationToken).ConfigureAwait(false);
        await using var command = connection.CreateCommand();
        command.CommandText = """
            INSERT INTO usage_aggregates (day, dictation_count, audio_seconds, character_count)
            VALUES ($day, $count, $seconds, $chars)
            ON CONFLICT(day) DO UPDATE SET
              dictation_count = usage_aggregates.dictation_count + excluded.dictation_count,
              audio_seconds = usage_aggregates.audio_seconds + excluded.audio_seconds,
              character_count = usage_aggregates.character_count + excluded.character_count;
            """;
        command.Parameters.AddWithValue("$day", aggregate.Day.ToString("yyyy-MM-dd"));
        command.Parameters.AddWithValue("$count", aggregate.DictationCount);
        command.Parameters.AddWithValue("$seconds", aggregate.AudioSeconds);
        command.Parameters.AddWithValue("$chars", aggregate.CharacterCount);
        await command.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
    }

    public async Task<IReadOnlyList<UsageAggregate>> ListAsync(CancellationToken cancellationToken = default)
    {
        await using var connection = await SqlitePersistence.OpenAsync(_path, cancellationToken).ConfigureAwait(false);
        await using var command = connection.CreateCommand();
        command.CommandText = "SELECT day, dictation_count, audio_seconds, character_count FROM usage_aggregates ORDER BY day DESC;";
        await using var reader = await command.ExecuteReaderAsync(cancellationToken).ConfigureAwait(false);
        var rows = new List<UsageAggregate>();
        while (await reader.ReadAsync(cancellationToken).ConfigureAwait(false))
            rows.Add(new UsageAggregate(DateOnly.ParseExact(reader.GetString(0), "yyyy-MM-dd"), reader.GetInt32(1), reader.GetDouble(2), reader.GetInt32(3)));
        return rows;
    }
}
