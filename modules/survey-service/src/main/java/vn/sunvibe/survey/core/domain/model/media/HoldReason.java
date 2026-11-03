package vn.sunvibe.survey.core.domain.model.media;

/** Why an ADMIN put a security hold on a photo (survey-media spec A-23); no free text. */
public enum HoldReason {
    /** A malicious or exploit-suspected file, or a photo produced under a decoder CVE. */
    SECURITY_INCIDENT,
    /** A Sunvibe system secret visible in the photo or its caption; the caption stays hidden after the release. */
    CREDENTIAL_EXPOSURE
}
