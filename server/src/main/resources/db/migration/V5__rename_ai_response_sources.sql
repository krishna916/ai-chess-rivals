UPDATE dialogue_line
SET response_source = 'REMOTE_PRIMARY'
WHERE response_source = 'GROQ';

UPDATE dialogue_line
SET response_source = 'REMOTE_FALLBACK'
WHERE response_source = 'GEMINI';
