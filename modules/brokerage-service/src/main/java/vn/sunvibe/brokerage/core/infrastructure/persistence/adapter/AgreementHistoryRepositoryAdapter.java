package vn.sunvibe.brokerage.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.sunvibe.brokerage.core.domain.repository.AgreementHistoryDomainRepository;
import vn.sunvibe.brokerage.core.domain.repository.AgreementHistoryPage;
import vn.sunvibe.brokerage.core.domain.repository.AgreementHistoryRow;

import java.util.List;

/**
 * Repository Implementation - the read of an agreement's stored history (E05-S7 spec S7-D7, 5.3). Plain SQL over the
 * index on agreement_id: one page query and one count, each joining the caller's transaction. The rows are read as
 * stored, the event code as its text (spec S7-D8 maps an unknown one).
 */
@Service
@RequiredArgsConstructor
public class AgreementHistoryRepositoryAdapter implements AgreementHistoryDomainRepository {

    private final JdbcTemplate jdbc;

    @Override
    public AgreementHistoryPage history(String agreementId, int page, int size) {
        List<AgreementHistoryRow> rows = jdbc.query("SELECT event_type, version_no, actor_user_id, occurred_at"
                        + " FROM brokerage_agreement_events WHERE agreement_id = ?"
                        + " ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?",
                (row, number) -> new AgreementHistoryRow(row.getString("event_type"),
                        row.getObject("version_no", Integer.class), row.getString("actor_user_id"),
                        row.getTimestamp("occurred_at").toInstant()),
                agreementId, size, (long) page * size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM brokerage_agreement_events WHERE agreement_id = ?",
                Long.class, agreementId);
        return new AgreementHistoryPage(rows, total == null ? 0L : total);
    }
}
