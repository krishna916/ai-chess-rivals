CREATE TABLE IF NOT EXISTS event_publication (
    id uuid NOT NULL,
    listener_id text NOT NULL,
    event_type text NOT NULL,
    serialized_event text NOT NULL,
    publication_date timestamp with time zone NOT NULL,
    completion_date timestamp with time zone,
    completion_attempts integer NOT NULL,
    last_resubmission_date timestamp(6) with time zone,
    status text,
    CONSTRAINT event_publication_pkey PRIMARY KEY (id),
    CONSTRAINT event_publication_status_check CHECK (status IN ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED'))
);

CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication USING btree (completion_date);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication (listener_id, serialized_event);
