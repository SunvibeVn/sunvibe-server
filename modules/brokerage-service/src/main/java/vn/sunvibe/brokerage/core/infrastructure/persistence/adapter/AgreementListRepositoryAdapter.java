package vn.sunvibe.brokerage.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.repository.AgreementIdPage;
import vn.sunvibe.brokerage.core.domain.repository.AgreementListCriteria;
import vn.sunvibe.brokerage.core.domain.repository.AgreementListDomainRepository;

import java.sql.Array;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Repository Implementation - the agreement list's page of ids (E05-S7 spec S7-D1 to S7-D4, 5.3; plan E05S7-P2). Plain
 * SQL: one id-page query and one count query with the same predicate, each joining the caller's transaction. The text
 * is put together from fixed fragments chosen by three flags (a state filter, a lead scope, the ending filter), so the
 * planner gets one plan per combination; every value is bound, the two sets as one varchar array each.
 */
@Service
@RequiredArgsConstructor
public class AgreementListRepositoryAdapter implements AgreementListDomainRepository {

    private static final String SELECT_IDS = "SELECT a.id";
    private static final String SELECT_COUNT = "SELECT count(*)";
    private static final String FROM = " FROM brokerage_agreements a";
    /** Only an activated or effective version can be the version that counts (E05-S6 spec S6-D7). */
    private static final String JOIN_IN_EFFECT_VERSION = " JOIN brokerage_agreement_versions v"
            + " ON v.agreement_id = a.id AND v.status IN ('ACTIVATED', 'EFFECTIVE')";
    private static final String WHERE = " WHERE TRUE";
    private static final String STATUS_IN = " AND a.status = ANY (?)";
    private static final String BASIS_LEAD_IN = " AND a.basis_lead_id = ANY (?)";
    /**
     * brokerage-agreement BR-6, BR-13 (E05-S7 spec S7-D4): only an agreement in effect ends; its version that counts is
     * the EFFECTIVE version with no EFFECTIVE version of a higher number, else, when it has no EFFECTIVE version, its
     * lowest-numbered ACTIVATED one, as AdvertisingRightPolicy.versionThatCounts chooses it. One row per agreement.
     */
    private static final String ENDING_WITHIN = " AND a.status IN ('ACTIVE', 'SUSPENDED')"
            + " AND v.end_date BETWEEN ? AND ?"
            + " AND NOT EXISTS (SELECT 1 FROM brokerage_agreement_versions w"
            + " WHERE w.agreement_id = a.id AND w.status = 'EFFECTIVE' AND w.version_no > v.version_no)"
            + " AND (v.status = 'EFFECTIVE' OR NOT EXISTS (SELECT 1 FROM brokerage_agreement_versions e"
            + " WHERE e.agreement_id = a.id"
            + " AND (e.status = 'EFFECTIVE' OR (e.status = 'ACTIVATED' AND e.version_no < v.version_no))))";
    /** Both orders are total, so pages neither repeat nor skip a row (persistence.md). */
    private static final String NEWEST_FIRST = " ORDER BY a.created_at DESC, a.id DESC";
    private static final String SOONEST_END_FIRST = " ORDER BY v.end_date ASC, a.id ASC";
    private static final String PAGE = " LIMIT ? OFFSET ?";

    private final JdbcTemplate jdbc;

    @Override
    public AgreementIdPage idPage(AgreementListCriteria criteria, int page, int size) {
        String predicate = predicate(criteria);
        String order = criteria.ending() ? SOONEST_END_FIRST : NEWEST_FIRST;
        List<String> ids = jdbc.query(connection -> prepare(connection, SELECT_IDS + predicate + order + PAGE, criteria,
                true, page, size), (row, number) -> row.getString(1));
        Long total = jdbc.query(connection -> prepare(connection, SELECT_COUNT + predicate, criteria, false, page, size),
                result -> result.next() ? result.getLong(1) : 0L);
        return new AgreementIdPage(ids, total == null ? 0L : total);
    }

    /** The FROM and WHERE of both queries; only the flags of the criteria choose its fragments. */
    private static String predicate(AgreementListCriteria criteria) {
        StringBuilder sql = new StringBuilder(FROM);
        if (criteria.ending()) {
            sql.append(JOIN_IN_EFFECT_VERSION);
        }
        sql.append(WHERE);
        if (!criteria.statuses().isEmpty()) {
            sql.append(STATUS_IN);
        }
        if (criteria.basisLeadIdsOrNull() != null) {
            sql.append(BASIS_LEAD_IN);
        }
        if (criteria.ending()) {
            sql.append(ENDING_WITHIN);
        }
        return sql.toString();
    }

    /** Binds the values in the order of the fragments: states, leads, the two dates, then the page bounds. */
    private static PreparedStatement prepare(Connection connection, String sql, AgreementListCriteria criteria,
                                             boolean paged, int page, int size) throws SQLException {
        Array statuses = criteria.statuses().isEmpty() ? null : connection.createArrayOf("varchar",
                criteria.statuses().stream().map(AgreementStatus::name).sorted().toArray());
        Array leads = criteria.basisLeadIdsOrNull() == null ? null : connection.createArrayOf("varchar",
                criteria.basisLeadIdsOrNull().stream().sorted().toArray());
        PreparedStatement statement = connection.prepareStatement(sql);
        int index = 1;
        if (statuses != null) {
            statement.setArray(index++, statuses);
        }
        if (leads != null) {
            statement.setArray(index++, leads);
        }
        if (criteria.ending()) {
            statement.setDate(index++, Date.valueOf(criteria.endingFromOrNull()));
            statement.setDate(index++, Date.valueOf(criteria.endingToOrNull()));
        }
        if (paged) {
            statement.setInt(index++, size);
            statement.setLong(index, (long) page * size);
        }
        return statement;
    }
}
