package com.limiteddealproducts.repository;

import com.limiteddealproducts.domain.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class MemberRepositoryTest {

    @Autowired
    MemberRepository memberRepository;

    @Test
    void saveANDcheck(){
        Member member = new Member("김유찬", "yuchankim0906@gmail.com");

        Member saved = memberRepository.save(member);
        Member found = memberRepository.findById(saved.getId()).orElseThrow();

        assertThat(saved.getId()).isNotNull();
        assertThat(found.getName()).isEqualTo("김유찬");
        assertThat(found.getEmail()).isEqualTo("yuchankim0906@gmail.com");
    }


}
