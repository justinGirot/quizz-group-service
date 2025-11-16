package com.quizz.group.repository;

import com.quizz.group.model.Group;
import com.quizz.group.model.GroupType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Group entity.
 */
@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    /**
     * Check if a group exists by name.
     */
    boolean existsByName(String name);

    /**
     * Find a group by name.
     */
    Optional<Group> findByName(String name);

    /**
     * Find all public groups with pagination.
     */
    Page<Group> findByType(GroupType type, Pageable pageable);

    /**
     * Search groups by name containing the search term (case-insensitive).
     */
    @Query("SELECT g FROM Group g WHERE LOWER(g.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Group> searchByName(@Param("search") String search, Pageable pageable);

    /**
     * Increment member count for a group.
     */
    @Modifying
    @Query("UPDATE Group g SET g.memberCount = g.memberCount + 1 WHERE g.id = :groupId")
    void incrementMemberCount(@Param("groupId") Long groupId);

    /**
     * Decrement member count for a group.
     */
    @Modifying
    @Query("UPDATE Group g SET g.memberCount = g.memberCount - 1 WHERE g.id = :groupId AND g.memberCount > 0")
    void decrementMemberCount(@Param("groupId") Long groupId);
}
