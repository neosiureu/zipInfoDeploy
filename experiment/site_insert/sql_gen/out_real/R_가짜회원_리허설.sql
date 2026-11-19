DECLARE
  -- R. rehearsal only. A fake member who likes and has seen two example listings (plan section 9 step 5).
  -- Then the rollback has real LIKE_STOCK and STOCK_SAW rows to delete. COMMIT by hand after this block.
  -- If the fake member is already there (second round of the rehearsal), it is used again, not made twice.
  v_fake NUMBER;
  n NUMBER;
BEGIN
  IF SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') <> 'ZIP_REHEARSAL' THEN RAISE_APPLICATION_ERROR(-20001, 'Schema is not ZIP_REHEARSAL. Nothing was inserted.'); END IF;
  SELECT COUNT(*) INTO n FROM MEMBER WHERE MEMBER_EMAIL = 'rehearsal-fake-member@example.invalid';
  IF n = 0 THEN
    INSERT INTO MEMBER (MEMBER_NO, MEMBER_EMAIL, MEMBER_PW, MEMBER_IMG, ENROLL_DATE, MEMBER_DEL_FL, MEMBER_LOGIN,
                        MEMBER_NICKNAME, MEMBER_NAME, MEMBER_AUTH, ACCESS_TOKEN, MEMBER_LOCATION, MEMBER_WITHDRAW_DATE)
    VALUES (SEQ_MEMBER.NEXTVAL, 'rehearsal-fake-member@example.invalid', NULL, NULL, SYSDATE, 'N', 'E', 'rehearsal-fake', NULL, 1, NULL, NULL, NULL)
    RETURNING MEMBER_NO INTO v_fake;
    INSERT INTO TOKEN_INFO (MEMBER_NO, TOKEN_INFO) VALUES (v_fake, NULL);
  ELSE
    SELECT MEMBER_NO INTO v_fake FROM MEMBER WHERE MEMBER_EMAIL = 'rehearsal-fake-member@example.invalid';
  END IF;
  INSERT INTO LIKE_STOCK (STOCK_NO, MEMBER_NO) SELECT STOCK_NO, v_fake FROM EXAMPLE_STOCK WHERE CSV_ROW IN (1, 2);
  INSERT INTO STOCK_SAW (STOCK_NO, MEMBER_NO) SELECT STOCK_NO, v_fake FROM EXAMPLE_STOCK WHERE CSV_ROW IN (1, 2);
END;
