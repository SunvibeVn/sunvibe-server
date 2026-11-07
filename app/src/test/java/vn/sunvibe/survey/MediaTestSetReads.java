package vn.sunvibe.survey;

import java.time.Instant;
import java.util.Optional;

import vn.sunvibe.survey.core.domain.model.media.MediaSet;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSetDomainRepository;

/**
 * The media tests' set repository: the real adapter behind a test-only hook that runs once, on the reading thread,
 * right after the next plain read of one checklist's set row (survey-media spec 4.6, 5.4; plan Task A18). A test uses
 * it to commit a photo from another thread inside a checklist or survey block read, or to look at that read's
 * transaction. Unarmed it only delegates.
 */
public final class MediaTestSetReads implements SurveyMediaSetDomainRepository {

	private final SurveyMediaSetDomainRepository real;
	private volatile String armedChecklistId;
	private volatile Runnable afterRead;

	public MediaTestSetReads(SurveyMediaSetDomainRepository real) {
		this.real = real;
	}

	/** Runs the action once, after the next {@link #findForRead} of the checklist, on the reading thread. */
	public void afterNextRead(String checklistId, Runnable action) {
		afterRead = action;
		armedChecklistId = checklistId;
	}

	public void reset() {
		armedChecklistId = null;
		afterRead = null;
	}

	@Override
	public void insertIgnoringExisting(String checklistId, Instant now) {
		real.insertIgnoringExisting(checklistId, now);
	}

	@Override
	public Optional<MediaSet> lockForUpdate(String checklistId) {
		return real.lockForUpdate(checklistId);
	}

	@Override
	public Optional<MediaSet> findForRead(String checklistId) {
		Optional<MediaSet> set = real.findForRead(checklistId);
		Runnable action = afterRead;
		if (action != null && checklistId.equals(armedChecklistId)) {
			reset();
			action.run();
		}
		return set;
	}

	@Override
	public void update(MediaSet set) {
		real.update(set);
	}
}
