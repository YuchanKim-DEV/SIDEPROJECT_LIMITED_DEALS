package com.limiteddeals.order.repository;

import com.limiteddeals.order.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @Repository를 안붙여도 무관하다.
 * Jparepository를 상속하면 Spring Data가 알아서 빈으로 등록한다.
 *
 */
public interface MemberRepository extends JpaRepository<Member, Long> {
}
