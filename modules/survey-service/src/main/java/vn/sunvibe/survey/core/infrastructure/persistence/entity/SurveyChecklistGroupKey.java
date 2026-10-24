package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;

import java.io.Serial;
import java.io.Serializable;

/** The key of a group row: the checklist and the group. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SurveyChecklistGroupKey implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "checklist_id", length = 26)
    private String checklistId;

    @Column(name = "group_key", length = 20)
    @Enumerated(EnumType.STRING)
    private ChecklistGroup groupKey;
}
