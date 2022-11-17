package hello.springtx.propagation;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.*;

@Slf4j
@SpringBootTest
public class BasicTxTest {

    @Autowired
    PlatformTransactionManager txManager;

    @TestConfiguration
    static class Config {
        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @Test
    void commit() {
        log.info("트랜잭션 시작");
        TransactionStatus status = txManager.getTransaction(new DefaultTransactionAttribute());

        log.info("트랜잭션 커밋 시작");
        txManager.commit(status);
        log.info("트랜잭션 커밋 종료");
        // 트랜잭션 시작시 트랜잭션 풀에서 트랜잭션을 가져온 후 커밋이 완료되면 해당 트랜잭션을 돌려준다.
    }

    @Test
    void rollback() {
        log.info("트랜잭션 시작");
        TransactionStatus status = txManager.getTransaction(new DefaultTransactionAttribute());

        log.info("트랜잭션 롤백 시작");
        txManager.rollback(status);
        log.info("트랜잭션 롤백 종료");
        // 트랜잭션 시작시 트랜잭션 풀에서 트랜잭션을 가져온 후 롤백이 완료되면 해당 트랜잭션을 돌려준다.
    }

    @Test
    void double_commit() {
        log.info("트랜잭션1 시작");
        TransactionStatus tx1 = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("트랜잭션1 커밋");
        txManager.commit(tx1);

        log.info("트랜잭션2 시작");
        TransactionStatus tx2 = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("트랜잭션2 커밋");
        txManager.commit(tx2);
    }

    @Test
    void double_commit_rollback() {
        log.info("트랜잭션1 시작");
        TransactionStatus tx1 = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("트랜잭션1 커밋");
        txManager.commit(tx1);

        log.info("트랜잭션2 시작");
        TransactionStatus tx2 = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("트랜잭션2 롤백");
        txManager.rollback(tx2);
    }

    @Test
    void inner_commit() {
        log.info("외부 트랜잭션 시작");
        TransactionStatus outer = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("outer.isNewTransaction()={}", outer.isNewTransaction());  // 처음 수행된 트랜잭션인지 여부 : true

        log.info("내부 트랜잭션 시작");
        TransactionStatus inner = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("inner.isNewTransaction()={}", inner.isNewTransaction());  // 처음 수행된 트랜잭션인지 여부 : false -> 외부 트랜잭션의 커넥션 사용
        // o.s.j.d.DataSourceTransactionManager     : Participating in existing transaction -> 신규 트랜잭션이 아닌 기존 트랜잭션에 참여
        log.info("내부 트랜잭션 커밋");
        txManager.commit(inner);    // 물리적으로 아무 일도 발생하지 않는다.(외부 트랜잭션만 물리 트랜잭션 관리 -> 실행/커밋)
        // 트랜잭션 매니저는 신규 트랜잭션 여부에 따라 커밋 호출을 결정한다.(논리 커밋만 호출)

        log.info("외부 트랜잭션 커밋");
        txManager.commit(outer);
    }

    @Test
    void outer_rollback() {
        log.info("외부 트랜잭션 시작");
        TransactionStatus outer = txManager.getTransaction(new DefaultTransactionAttribute());

        log.info("내부 트랜잭션 시작");
        TransactionStatus inner = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("내부 트랜잭션 커밋");
        txManager.commit(inner);

        log.info("외부 트랜잭션 롤백");
        txManager.rollback(outer);
    }

    @Test
    void inner_rollback() {
        log.info("외부 트랜잭션 시작");
        TransactionStatus outer = txManager.getTransaction(new DefaultTransactionAttribute());

        log.info("내부 트랜잭션 시작");
        TransactionStatus inner = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("내부 트랜잭션 롤백");
        txManager.rollback(inner);
        // org.springframework.transaction.UnexpectedRollbackException: Transaction rolled back because it has been marked as rollback-only
        // rollback-only 표시 : Participating transaction failed - marking existing transaction as rollback-only
        log.info("inner.isRollbackOnly()={}", inner.isRollbackOnly());
        log.info("outer.isRollbackOnly()={}", outer.isRollbackOnly());

        log.info("외부 트랜잭션 커밋");
        assertThatThrownBy(() -> txManager.commit(outer)).isInstanceOf(UnexpectedRollbackException.class);
        // commit을 시도했지만 기대하지 않았던 rollback이 발생했다는 예외 발생
    }

}
