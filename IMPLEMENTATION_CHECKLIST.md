# Implementation Checklist: Group Service & Question Service Integration

## Overview

This document describes everything needed to implement the group-based access control system with caching and webhooks.

---

## Part 1: Question Service (Frontend Consumer)

The Question Service consumes the Group Service API and needs caching, resilience, and webhook handling.

### 1.1 Dependencies (pom.xml)

**Already Added:**
```xml
<!-- Caching -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>

<!-- Resilience4j for Circuit Breaker -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

### 1.2 Cache Configuration

**File:** `src/main/java/com/quizz/question/config/CacheConfig.java`

**Already Created:**
```java
@Configuration
@EnableCaching
public class CacheConfig {
    public static final String ADMIN_GROUPS_CACHE = "adminGroups";
    public static final String USER_GROUPS_CACHE = "userGroups";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            ADMIN_GROUPS_CACHE, USER_GROUPS_CACHE
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)  // 5-minute TTL
            .maximumSize(1000)                       // Max 1000 users
            .recordStats());                         // Enable metrics
        return cacheManager;
    }
}
```

### 1.3 Resilience4j Circuit Breaker Configuration

**File:** `src/main/resources/application.yml` (or application.properties)

**Need to Add:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      groupService:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 5s
        failureRateThreshold: 50
        eventConsumerBufferSize: 10
```

**What this does:**
- Opens circuit if 50% of calls fail (after 5 calls minimum)
- Waits 5 seconds before trying again (half-open state)
- If Group Service is down, circuit opens and returns fallback (empty list)

### 1.4 Update GroupServiceClient

**File:** `src/main/java/com/quizz/question/client/GroupServiceClient.java`

**Changes Needed:**

1. **Add constructor parameter for MeterRegistry:**
```java
public GroupServiceClient(
        RestTemplate restTemplate,
        @Value("${group.service.url:http://localhost:8083}") String groupServiceUrl,
        MeterRegistry meterRegistry) {  // ADD THIS
    this.restTemplate = restTemplate;
    this.groupServiceUrl = groupServiceUrl;
    this.meterRegistry = meterRegistry;  // ADD THIS
}
```

2. **Add caching to getUserAdminGroupIds():**
```java
@Cacheable(value = ADMIN_GROUPS_CACHE, key = "#userId")
@CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getUserAdminGroupIdsFallback")
public List<Long> getUserAdminGroupIds(Long userId) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
        String url = groupServiceUrl + "/api/groups/user/" + userId + "/admin-groups";
        log.debug("Fetching admin groups for userId={}", userId);

        UserGroupsResponse response = restTemplate.getForObject(url, UserGroupsResponse.class);
        List<Long> adminGroupIds = response != null ? response.getGroupIds() : Collections.emptyList();

        log.debug("User is admin of {} groups", adminGroupIds.size());

        sample.stop(Timer.builder("group.service.call")
            .tag("method", "getUserAdminGroupIds")
            .tag("outcome", "success")
            .register(meterRegistry));

        return adminGroupIds;
    } catch (Exception e) {
        sample.stop(Timer.builder("group.service.call")
            .tag("method", "getUserAdminGroupIds")
            .tag("outcome", "failure")
            .register(meterRegistry));
        throw e;
    }
}

// Fallback method when circuit is open
private List<Long> getUserAdminGroupIdsFallback(Long userId, Exception e) {
    log.warn("Circuit breaker active for getUserAdminGroupIds, returning empty list: userId={}", userId, e);
    return Collections.emptyList();  // Fail-safe: deny access
}
```

3. **Add caching to getUserGroupIds():**
```java
@Cacheable(value = USER_GROUPS_CACHE, key = "#userId")
@CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getUserGroupIdsFallback")
public List<Long> getUserGroupIds(Long userId) {
    // Similar implementation with metrics
}

private List<Long> getUserGroupIdsFallback(Long userId, Exception e) {
    log.warn("Circuit breaker active for getUserGroupIds, returning empty list: userId={}", userId, e);
    return Collections.emptyList();
}
```

4. **Add cache invalidation method:**
```java
@CacheEvict(value = ADMIN_GROUPS_CACHE, key = "#userId")
public void invalidateUserAdminGroupsCache(Long userId) {
    log.info("Invalidating admin groups cache for userId={}", userId);
}

@CacheEvict(value = USER_GROUPS_CACHE, key = "#userId")
public void invalidateUserGroupsCache(Long userId) {
    log.info("Invalidating user groups cache for userId={}", userId);
}
```

### 1.5 Cache Invalidation Webhook Controller

**File:** `src/main/java/com/quizz/question/controller/CacheInvalidationController.java`

**Need to Create:**
```java
package com.quizz.question.controller;

import com.quizz.question.client.GroupServiceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook endpoint for cache invalidation
 * Called by Group Service when user roles change
 */
@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cache Management", description = "Webhook endpoints for cache invalidation")
public class CacheInvalidationController {

    private final GroupServiceClient groupServiceClient;

    @PostMapping("/invalidate/user/{userId}/admin-groups")
    @Operation(summary = "Invalidate admin groups cache for user",
               description = "Webhook called by Group Service when user's admin roles change")
    public ResponseEntity<Void> invalidateUserAdminGroups(@PathVariable Long userId) {
        log.info("Received cache invalidation request for user {} admin groups", userId);
        groupServiceClient.invalidateUserAdminGroupsCache(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/invalidate/user/{userId}/groups")
    @Operation(summary = "Invalidate user groups cache",
               description = "Webhook called by Group Service when user's group memberships change")
    public ResponseEntity<Void> invalidateUserGroups(@PathVariable Long userId) {
        log.info("Received cache invalidation request for user {} groups", userId);
        groupServiceClient.invalidateUserGroupsCache(userId);
        return ResponseEntity.ok().build();
    }
}
```

**What this does:**
- Group Service calls these endpoints when roles change
- Immediately invalidates cache for affected user
- Ensures next request fetches fresh data

### 1.6 Application Properties Configuration

**File:** `src/main/resources/application.yml`

**Need to Add/Verify:**
```yaml
group:
  service:
    url: http://localhost:8083  # URL of Group Service

# Actuator endpoints (for monitoring cache stats)
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,caches
  metrics:
    enable:
      cache: true
```

---

## Part 2: Group Service (Backend Provider)

The Group Service provides the API that Question Service consumes. This is a **NEW SERVICE** that needs to be created from scratch.

### 2.1 Project Setup

**Create New Spring Boot Project:**

**File:** `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.0</version>
        <relativePath/>
    </parent>

    <groupId>com.quizz</groupId>
    <artifactId>group-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>group-service</name>
    <description>Group management service</description>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Database -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.liquibase</groupId>
            <artifactId>liquibase-core</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- OpenAPI/Swagger -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.3.0</version>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 2.2 Database Entities

**File:** `src/main/java/com/quizz/group/model/Group.java`

```java
package com.quizz.group.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GroupMember> members = new ArrayList<>();
}
```

**File:** `src/main/java/com/quizz/group/model/GroupMember.java`

```java
package com.quizz.group.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_members",
       uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private GroupRole role;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;
}
```

**File:** `src/main/java/com/quizz/group/model/GroupRole.java`

```java
package com.quizz.group.model;

public enum GroupRole {
    MEMBER,
    ADMIN
}
```

### 2.3 Database Migration (Liquibase)

**File:** `src/main/resources/db/changelog/db.changelog-master.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 1
      author: system
      changes:
        - createTable:
            tableName: groups
            columns:
              - column:
                  name: id
                  type: BIGSERIAL
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: name
                  type: VARCHAR(255)
                  constraints:
                    nullable: false
              - column:
                  name: description
                  type: TEXT
              - column:
                  name: created_by
                  type: BIGINT
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: TIMESTAMP
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: TIMESTAMP
                  constraints:
                    nullable: false

  - changeSet:
      id: 2
      author: system
      changes:
        - createTable:
            tableName: group_members
            columns:
              - column:
                  name: id
                  type: BIGSERIAL
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: group_id
                  type: BIGINT
                  constraints:
                    nullable: false
                    foreignKeyName: fk_group_members_group
                    references: groups(id)
                    deleteCascade: true
              - column:
                  name: user_id
                  type: BIGINT
                  constraints:
                    nullable: false
              - column:
                  name: role
                  type: VARCHAR(50)
                  constraints:
                    nullable: false
              - column:
                  name: joined_at
                  type: TIMESTAMP
                  constraints:
                    nullable: false

  - changeSet:
      id: 3
      author: system
      changes:
        - addUniqueConstraint:
            tableName: group_members
            columnNames: group_id, user_id
            constraintName: uk_group_user

  - changeSet:
      id: 4
      author: system
      changes:
        - createIndex:
            tableName: group_members
            indexName: idx_group_members_user_id
            columns:
              - column:
                  name: user_id
        - createIndex:
            tableName: group_members
            indexName: idx_group_members_group_id
            columns:
              - column:
                  name: group_id
        - createIndex:
            tableName: group_members
            indexName: idx_group_members_role
            columns:
              - column:
                  name: role
```

### 2.4 Repositories

**File:** `src/main/java/com/quizz/group/repository/GroupRepository.java`

```java
package com.quizz.group.repository;

import com.quizz.group.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {
    // Basic CRUD operations from JpaRepository
}
```

**File:** `src/main/java/com/quizz/group/repository/GroupMemberRepository.java`

```java
package com.quizz.group.repository;

import com.quizz.group.model.GroupMember;
import com.quizz.group.model.GroupRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    /**
     * Find all group IDs where user is a member (any role)
     */
    @Query("SELECT gm.group.id FROM GroupMember gm WHERE gm.userId = :userId")
    List<Long> findGroupIdsByUserId(@Param("userId") Long userId);

    /**
     * Find group IDs where user has specific role
     */
    @Query("SELECT gm.group.id FROM GroupMember gm WHERE gm.userId = :userId AND gm.role = :role")
    List<Long> findGroupIdsByUserIdAndRole(@Param("userId") Long userId, @Param("role") GroupRole role);

    /**
     * Check if user is member of group
     */
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    /**
     * Find group member record
     */
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    /**
     * Find all members of a group
     */
    List<GroupMember> findByGroupId(Long groupId);
}
```

### 2.5 DTOs

**File:** `src/main/java/com/quizz/group/dto/UserGroupsResponse.java`

```java
package com.quizz.group.dto;

import lombok.*;

import java.util.Collections;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserGroupsResponse {
    private List<Long> groupIds;

    public List<Long> getGroupIds() {
        return groupIds != null ? groupIds : Collections.emptyList();
    }
}
```

**File:** `src/main/java/com/quizz/group/dto/MembershipResponse.java`

```java
package com.quizz.group.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipResponse {
    private boolean isMember;
    private String role;
}
```

### 2.6 Service Layer

**File:** `src/main/java/com/quizz/group/service/GroupService.java`

```java
package com.quizz.group.service;

import com.quizz.group.model.GroupRole;
import com.quizz.group.repository.GroupMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupService {

    private final GroupMemberRepository groupMemberRepository;

    /**
     * Get all group IDs where user is a member (any role)
     */
    @Transactional(readOnly = true)
    public List<Long> getUserGroupIds(Long userId) {
        log.debug("Fetching all groups for userId={}", userId);
        return groupMemberRepository.findGroupIdsByUserId(userId);
    }

    /**
     * Get group IDs where user has ADMIN role
     */
    @Transactional(readOnly = true)
    public List<Long> getUserAdminGroupIds(Long userId) {
        log.debug("Fetching admin groups for userId={}", userId);
        return groupMemberRepository.findGroupIdsByUserIdAndRole(userId, GroupRole.ADMIN);
    }

    /**
     * Check if user is member of group
     */
    @Transactional(readOnly = true)
    public boolean isMemberOfGroup(Long groupId, Long userId) {
        log.debug("Checking membership: groupId={}, userId={}", groupId, userId);
        return groupMemberRepository.existsByGroupIdAndUserId(groupId, userId);
    }

    /**
     * Get user's role in group
     */
    @Transactional(readOnly = true)
    public String getUserRoleInGroup(Long groupId, Long userId) {
        log.debug("Getting role: groupId={}, userId={}", groupId, userId);
        return groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
            .map(gm -> gm.getRole().name())
            .orElse(null);
    }
}
```

### 2.7 Controller Layer

**File:** `src/main/java/com/quizz/group/controller/GroupController.java`

```java
package com.quizz.group.controller;

import com.quizz.group.dto.MembershipResponse;
import com.quizz.group.dto.UserGroupsResponse;
import com.quizz.group.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Groups", description = "Group membership and role management API")
public class GroupController {

    private final GroupService groupService;

    /**
     * Get all groups user is member of (any role)
     */
    @GetMapping("/user/{userId}/groups")
    @Operation(summary = "Get user's groups",
               description = "Returns all groups where user is a member (any role)")
    public ResponseEntity<UserGroupsResponse> getUserGroups(@PathVariable Long userId) {
        log.info("Getting groups for userId={}", userId);
        List<Long> groupIds = groupService.getUserGroupIds(userId);
        return ResponseEntity.ok(new UserGroupsResponse(groupIds));
    }

    /**
     * Get groups where user is admin
     */
    @GetMapping("/user/{userId}/admin-groups")
    @Operation(summary = "Get user's admin groups",
               description = "Returns only groups where user has ADMIN role")
    public ResponseEntity<UserGroupsResponse> getUserAdminGroups(@PathVariable Long userId) {
        log.info("Getting admin groups for userId={}", userId);
        List<Long> adminGroupIds = groupService.getUserAdminGroupIds(userId);
        return ResponseEntity.ok(new UserGroupsResponse(adminGroupIds));
    }

    /**
     * Check if user is member of group
     */
    @GetMapping("/{groupId}/members/{userId}")
    @Operation(summary = "Check group membership",
               description = "Returns whether user is member of group and their role")
    public ResponseEntity<MembershipResponse> checkMembership(
            @PathVariable Long groupId,
            @PathVariable Long userId) {

        log.info("Checking membership: groupId={}, userId={}", groupId, userId);

        boolean isMember = groupService.isMemberOfGroup(groupId, userId);
        String role = isMember ? groupService.getUserRoleInGroup(groupId, userId) : null;

        return ResponseEntity.ok(new MembershipResponse(isMember, role));
    }
}
```

### 2.8 Webhook Client (Notify Question Service)

**File:** `src/main/java/com/quizz/group/client/QuestionServiceWebhookClient.java`

```java
package com.quizz.group.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Client for calling Question Service webhooks
 * Used to invalidate cache when group memberships change
 */
@Component
@Slf4j
public class QuestionServiceWebhookClient {

    private final RestTemplate restTemplate;
    private final String questionServiceUrl;

    public QuestionServiceWebhookClient(
            RestTemplate restTemplate,
            @Value("${question.service.url:http://localhost:8082}") String questionServiceUrl) {
        this.restTemplate = restTemplate;
        this.questionServiceUrl = questionServiceUrl;
    }

    /**
     * Notify Question Service to invalidate admin groups cache for user
     */
    public void invalidateUserAdminGroupsCache(Long userId) {
        try {
            String url = questionServiceUrl + "/api/cache/invalidate/user/" + userId + "/admin-groups";
            log.info("Sending cache invalidation webhook for user {} admin groups", userId);
            restTemplate.postForEntity(url, null, Void.class);
            log.debug("Cache invalidation webhook sent successfully");
        } catch (Exception e) {
            log.warn("Failed to send cache invalidation webhook for user {}: {}", userId, e.getMessage());
            // Don't fail the operation if webhook fails
        }
    }

    /**
     * Notify Question Service to invalidate user groups cache
     */
    public void invalidateUserGroupsCache(Long userId) {
        try {
            String url = questionServiceUrl + "/api/cache/invalidate/user/" + userId + "/groups";
            log.info("Sending cache invalidation webhook for user {} groups", userId);
            restTemplate.postForEntity(url, null, Void.class);
            log.debug("Cache invalidation webhook sent successfully");
        } catch (Exception e) {
            log.warn("Failed to send cache invalidation webhook for user {}: {}", userId, e.getMessage());
            // Don't fail the operation if webhook fails
        }
    }
}
```

**File:** `src/main/java/com/quizz/group/config/RestTemplateConfig.java`

```java
package com.quizz.group.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

### 2.9 Application Configuration

**File:** `src/main/resources/application.yml`

```yaml
spring:
  application:
    name: group-service

  datasource:
    url: jdbc:postgresql://localhost:5432/quizz_group
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect

  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yaml
    enabled: true

server:
  port: 8083

# Question Service URL for webhooks
question:
  service:
    url: http://localhost:8082

# Logging
logging:
  level:
    com.quizz.group: DEBUG
    org.hibernate.SQL: DEBUG
```

### 2.10 Main Application Class

**File:** `src/main/java/com/quizz/group/GroupServiceApplication.java`

```java
package com.quizz.group;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GroupServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GroupServiceApplication.class, args);
    }
}
```

### 2.11 When to Call Webhooks

**Integrate webhook calls into any service methods that modify group memberships:**

```java
@Service
@RequiredArgsConstructor
public class GroupMembershipService {

    private final GroupMemberRepository groupMemberRepository;
    private final QuestionServiceWebhookClient webhookClient;

    /**
     * Add user to group
     */
    @Transactional
    public void addMemberToGroup(Long groupId, Long userId, GroupRole role) {
        // ... save member to database ...

        // Invalidate caches
        webhookClient.invalidateUserGroupsCache(userId);
        if (role == GroupRole.ADMIN) {
            webhookClient.invalidateUserAdminGroupsCache(userId);
        }
    }

    /**
     * Remove user from group
     */
    @Transactional
    public void removeMemberFromGroup(Long groupId, Long userId) {
        // ... get current role and delete ...

        // Invalidate caches
        webhookClient.invalidateUserGroupsCache(userId);
        webhookClient.invalidateUserAdminGroupsCache(userId);  // Always invalidate (in case they were admin)
    }

    /**
     * Update user's role in group
     */
    @Transactional
    public void updateMemberRole(Long groupId, Long userId, GroupRole newRole) {
        // ... update role in database ...

        // Invalidate caches
        webhookClient.invalidateUserGroupsCache(userId);
        webhookClient.invalidateUserAdminGroupsCache(userId);  // Role changed, invalidate both
    }
}
```

---

## Part 3: Testing

### 3.1 Test Scenario 1: Regular User

**Setup:**
- User ID: 1
- Groups: Member of [10, 15] (MEMBER role only)
- Admin of: []

**Expected Behavior:**
```
GET /api/questions
→ Question Service calls Group Service: GET /user/1/admin-groups
→ Returns: { "groupIds": [] }
→ SQL: WHERE (q.createdBy = 1 OR q.groupId IN ())
→ Result: Only questions created by user 1
```

### 3.2 Test Scenario 2: Group Admin

**Setup:**
- User ID: 2
- Groups: Member of [5, 10, 15]
- Admin of: [5, 10]

**Expected Behavior:**
```
GET /api/questions
→ Question Service calls Group Service: GET /user/2/admin-groups
→ Returns: { "groupIds": [5, 10] }
→ SQL: WHERE (q.createdBy = 2 OR q.groupId IN (5, 10))
→ Result: Questions by user 2 + all questions in groups 5 and 10
```

### 3.3 Test Scenario 3: Cache Invalidation

**Setup:**
- User 2 is admin of groups [5, 10]
- Cache populated with this data

**Flow:**
```
1. Admin removes user 2 from group 10 admin role
   → Group Service: removeMemberFromGroup(10, 2)
   → Calls: webhookClient.invalidateUserAdminGroupsCache(2)
   → POST http://localhost:8082/api/cache/invalidate/user/2/admin-groups

2. Question Service receives webhook
   → CacheInvalidationController.invalidateUserAdminGroups(2)
   → Calls: groupServiceClient.invalidateUserAdminGroupsCache(2)
   → Cache entry removed

3. Next request from user 2
   → GET /api/questions
   → Cache miss, calls Group Service
   → Returns: { "groupIds": [5] }  (10 removed)
   → User 2 can no longer see group 10 questions
```

### 3.4 Test Scenario 4: Circuit Breaker

**Setup:**
- Group Service is down

**Expected Behavior:**
```
GET /api/questions (user 2)
→ Question Service tries: GET /user/2/admin-groups
→ Connection refused (5 times)
→ Circuit breaker opens
→ Fallback: getUserAdminGroupIdsFallback(2, exception)
→ Returns: []
→ SQL: WHERE (q.createdBy = 2 OR q.groupId IN ())
→ Result: Only questions by user 2 (fail-safe mode)
```

---

## Summary

### Question Service Implementation

| Component | Status | Description |
|-----------|--------|-------------|
| Dependencies | ✅ Added | Cache, Resilience4j |
| CacheConfig | ✅ Created | 5-min TTL, 1000 users |
| GroupServiceClient updates | ⏳ **TODO** | Add @Cacheable, @CircuitBreaker, metrics |
| CacheInvalidationController | ⏳ **TODO** | Webhook endpoint |
| application.yml | ⏳ **TODO** | Circuit breaker config |

### Group Service Implementation

| Component | Status | Description |
|-----------|--------|-------------|
| Project structure | ⏳ **TODO** | New Spring Boot project |
| Entities | ⏳ **TODO** | Group, GroupMember, GroupRole |
| Repositories | ⏳ **TODO** | GroupRepository, GroupMemberRepository |
| DTOs | ⏳ **TODO** | UserGroupsResponse, MembershipResponse |
| Service layer | ⏳ **TODO** | GroupService with 4 methods |
| Controller | ⏳ **TODO** | 3 endpoints |
| Webhook client | ⏳ **TODO** | QuestionServiceWebhookClient |
| Database migration | ⏳ **TODO** | Liquibase changelog |
| Configuration | ⏳ **TODO** | application.yml |

### Key Integration Points

1. **Group Service → Question Service (Webhook)**
   - When: Group membership/role changes
   - What: POST /api/cache/invalidate/user/{userId}/admin-groups
   - Why: Immediate cache invalidation

2. **Question Service → Group Service (REST)**
   - When: Every question query (cached)
   - What: GET /api/groups/user/{userId}/admin-groups
   - Why: Fetch admin groups for access control

3. **Circuit Breaker**
   - When: Group Service unavailable
   - What: Return empty list (fail-safe)
   - Why: Prevent cascade failures

---

This completes the full implementation specification for both services!
