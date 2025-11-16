# Group Service

**Port:** 8083
**Database:** PostgreSQL - `groups_db`

## Purpose

The Group Service manages all group-related operations in the Quiz Play System. It provides functionality for:

- **Group Management**: Create, update, and delete groups (application admins only)
- **Membership Management**: Handle group memberships, invitations, and member roles
- **Access Control**: Support for public and private groups with different join mechanisms
- **Group Administration**: Designate and manage group admins who can moderate their groups

## Scope

### Core Responsibilities

1. **Group CRUD Operations**
   - Application admins (ROLE_ADMIN) can create groups
   - Designate group admins from existing users
   - Update group details (name, description, type, avatar)
   - Delete groups (with validation - no questions/quizzes attached)

2. **Membership Management**
   - Users can join public groups directly
   - Private groups require invitation
   - Track member status (ACTIVE, PENDING, REMOVED)
   - Support member roles (MEMBER, ADMIN)

3. **Invitation System**
   - Group admins can invite users by email or userId
   - Token-based invitation acceptance
   - Invitation expiration (7 days)
   - Track invitation status (PENDING, ACCEPTED, DECLINED, EXPIRED)

4. **Authorization**
   - Validate group membership for question/quiz access
   - Check group admin privileges
   - Expose endpoints for other services to verify permissions

### Business Rules

- **Group Creation**: Only application admins (ROLE_ADMIN) can create groups
- **Group Admin Assignment**: Application admin designates one user as group admin
- **Group Admin Capabilities**:
  - Invite new members
  - Remove members
  - Change member roles (promote to admin, demote to member)
  - Update group settings
- **Public Groups**: Anyone can join without invitation
- **Private Groups**: Invitation-only membership
- **Multi-Group Membership**: Users can belong to multiple groups
- **Member Removal**: Group admin or app admin can remove members

## Database Schema

### Tables

#### `groups`
```sql
CREATE TABLE groups (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100) NOT NULL UNIQUE,
    description         TEXT,
    type                VARCHAR(20) NOT NULL,           -- PUBLIC, PRIVATE
    created_by          BIGINT NOT NULL,                -- App admin who created it
    group_admin_id      BIGINT NOT NULL,                -- Designated group admin
    avatar_url          VARCHAR(500),
    member_count        INTEGER DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (type IN ('PUBLIC', 'PRIVATE'))
);

CREATE INDEX idx_groups_type ON groups(type);
CREATE INDEX idx_groups_created_by ON groups(created_by);
CREATE INDEX idx_groups_group_admin ON groups(group_admin_id);
```

#### `group_members`
```sql
CREATE TABLE group_members (
    id                  BIGSERIAL PRIMARY KEY,
    group_id            BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id             BIGINT NOT NULL,
    role                VARCHAR(20) NOT NULL,           -- MEMBER, ADMIN
    status              VARCHAR(20) NOT NULL,           -- ACTIVE, PENDING, REMOVED
    joined_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    removed_at          TIMESTAMP,

    CHECK (role IN ('MEMBER', 'ADMIN')),
    CHECK (status IN ('ACTIVE', 'PENDING', 'REMOVED')),
    UNIQUE(group_id, user_id)
);

CREATE INDEX idx_group_members_user ON group_members(user_id, status);
CREATE INDEX idx_group_members_group ON group_members(group_id, status);
```

#### `group_invitations`
```sql
CREATE TABLE group_invitations (
    id                  BIGSERIAL PRIMARY KEY,
    group_id            BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    invited_by          BIGINT NOT NULL,                -- User who sent invitation
    invitee_email       VARCHAR(255),                   -- Email (if not yet registered)
    invitee_user_id     BIGINT,                         -- User ID (if registered user)
    status              VARCHAR(20) NOT NULL,           -- PENDING, ACCEPTED, DECLINED, EXPIRED
    token               VARCHAR(100) NOT NULL UNIQUE,   -- Unique invitation token
    expires_at          TIMESTAMP NOT NULL,
    responded_at        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')),
    CHECK (invitee_email IS NOT NULL OR invitee_user_id IS NOT NULL)
);

CREATE INDEX idx_invitations_token ON group_invitations(token);
CREATE INDEX idx_invitations_email ON group_invitations(invitee_email);
CREATE INDEX idx_invitations_user ON group_invitations(invitee_user_id);
CREATE INDEX idx_invitations_group ON group_invitations(group_id, status);
```

### Entity Relationships

```
┌─────────────────┐
│     Groups      │
│                 │
│  + type         │
│  + groupAdminId │
└────────┬────────┘
         │
         │ 1:N
         ▼
┌─────────────────┐       ┌─────────────────────┐
│  GroupMembers   │       │ GroupInvitations    │
│                 │       │                     │
│  + groupId      │       │  + groupId          │
│  + userId       │       │  + inviteeEmail     │
│  + role         │       │  + token            │
│  + status       │       │  + status           │
└─────────────────┘       └─────────────────────┘
```

## API Endpoints

### Group Management (Application Admin Only)

```
POST   /api/groups
  - Create a new group
  - Auth: ROLE_ADMIN
  - Body: { name, description, type, groupAdminId, avatarUrl }
  - Returns: GroupDTO

PUT    /api/groups/{id}
  - Update group details
  - Auth: ROLE_ADMIN
  - Body: { name, description, type, avatarUrl }
  - Returns: GroupDTO

DELETE /api/groups/{id}
  - Delete a group (only if no questions/quizzes associated)
  - Auth: ROLE_ADMIN
  - Returns: { message }

PUT    /api/groups/{id}/admin
  - Assign/change group admin
  - Auth: ROLE_ADMIN
  - Body: { groupAdminId }
  - Returns: GroupDTO
```

### Group Admin Operations

```
POST   /api/groups/{id}/invite
  - Invite user to group
  - Auth: Group Admin or App Admin
  - Body: { email?, userId?, message? }
  - Returns: InvitationDTO

DELETE /api/groups/{id}/members/{userId}
  - Remove member from group
  - Auth: Group Admin or App Admin
  - Returns: { message }

PUT    /api/groups/{id}/members/{userId}/role
  - Change member role (promote/demote)
  - Auth: Group Admin or App Admin
  - Body: { role }
  - Returns: MemberDTO
```

### Public Operations

```
GET    /api/groups
  - List all public groups (with pagination)
  - Query params: page, size, sort, search
  - Returns: Page<GroupDTO>

GET    /api/groups/{id}
  - Get group details
  - Auth: Required
  - Returns: GroupDTO with member count

POST   /api/groups/{id}/join
  - Join a public group
  - Auth: Required
  - Returns: MemberDTO

POST   /api/groups/invitations/{token}/accept
  - Accept group invitation
  - Auth: Required
  - Returns: MemberDTO

POST   /api/groups/invitations/{token}/decline
  - Decline group invitation
  - Auth: Required
  - Returns: { message }

GET    /api/groups/my-groups
  - Get user's groups
  - Auth: Required
  - Returns: List<GroupDTO>

GET    /api/groups/{id}/members
  - Get group members (paginated)
  - Auth: Group member or admin
  - Query params: page, size, sort
  - Returns: Page<MemberDTO>

POST   /api/groups/{id}/leave
  - Leave a group
  - Auth: Required
  - Returns: { message }
```

### Internal/Service-to-Service Endpoints

```
GET    /api/groups/{id}/members/{userId}
  - Check if user is member of group
  - Auth: Service token
  - Returns: { isMember: boolean, role: string }

GET    /api/groups/{id}/validate
  - Validate group exists and get type
  - Auth: Service token
  - Returns: { type: string }
```

## Data Models

### Enums

```java
enum GroupType {
    PUBLIC,    // Anyone can join
    PRIVATE    // Invitation only
}

enum MemberRole {
    MEMBER,    // Regular member
    ADMIN      // Group administrator
}

enum MemberStatus {
    ACTIVE,    // Current member
    PENDING,   // Invitation pending
    REMOVED    // No longer member
}

enum InvitationStatus {
    PENDING,   // Awaiting response
    ACCEPTED,  // Invitation accepted
    DECLINED,  // Invitation declined
    EXPIRED    // Invitation expired
}
```

### DTOs

```java
GroupDTO {
    Long id;
    String name;
    String description;
    GroupType type;
    Long createdBy;
    Long groupAdminId;
    String groupAdminName;     // Denormalized
    String avatarUrl;
    Integer memberCount;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}

MemberDTO {
    Long id;
    Long groupId;
    Long userId;
    String userDisplayName;    // Denormalized
    String userAvatarUrl;      // Denormalized
    MemberRole role;
    MemberStatus status;
    LocalDateTime joinedAt;
}

InvitationDTO {
    Long id;
    Long groupId;
    String groupName;          // Denormalized
    Long invitedBy;
    String invitedByName;      // Denormalized
    String inviteeEmail;
    Long inviteeUserId;
    InvitationStatus status;
    String token;
    LocalDateTime expiresAt;
    LocalDateTime createdAt;
}
```

## Technology Stack

- **Java**: 21
- **Spring Boot**: 3.4.0
- **Database**: PostgreSQL 16
- **Migrations**: Liquibase
- **Security**: Spring Security + JWT
- **Documentation**: SpringDoc OpenAPI
- **Testing**: JUnit 5, Mockito, AssertJ

## Dependencies

```xml
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
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- Database -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
    <dependency>
        <groupId>org.liquibase</groupId>
        <artifactId>liquibase-core</artifactId>
    </dependency>

    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
    </dependency>

    <!-- Eureka Client (optional) -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>

    <!-- OpenAPI Documentation -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    </dependency>
</dependencies>
```

## Configuration

### application.properties

```properties
# Server Configuration
server.port=8083
spring.application.name=group-service

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/groups_db
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA Configuration
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.format_sql=true

# Liquibase
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml
spring.liquibase.enabled=true

# JWT Configuration
jwt.secret=${JWT_SECRET}
jwt.expiration=86400000

# Eureka Client (optional)
eureka.client.enabled=${EUREKA_ENABLED:false}
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
eureka.instance.prefer-ip-address=true

# Actuator
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=when-authorized

# OpenAPI
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
```

## Scalability Considerations

- **Indexing**: Indexes on userId and groupId for fast membership lookups
- **Caching**: Cache group membership in Redis for permission checks (TTL: 30 minutes)
- **Pagination**: All list endpoints support pagination (max 100 items per page)
- **Denormalization**: Member counts stored in groups table to avoid expensive queries
- **Connection Pooling**: HikariCP with optimized pool size

## Security

- **Authentication**: JWT token validation via httpOnly cookies
- **Authorization**: Role-based access control (RBAC)
  - App Admin: Full group management
  - Group Admin: Manage assigned groups
  - User: Join groups, view memberships
- **Input Validation**: Bean Validation for all DTOs
- **XSS Prevention**: OWASP HTML Sanitizer for text fields

## Testing Strategy

- **Unit Tests**: Service layer business logic
- **Integration Tests**: Full Spring context with H2 database
- **API Tests**: MockMvc for controller testing
- **Target Coverage**: 85-90%

## Development Setup

1. **Prerequisites**
   - Java 21
   - PostgreSQL 16
   - Maven 3.9+

2. **Database Setup**
   ```bash
   createdb groups_db
   ```

3. **Environment Variables**
   ```bash
   export JWT_SECRET=your-secret-key
   export EUREKA_ENABLED=false
   ```

4. **Run Application**
   ```bash
   mvn spring-boot:run
   ```

5. **Access Swagger UI**
   ```
   http://localhost:8083/swagger-ui.html
   ```

## API Gateway Integration

When deployed with API Gateway (port 8080):
- Routes: `/api/groups/**` → `http://group-service:8083/api/groups/**`
- Load balancing via Eureka service discovery
- Rate limiting at gateway level

## Future Enhancements

- Group activity feed
- Member notifications
- Group statistics and analytics
- Group badges/avatars upload
- Bulk member management
- Member activity tracking

---

**Service Owner**: Quiz Play System Team
**Created**: 2024-01-20
**Last Updated**: 2024-01-20
**Version**: 1.0.0
