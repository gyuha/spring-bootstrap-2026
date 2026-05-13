-- =============================================================================
-- V2__batch_schema.sql — Spring Batch 5.x 스키마 (PostgreSQL)
--
-- spring.batch.jdbc.initialize-schema=never 설정으로 Spring Batch 자동 초기화를
-- 비활성화하고 Flyway가 배치 테이블 생성을 담당합니다.
--
-- 원본 참조: spring-batch-core/src/main/resources/org/springframework/batch/core/schema-postgresql.sql
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 시퀀스 생성 (PK 생성용)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE SEQUENCE BATCH_STEP_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_EXECUTION_SEQ  MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_SEQ            MAXVALUE 9223372036854775807 NO CYCLE;

-- ─────────────────────────────────────────────────────────────────────────────
-- BATCH_JOB_INSTANCE — 잡 인스턴스 (잡 이름 + 파라미터 조합)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE BATCH_JOB_INSTANCE (
    JOB_INSTANCE_ID BIGINT       NOT NULL PRIMARY KEY,
    VERSION         BIGINT,
    JOB_NAME        VARCHAR(100) NOT NULL,
    JOB_KEY         VARCHAR(32)  NOT NULL,

    CONSTRAINT JOB_INST_UN UNIQUE (JOB_NAME, JOB_KEY)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- BATCH_JOB_EXECUTION — 잡 실행 이력
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE BATCH_JOB_EXECUTION (
    JOB_EXECUTION_ID BIGINT        NOT NULL PRIMARY KEY,
    VERSION          BIGINT,
    JOB_INSTANCE_ID  BIGINT        NOT NULL,
    CREATE_TIME      TIMESTAMP     NOT NULL,
    START_TIME       TIMESTAMP     DEFAULT NULL,
    END_TIME         TIMESTAMP     DEFAULT NULL,
    STATUS           VARCHAR(10),
    EXIT_CODE        VARCHAR(2500),
    EXIT_MESSAGE     VARCHAR(2500),
    LAST_UPDATED     TIMESTAMP,

    CONSTRAINT JOB_INST_EXEC_FK FOREIGN KEY (JOB_INSTANCE_ID)
        REFERENCES BATCH_JOB_INSTANCE (JOB_INSTANCE_ID)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- BATCH_JOB_EXECUTION_PARAMS — 잡 실행 파라미터
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE BATCH_JOB_EXECUTION_PARAMS (
    JOB_EXECUTION_ID BIGINT        NOT NULL,
    PARAMETER_NAME   VARCHAR(100)  NOT NULL,
    PARAMETER_TYPE   VARCHAR(100)  NOT NULL,
    PARAMETER_VALUE  VARCHAR(2500),
    IDENTIFYING      CHAR(1)       NOT NULL,

    CONSTRAINT JOB_EXEC_PARAMS_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- BATCH_STEP_EXECUTION — 스텝 실행 이력
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE BATCH_STEP_EXECUTION (
    STEP_EXECUTION_ID  BIGINT        NOT NULL PRIMARY KEY,
    VERSION            BIGINT        NOT NULL,
    STEP_NAME          VARCHAR(100)  NOT NULL,
    JOB_EXECUTION_ID   BIGINT        NOT NULL,
    CREATE_TIME        TIMESTAMP     NOT NULL,
    START_TIME         TIMESTAMP     DEFAULT NULL,
    END_TIME           TIMESTAMP     DEFAULT NULL,
    STATUS             VARCHAR(10),
    COMMIT_COUNT       BIGINT,
    READ_COUNT         BIGINT,
    FILTER_COUNT       BIGINT,
    WRITE_COUNT        BIGINT,
    READ_SKIP_COUNT    BIGINT,
    WRITE_SKIP_COUNT   BIGINT,
    PROCESS_SKIP_COUNT BIGINT,
    ROLLBACK_COUNT     BIGINT,
    EXIT_CODE          VARCHAR(2500),
    EXIT_MESSAGE       VARCHAR(2500),
    LAST_UPDATED       TIMESTAMP,

    CONSTRAINT JOB_EXEC_STEP_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- BATCH_STEP_EXECUTION_CONTEXT — 스텝 실행 컨텍스트 (체크포인트)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE BATCH_STEP_EXECUTION_CONTEXT (
    STEP_EXECUTION_ID  BIGINT        NOT NULL PRIMARY KEY,
    SHORT_CONTEXT      VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,

    CONSTRAINT STEP_EXEC_CTX_FK FOREIGN KEY (STEP_EXECUTION_ID)
        REFERENCES BATCH_STEP_EXECUTION (STEP_EXECUTION_ID)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- BATCH_JOB_EXECUTION_CONTEXT — 잡 실행 컨텍스트
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE BATCH_JOB_EXECUTION_CONTEXT (
    JOB_EXECUTION_ID   BIGINT        NOT NULL PRIMARY KEY,
    SHORT_CONTEXT      VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,

    CONSTRAINT JOB_EXEC_CTX_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
);
