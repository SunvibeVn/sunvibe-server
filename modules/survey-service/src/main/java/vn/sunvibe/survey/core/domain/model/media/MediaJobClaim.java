package vn.sunvibe.survey.core.domain.model.media;

/** A PROCESS job claimed by a worker (survey-media spec 4.4 step 3): the job with its new lease and the uploader. */
public record MediaJobClaim(MediaJob job, String uploadedByUserId) {

    public MediaJobClaim {
        if (job == null || job.leaseTokenOrNull() == null || uploadedByUserId == null) {
            throw new IllegalArgumentException("A claimed job has its lease token and uploader.");
        }
    }

    /** The lease token is the fencing token: ids only. */
    @Override
    public String toString() {
        return "MediaJobClaim[" + job.id() + " attempt " + job.attempts() + "]";
    }
}
