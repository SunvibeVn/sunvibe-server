package vn.sunvibe.survey.core.infrastructure.persistence.entity.media;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.sunvibe.survey.core.domain.model.media.VariantKind;

import java.io.Serializable;

/** The primary key of a photo variant: the photo and the variant kind (survey-media spec 5.2). */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SurveyMediaVariantKey implements Serializable {

    @Column(name = "media_id", length = 26)
    private String mediaId;

    @Column(name = "variant", length = 10)
    @Enumerated(EnumType.STRING)
    private VariantKind variant;
}
