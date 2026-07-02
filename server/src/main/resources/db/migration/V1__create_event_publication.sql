CREATE TABLE IF NOT EXISTS event_publication (
    id uuid NOT NULL,
    listener_id character varying(255) NOT NULL,
    event_type character varying(255) NOT NULL,
    serialized_event character varying(255) NOT NULL,
    publication_date timestamp with time zone NOT NULL,
    completion_date timestamp with time zone,
    completion_attempts integer NOT NULL,
    last_resubmission_date timestamp(6) with time zone,
    status character varying(255),
    CONSTRAINT event_publication_pkey PRIMARY KEY (id),
    CONSTRAINT event_publication_status_check CHECK (((status)::text = ANY ((ARRAY['PUBLISHED'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'RESUBMITTED'::character varying])::text[])))
);

CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication USING btree (completion_date);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
