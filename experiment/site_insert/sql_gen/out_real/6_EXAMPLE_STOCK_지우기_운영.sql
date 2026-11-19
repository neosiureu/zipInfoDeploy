DECLARE
  -- 6. drop the marker table after the rollback has been committed.
BEGIN
  IF SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') <> 'ADMIN' THEN RAISE_APPLICATION_ERROR(-20001, 'Schema is not ADMIN. Nothing was dropped.'); END IF;
  EXECUTE IMMEDIATE 'DROP TABLE EXAMPLE_STOCK';
END;
