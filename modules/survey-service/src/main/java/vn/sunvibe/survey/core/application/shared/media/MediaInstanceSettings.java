package vn.sunvibe.survey.core.application.shared.media;

/**
 * This instance's survey-media settings the intake needs (plan PD-12, survey-media spec A-3): its id in body slot rows and error
 * logs, how many content bodies one user sends at once ({@code app.survey.media.bodies.per-user}, across instances) and
 * how many this instance reads at once ({@code bodies.per-instance}).
 */
public record MediaInstanceSettings(String instanceId, int bodiesPerUser, int bodiesPerInstance) {

    public MediaInstanceSettings {
        if (instanceId == null || instanceId.isBlank() || bodiesPerUser < 1 || bodiesPerInstance < 1) {
            throw new IllegalArgumentException("An instance has an id and room for at least one body per user and in all.");
        }
    }
}
