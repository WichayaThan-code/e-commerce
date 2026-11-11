-- Allow rich HTML bodies for help topic descriptions and guide summaries (FAQ answers already TEXT).

ALTER TABLE help_topics
    ALTER COLUMN description_th TYPE TEXT,
    ALTER COLUMN description_en TYPE TEXT,
    ALTER COLUMN description_zh TYPE TEXT;

ALTER TABLE help_guides
    ALTER COLUMN summary_th TYPE TEXT,
    ALTER COLUMN summary_en TYPE TEXT,
    ALTER COLUMN summary_zh TYPE TEXT;
