CREATE TABLE coordinates (id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY CHECK (id > 0), VERSION bigint NOT NULL DEFAULT 0,
                                                                                                                              x integer NOT NULL,
                                                                                                                                        y real NOT NULL CHECK (y NOT IN ('NaN'::real,
                                                                                                                                                                         'Infinity'::real,
                                                                                                                                                                         '-Infinity'::real)));


CREATE TABLE LOCATION (id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY CHECK (id > 0), VERSION bigint NOT NULL DEFAULT 0,
                                                                                                                           x real NOT NULL CHECK (x NOT IN ('NaN'::real,
                                                                                                                                                            'Infinity'::real,
                                                                                                                                                            '-Infinity'::real)), y bigint NOT NULL,
                                                                                                                                                                                          name varchar(291));


CREATE TABLE discipline (id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY CHECK (id > 0), VERSION bigint NOT NULL DEFAULT 0,
                                                                                                                             name text NOT NULL CHECK (length(trim(name)) > 0), lecture_hours bigint, practice_hours integer NOT NULL,
                                                                                                                                                                                                                             self_study_hours bigint NOT NULL,
                                                                                                                                                                                                                                                     labs_count bigint NOT NULL);


CREATE TABLE person (id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY CHECK (id > 0), VERSION bigint NOT NULL DEFAULT 0,
                                                                                                                         name text NOT NULL CHECK (length(trim(name)) > 0), eye_color varchar(20),
                                                                                                                                                                                      hair_color varchar(20),
                                                                                                                                                                                                 location_id integer NOT NULL REFERENCES location(id),
                                                                                                                                                                                                                                         birthday timestamp, nationality varchar(20) NOT NULL CHECK (nationality IN ('FRANCE',
                                                                                                                                                                                                                                                                                                                     'SPAIN',
                                                                                                                                                                                                                                                                                                                     'THAILAND')), CHECK (eye_color IN ('GREEN',
                                                                                                                                                                                                                                                                                                                                                        'BLUE',
                                                                                                                                                                                                                                                                                                                                                        'ORANGE',
                                                                                                                                                                                                                                                                                                                                                        'WHITE',
                                                                                                                                                                                                                                                                                                                                                        'BROWN')), CHECK (hair_color IN ('GREEN',
                                                                                                                                                                                                                                                                                                                                                                                         'BLUE',
                                                                                                                                                                                                                                                                                                                                                                                         'ORANGE',
                                                                                                                                                                                                                                                                                                                                                                                         'WHITE',
                                                                                                                                                                                                                                                                                                                                                                                         'BROWN')));


CREATE TABLE lab_work (id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY CHECK (id > 0), VERSION bigint NOT NULL DEFAULT 0,
                                                                                                                           name text NOT NULL CHECK (length(trim(name)) > 0), coordinates_id integer NOT NULL REFERENCES coordinates(id),
                                                                                                                                                                                                                         creation_date timestamp NOT NULL DEFAULT LOCALTIMESTAMP,
                                                                                                                                                                                                                                                                  description text NOT NULL,
                                                                                                                                                                                                                                                                                   difficulty varchar(20) NOT NULL CHECK (difficulty IN ('EASY',
                                                                                                                                                                                                                                                                                                                                         'HARD',
                                                                                                                                                                                                                                                                                                                                         'TERRIBLE')), discipline_id integer REFERENCES discipline(id),
                                                                                                                                                                                                                                                                                                                                                                                        minimal_point integer NOT NULL CHECK (minimal_point > 0), personal_qualities_maximum bigint NOT NULL CHECK (personal_qualities_maximum > 0), author_id integer REFERENCES person(id));


CREATE TABLE discipline_program
  (discipline_id integer NOT NULL REFERENCES discipline(id), lab_work_id integer NOT NULL REFERENCES lab_work(id) ON DELETE CASCADE,
                                                                                                                            PRIMARY KEY (discipline_id,
                                                                                                                                         lab_work_id));


CREATE INDEX ON lab_work (author_id);


CREATE INDEX ON lab_work (discipline_id);


CREATE INDEX ON lab_work (coordinates_id);


CREATE INDEX ON person (location_id);


CREATE FUNCTION protect_creation_date() RETURNS TRIGGER LANGUAGE PLPGSQL AS $$
BEGIN
  NEW.creation_date := OLD.creation_date;
  RETURN NEW;
END
$$;


CREATE TRIGGER immutable_creation_date
BEFORE
UPDATE ON lab_work
FOR EACH ROW EXECUTE FUNCTION protect_creation_date();


CREATE FUNCTION delete_labworks_by_author(p_author_id integer) RETURNS integer LANGUAGE PLPGSQL AS $$
DECLARE
  n integer;
BEGIN
  DELETE FROM lab_work WHERE author_id IS NOT DISTINCT FROM p_author_id;
  GET DIAGNOSTICS n = ROW_COUNT;
  RETURN n;
END
$$;


CREATE FUNCTION delete_one_labwork_by_minimal_point(p_minimal_point integer) RETURNS integer LANGUAGE PLPGSQL AS $$
DECLARE
  result integer;
BEGIN
  IF p_minimal_point IS NULL OR p_minimal_point <= 0 THEN
    RAISE EXCEPTION 'Минимальный балл должен быть больше 0';
  END IF;
  DELETE FROM lab_work
    WHERE id = (SELECT id FROM lab_work WHERE minimal_point = p_minimal_point ORDER BY id LIMIT 1 FOR UPDATE)
    RETURNING id INTO result;
  RETURN result;
END
$$;


CREATE FUNCTION find_labworks_by_name_prefix(p_prefix text) RETURNS
SETOF lab_work LANGUAGE SQL STABLE AS $$
  SELECT * FROM lab_work WHERE starts_with(name, p_prefix) ORDER BY id
$$;


CREATE FUNCTION lower_labwork_difficulty(p_labwork_id integer, p_steps integer) RETURNS varchar LANGUAGE PLPGSQL AS $$
DECLARE
  old_level integer;
  new_level varchar;
BEGIN
  IF p_steps IS NULL OR p_steps < 0 THEN
    RAISE EXCEPTION 'Число шагов должно быть неотрицательным';
  END IF;
  SELECT CASE difficulty WHEN 'EASY' THEN 0 WHEN 'HARD' THEN 1 ELSE 2 END INTO old_level
    FROM lab_work WHERE id = p_labwork_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Лабораторная работа не найдена';
  END IF;
  new_level := (ARRAY['EASY', 'HARD', 'TERRIBLE'])[greatest(0, old_level - p_steps) + 1];
  UPDATE lab_work SET difficulty = new_level, version = version + 1 WHERE id = p_labwork_id;
  RETURN new_level;
END
$$;


CREATE FUNCTION assign_top_ten_hardest_to_discipline(p_discipline_id integer) RETURNS integer LANGUAGE PLPGSQL AS $$
DECLARE
  n integer;
BEGIN
  PERFORM id FROM discipline WHERE id = p_discipline_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Дисциплина не найдена';
  END IF;
  INSERT INTO discipline_program(discipline_id, lab_work_id)
    SELECT p_discipline_id, id FROM lab_work
    ORDER BY CASE difficulty WHEN 'TERRIBLE' THEN 2 WHEN 'HARD' THEN 1 ELSE 0 END DESC, id
    LIMIT 10
    ON CONFLICT DO NOTHING;
  GET DIAGNOSTICS n = ROW_COUNT;
  RETURN n;
END
$$;