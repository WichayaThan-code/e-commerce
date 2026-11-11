-- Allow storing a reason code when a FAQ answer is marked not helpful.
ALTER TABLE help_feedback
    ADD COLUMN IF NOT EXISTS reason_code VARCHAR(64);

COMMENT ON COLUMN help_feedback.reason_code IS
    'Optional reason when helpful=false (HARD_TO_UNDERSTAND|INCORRECT_OR_INCOMPLETE|DID_NOT_SOLVE|BAD_LINKS|POLICY_FRICTION)';
