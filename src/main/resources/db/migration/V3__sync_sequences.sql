DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT t
    FROM (VALUES ('coordinates'), ('location'), ('discipline'), ('person'), ('lab_work')) AS x(t)
  LOOP
    EXECUTE format(
        'SELECT setval(pg_get_serial_sequence(%L, ''id''), '
        || '(SELECT COALESCE(MAX(id), 0) + 1 FROM %s), false)',
        r.t, r.t);
  END LOOP;
END
$$;