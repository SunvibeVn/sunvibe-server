package vn.sunvibe.survey.core.infrastructure.persistence.entity.common;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** The primary key of a photo's row under a revision: the revision and the photo (survey-checklist submission spec 5.3). */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SurveyChecklistMediaKey implements Serializable {

    @Column(name = "checklist_id", length = 26)
    private String checklistId;

    @Column(name = "media_id", length = 26)
    private String mediaId;
}
