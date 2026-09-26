package com.example.backend.diagnosis;

import com.example.backend.diagnosis.entity.DiagnosisCode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DiagnosisRepository extends JpaRepository<DiagnosisCode, String> {
    @Query("""
        select d from DiagnosisCode d where d.completeCode = true and
        (d.code like :code escape '!' or lower(d.koreanName) like :term escape '!'
         or lower(d.englishName) like :term escape '!'
         or exists (select t.id from DiagnosisTerm t where t.diagnosis = d
             and (lower(t.koreanName) like :term escape '!' or lower(t.englishName) like :term escape '!')))
        order by case when d.code = :exact then 0 else 1 end, d.code
        """)
    List<DiagnosisCode> search(@Param("code") String code, @Param("term") String term,
                              @Param("exact") String exact, Pageable pageable);
}
