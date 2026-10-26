package demo.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL data-integrity checks, the kind you run after a migration or after API calls
 * that should have written consistent data across tables.
 *
 * The schema is a simplified "dossier + documents" model. Foreign keys are deliberately missing
 * (as in many legacy or migrated databases), so the checks have to catch problems themselves.
 * The test data contains one planted defect per check; each test proves its query finds it.
 */
@DisplayName("Dossier data integrity (SQL)")
class DossierDataIntegrityTest {

    static final String ORPHAN_DOCUMENTS = """
            SELECT d.id
            FROM document d
            LEFT JOIN dossier ds ON ds.id = d.dossier_id
            WHERE ds.id IS NULL
            """;

    static final String DUPLICATE_CASE_NUMBERS = """
            SELECT case_number
            FROM dossier
            GROUP BY case_number
            HAVING COUNT(*) > 1
            """;

    static final String INVALID_STATUSES = """
            SELECT id
            FROM dossier
            WHERE status NOT IN ('OPEN', 'IN_REVIEW', 'DECIDED', 'CLOSED')
            """;

    static final String DOCUMENTS_BEFORE_DOSSIER = """
            SELECT d.id
            FROM document d
            JOIN dossier ds ON ds.id = d.dossier_id
            WHERE d.uploaded_at < ds.created_at
            """;

    private Connection db;

    @BeforeEach
    void createDatabase() throws SQLException {
        db = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (Statement s = db.createStatement()) {
            s.execute("""
                    CREATE TABLE dossier (
                      id          BIGINT PRIMARY KEY,
                      case_number VARCHAR(20) NOT NULL,
                      status      VARCHAR(20) NOT NULL,
                      created_at  TIMESTAMP   NOT NULL)""");
            s.execute("""
                    CREATE TABLE document (
                      id          BIGINT PRIMARY KEY,
                      dossier_id  BIGINT,
                      doc_type    VARCHAR(30) NOT NULL,
                      uploaded_at TIMESTAMP   NOT NULL)""");

            // Valid data
            s.execute("""
                    INSERT INTO dossier VALUES
                      (1, '2026-0001', 'OPEN',      TIMESTAMP '2026-01-10 09:00:00'),
                      (2, '2026-0002', 'IN_REVIEW', TIMESTAMP '2026-01-11 09:00:00'),
                      (3, '2026-0003', 'DECIDED',   TIMESTAMP '2026-01-12 09:00:00')""");
            s.execute("""
                    INSERT INTO document VALUES
                      (10, 1, 'INTERVIEW_REPORT', TIMESTAMP '2026-01-15 10:00:00'),
                      (11, 2, 'IDENTITY_PROOF',   TIMESTAMP '2026-01-12 10:00:00'),
                      (12, 3, 'DECISION_LETTER',  TIMESTAMP '2026-02-01 10:00:00')""");

            // Planted defects, one per check
            s.execute("INSERT INTO dossier VALUES (4, '2026-0001', 'OPEN', TIMESTAMP '2026-01-13 09:00:00')"); // duplicate case no.
            s.execute("INSERT INTO dossier VALUES (5, '2026-0005', 'ARCHIVED', TIMESTAMP '2026-01-14 09:00:00')"); // invalid status
            s.execute("INSERT INTO document VALUES (13, 999, 'PHOTO', TIMESTAMP '2026-01-20 10:00:00')"); // orphan
            s.execute("INSERT INTO document VALUES (14, 3, 'PHOTO', TIMESTAMP '2026-01-01 10:00:00')"); // before dossier
        }
    }

    @AfterEach
    void closeDatabase() throws SQLException {
        db.close();
    }

    @Test
    void findsDocumentsWithoutADossier() throws SQLException {
        assertThat(column(ORPHAN_DOCUMENTS)).containsExactly("13");
    }

    @Test
    void findsDuplicateCaseNumbers() throws SQLException {
        assertThat(column(DUPLICATE_CASE_NUMBERS)).containsExactly("2026-0001");
    }

    @Test
    void findsDossiersWithAnInvalidStatus() throws SQLException {
        assertThat(column(INVALID_STATUSES)).containsExactly("5");
    }

    @Test
    void findsDocumentsUploadedBeforeTheirDossierExisted() throws SQLException {
        assertThat(column(DOCUMENTS_BEFORE_DOSSIER)).containsExactly("14");
    }

    @Test
    void cleanDataPassesEveryCheck() throws SQLException {
        try (Statement s = db.createStatement()) {
            s.execute("DELETE FROM document WHERE id IN (13, 14)");
            s.execute("DELETE FROM dossier WHERE id IN (4, 5)");
        }
        for (String check : List.of(ORPHAN_DOCUMENTS, DUPLICATE_CASE_NUMBERS, INVALID_STATUSES, DOCUMENTS_BEFORE_DOSSIER)) {
            assertThat(column(check)).as(check).isEmpty();
        }
    }

    private List<String> column(String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement s = db.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
    }
}
