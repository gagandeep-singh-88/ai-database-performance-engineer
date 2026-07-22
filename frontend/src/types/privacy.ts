export interface PiiFinding {
  type: string;
  label: string;
  occurrences: number;
}

export interface RemovedField {
  location: string;
  category: string;
  reason: string;
  occurrences: number;
}

export interface PlaceholderMapping {
  placeholder: string; // e.g. "$1"
  category: string; // e.g. "Email address"
  occurrences: number;
}

export interface ValidationResult {
  passed: boolean;
  aiEnabled: boolean;
  residualFindings: PiiFinding[];
  message: string;
}

export interface PayloadSection {
  sql: string | null;
  executionPlan: string | null;
  metrics: string | null;
}

export type PrivacyStatus = 'PROTECTED' | 'ALLOWED_WITH_WARNING' | 'BLOCKED' | 'AI_DISABLED';

export interface PayloadPreviewResponse {
  original: PayloadSection;
  sanitized: PayloadSection;
  findings: PiiFinding[];
  placeholders: PlaceholderMapping[];
  removedFields: RemovedField[];
  validation: ValidationResult;
  privacyStatus: PrivacyStatus;
}

export interface PreviewPayload {
  sql: string | null;
  executionPlan: string | null;
  metricsJson: string | null;
}

export type SanitizationMode = 'AUTOMATIC' | 'WARN_BEFORE_SENDING' | 'STRICT_BLOCK';
export type AiResponseStyle = 'TECHNICAL' | 'SIMPLE';

export interface PrivacySettingsDto {
  sqlSanitizationEnabled: boolean;
  aiEnabled: boolean;
  payloadValidationEnabled: boolean;
  showPayloadPreview: boolean;
  blockOnPiiDetected: boolean;
  sanitizationMode: SanitizationMode;
  aiResponseStyle: AiResponseStyle;
  maxResponseLength: number;
  updatedAt: string;
}

/** Partial update — omit a field to leave it unchanged. */
export type UpdatePrivacySettingsPayload = Partial<Omit<PrivacySettingsDto, 'updatedAt'>>;
