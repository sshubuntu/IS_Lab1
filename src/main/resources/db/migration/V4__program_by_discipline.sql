-- Программа дисциплины — это работы, поле discipline которых указывает на неё.
-- Отдельная таблица discipline_program дублировала эту связь и расходилась с ней:
-- работа могла числиться в программе одной дисциплины, оставаясь привязанной к другой.

-- Переносим ранее собранные программы в поле discipline, не затирая уже совпадающие связи.
UPDATE lab_work l
   SET discipline_id = p.discipline_id,
       VERSION = l.version + 1
  FROM (SELECT DISTINCT ON (lab_work_id) lab_work_id, discipline_id
          FROM discipline_program
         ORDER BY lab_work_id, discipline_id) p
 WHERE p.lab_work_id = l.id
   AND l.discipline_id IS DISTINCT FROM p.discipline_id;

DROP TABLE discipline_program;

DROP FUNCTION assign_top_ten_hardest_to_discipline(integer);

CREATE FUNCTION assign_top_ten_hardest_to_discipline(p_discipline_id integer) RETURNS integer LANGUAGE PLPGSQL AS $$
DECLARE
  n integer;
BEGIN
  PERFORM id FROM discipline WHERE id = p_discipline_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Дисциплина не найдена';
  END IF;
  UPDATE lab_work
     SET discipline_id = p_discipline_id,
         VERSION = VERSION + 1
   WHERE id IN (SELECT id
                  FROM lab_work
                 ORDER BY CASE difficulty
                            WHEN 'TERRIBLE' THEN 2
                            WHEN 'HARD' THEN 1
                            ELSE 0
                          END DESC, id
                 LIMIT 10)
     AND discipline_id IS DISTINCT FROM p_discipline_id;
  GET DIAGNOSTICS n = ROW_COUNT;
  RETURN n;
END
$$;
